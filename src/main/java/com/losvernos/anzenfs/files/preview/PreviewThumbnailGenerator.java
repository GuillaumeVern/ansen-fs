package com.losvernos.anzenfs.files.preview;

import java.nio.file.Path;
import java.util.Optional;

import com.losvernos.anzenfs.files.FileType;

/**
 * Generates a static preview image for a file whose native content isn't directly
 * browser-previewable. Implementations register themselves against the {@link FileType}
 * they handle; {@link PreviewThumbnailGeneratorRegistry} auto-discovers every Spring bean
 * of this type, so adding support for a new format is just adding a new implementation -
 * no other code needs to change.
 */
public interface PreviewThumbnailGenerator {

  FileType supportedType();

  /**
   * Attempts to generate a thumbnail image for {@code source} at {@code targetJpeg}.
   * Never throws: any failure (missing tooling, corrupt input, timeout) is reported as
   * an empty result so callers can fall back gracefully.
   */
  Optional<Path> generate(Path source, Path targetJpeg);
}
