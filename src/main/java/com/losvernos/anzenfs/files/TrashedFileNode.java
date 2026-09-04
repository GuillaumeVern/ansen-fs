package com.losvernos.anzenfs.files;

/** A file or folder currently sitting in the bin, as shown by the trash view. */
public record TrashedFileNode(
    String uuid,
    String name,
    FileType type,
    Long size,
    String originalPath,
    String deletedAt) {
}
