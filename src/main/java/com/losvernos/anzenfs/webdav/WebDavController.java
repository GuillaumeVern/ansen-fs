package com.losvernos.anzenfs.webdav;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.losvernos.anzenfs.files.FileNode;
import com.losvernos.anzenfs.files.FileService;
import com.losvernos.anzenfs.files.FileType;
import com.losvernos.anzenfs.files.ResourceAndName;
import com.losvernos.anzenfs.rbac.user.User;
import com.losvernos.anzenfs.security.FileSystemSecurityEvaluator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

/**
 * Minimal WebDAV subset (RFC 4918) covering exactly what a one-way photo-sync client (FolderSync,
 * Autosync) needs: OPTIONS for capability discovery, PROPFIND (depth 0/1) for listing, PUT for
 * upload/overwrite, MKCOL for folder creation, GET/HEAD for verification, DELETE for cleanup. No
 * MOVE/COPY/locking - out of scope for a one-way upload client.
 *
 * <p>Every request is scoped to the authenticated user's own WebDAV root
 * ({@link FileService#getOrCreateWebDavRoot}), never the wider file tree - so unlike
 * {@link com.losvernos.anzenfs.files.FileController}, there is no per-request target uuid to
 * annotate a {@code @PreAuthorize} with; the single {@code fsSecurity.hasAccess} check below runs
 * against that root once per request instead.
 *
 * <p>Spring MVC's {@code @GetMapping}/{@code @PutMapping} etc. are backed by
 * {@link org.springframework.web.bind.annotation.RequestMethod}, which has no PROPFIND/MKCOL
 * constants - so this dispatches on the raw HTTP method string from a single catch-all mapping
 * instead of per-verb annotations.
 */
@RestController
public class WebDavController {

  @Autowired
  private FileService fileService;

  @Autowired
  private FileSystemSecurityEvaluator fsSecurity;

  /**
   * Spring MVC auto-handles OPTIONS for any mapping that doesn't explicitly list it in {@code
   * method}, computing its own generic Allow header instead of invoking the handler - so the
   * WebDAV capability response (the {@code DAV} header clients probe for) needs its own,
   * more-specific mapping to take priority over {@link #handle} below for that one verb.
   */
  @RequestMapping(path = "/webdav/**", method = RequestMethod.OPTIONS)
  public void handleOptionsRequest(HttpServletResponse response) {
    handleOptions(response);
  }

  @RequestMapping(path = "/webdav/**")
  public void handle(HttpServletRequest request, HttpServletResponse response,
                      @AuthenticationPrincipal User currentUser) throws IOException {
    String relativePath = extractRelativePath(request);
    FileNode webDavRoot = fileService.getOrCreateWebDavRoot(currentUser);

    switch (request.getMethod()) {
      case "PROPFIND" -> handlePropfind(request, response, currentUser, webDavRoot, relativePath);
      case "MKCOL" -> handleMkcol(response, webDavRoot, relativePath);
      case "PUT" -> handlePut(request, response, webDavRoot, relativePath);
      case "GET" -> handleGet(response, webDavRoot, relativePath, false);
      case "HEAD" -> handleGet(response, webDavRoot, relativePath, true);
      case "DELETE" -> handleDelete(response, webDavRoot, relativePath);
      default -> response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
  }

  /**
   * Computed from the raw request URI rather than {@code HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}:
   * with this project's PathPattern-based matching, that attribute carries the full matched path
   * (including the literal "/webdav" prefix) rather than just the "**" tail, which would silently
   * double-count the prefix as part of the resolved file path.
   */
  private String extractRelativePath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      uri = uri.substring(contextPath.length());
    }

    String decoded = UriUtils.decode(uri, StandardCharsets.UTF_8);
    String tail = decoded.startsWith("/webdav") ? decoded.substring("/webdav".length()) : decoded;

    while (tail.startsWith("/")) tail = tail.substring(1);
    while (tail.endsWith("/")) tail = tail.substring(0, tail.length() - 1);
    return tail;
  }

  private void handleOptions(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader("DAV", "1");
    response.setHeader("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, MKCOL");
  }

  private void handlePropfind(HttpServletRequest request, HttpServletResponse response,
                               User currentUser, FileNode webDavRoot, String relativePath) throws IOException {
    if (!fsSecurity.hasAccess(webDavRoot.uuid(), "READ")) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    Optional<FileNode> target = fileService.resolveNode(webDavRoot, relativePath);
    if (target.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    FileNode node = target.get();
    boolean listChildren = !"0".equals(request.getHeader("Depth")) && node.type() == FileType.FOLDER;

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<D:multistatus xmlns:D=\"DAV:\">\n");
    appendResponse(xml, hrefFor(relativePath, node.type() == FileType.FOLDER), node);

    if (listChildren) {
      List<FileNode> children = fileService.getChildrenAfter(node.uuid(), null, Integer.MAX_VALUE, currentUser);
      for (FileNode child : children) {
        appendResponse(xml, hrefFor(joinPath(relativePath, child.name()), child.type() == FileType.FOLDER), child);
      }
    }

    xml.append("</D:multistatus>");

    response.setStatus(207); // Multi-Status
    response.setContentType("application/xml;charset=UTF-8");
    response.getWriter().write(xml.toString());
  }

  private void handleMkcol(HttpServletResponse response, FileNode webDavRoot, String relativePath) {
    if (relativePath.isBlank()) {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }
    if (!fsSecurity.hasAccess(webDavRoot.uuid(), "WRITE")) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    boolean created = fileService.createCollection(webDavRoot, relativePath);
    response.setStatus(created ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_METHOD_NOT_ALLOWED);
  }

  private void handlePut(HttpServletRequest request, HttpServletResponse response,
                          FileNode webDavRoot, String relativePath) throws IOException {
    if (relativePath.isBlank()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if (!fsSecurity.hasAccess(webDavRoot.uuid(), "WRITE")) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    boolean existedBefore = fileService.resolveNode(webDavRoot, relativePath).isPresent();

    try (InputStream body = request.getInputStream()) {
      fileService.putFile(webDavRoot, relativePath, body);
    } catch (SecurityException e) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    response.setStatus(existedBefore ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CREATED);
  }

  private void handleGet(HttpServletResponse response, FileNode webDavRoot, String relativePath, boolean headOnly)
      throws IOException {
    if (!fsSecurity.hasAccess(webDavRoot.uuid(), "READ")) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    Optional<FileNode> target = fileService.resolveNode(webDavRoot, relativePath);
    if (target.isEmpty() || target.get().type() == FileType.FOLDER) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    try {
      ResourceAndName resourceAndName = fileService.getResourceAndName(target.get().uuid());
      response.setContentType("application/octet-stream");
      response.setContentLengthLong(resourceAndName.resource().contentLength());

      if (!headOnly) {
        try (InputStream in = resourceAndName.resource().getInputStream()) {
          in.transferTo(response.getOutputStream());
        }
      }
    } catch (FileNotFoundException e) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private void handleDelete(HttpServletResponse response, FileNode webDavRoot, String relativePath) {
    if (relativePath.isBlank()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if (!fsSecurity.hasAccess(webDavRoot.uuid(), "WRITE")) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    Optional<FileNode> target = fileService.resolveNode(webDavRoot, relativePath);
    if (target.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    fileService.permanentlyDeleteItemByExternalId(target.get().uuid());
    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

  private String hrefFor(String relativePath, boolean isFolder) {
    String href = "/webdav/" + relativePath;
    if (isFolder && !href.endsWith("/")) {
      href += "/";
    }
    return href;
  }

  private String joinPath(String base, String name) {
    return base.isBlank() ? name : base + "/" + name;
  }

  private void appendResponse(StringBuilder xml, String href, FileNode node) {
    xml.append("  <D:response>\n");
    xml.append("    <D:href>").append(escapeXml(href)).append("</D:href>\n");
    xml.append("    <D:propstat>\n      <D:prop>\n");
    xml.append("        <D:displayname>").append(escapeXml(node.name())).append("</D:displayname>\n");

    if (node.type() == FileType.FOLDER) {
      xml.append("        <D:resourcetype><D:collection/></D:resourcetype>\n");
    } else {
      xml.append("        <D:resourcetype/>\n");
      xml.append("        <D:getcontentlength>").append(node.size() != null ? node.size() : 0L)
          .append("</D:getcontentlength>\n");
    }

    xml.append("      </D:prop>\n      <D:status>HTTP/1.1 200 OK</D:status>\n    </D:propstat>\n  </D:response>\n");
  }

  private String escapeXml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
