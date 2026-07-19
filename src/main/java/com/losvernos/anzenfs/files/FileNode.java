package com.losvernos.anzenfs.files;

public record FileNode(
    String uuid,
    String parentUuid,
    String name,
    FileType type,
    String hash,
    Long size) {
}
