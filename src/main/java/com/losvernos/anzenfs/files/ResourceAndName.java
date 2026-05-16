package com.losvernos.anzenfs.files;

import org.springframework.core.io.Resource;

public record ResourceAndName(Resource resource, String fileName) {
}
