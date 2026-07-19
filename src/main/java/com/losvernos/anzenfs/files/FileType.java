package com.losvernos.anzenfs.files;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Classifies a file by its extension. Each constant owns the extensions it recognizes,
 * so supporting a new format is either adding to an existing constant's set or adding
 * one new constant here - nothing else in the codebase needs to change.
 */
public enum FileType {
  FOLDER(Set.of()),
  IMAGE(Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif", "ico", "tiff", "tif")),
  VIDEO(Set.of("mp4", "webm", "mov", "avi", "mkv", "m4v", "mpeg", "mpg")),
  AUDIO(Set.of("mp3", "wav", "ogg", "flac", "m4a", "aac", "wma")),
  PDF(Set.of("pdf")),
  DOCUMENT(Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp")),
  ARCHIVE(Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz")),
  TEXT(Set.of("txt", "md", "csv", "json", "xml", "yaml", "yml", "log")),
  OTHER(Set.of());

  private final Set<String> extensions;

  FileType(Set<String> extensions) {
    this.extensions = extensions;
  }

  private static final Map<String, FileType> BY_EXTENSION = buildLookup();

  private static Map<String, FileType> buildLookup() {
    Map<String, FileType> lookup = new HashMap<>();
    for (FileType type : values()) {
      for (String extension : type.extensions) {
        lookup.put(extension, type);
      }
    }
    return lookup;
  }

  public static FileType fromFilename(String filename) {
    if (filename == null) {
      return OTHER;
    }
    return BY_EXTENSION.getOrDefault(extensionOf(filename), OTHER);
  }

  private static String extensionOf(String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) {
      return "";
    }
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
