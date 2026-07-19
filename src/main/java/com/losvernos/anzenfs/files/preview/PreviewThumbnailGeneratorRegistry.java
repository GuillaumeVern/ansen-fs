package com.losvernos.anzenfs.files.preview;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.losvernos.anzenfs.files.FileType;

/**
 * Indexes every {@link PreviewThumbnailGenerator} bean by the {@link FileType} it supports.
 * Adding a new generator to the Spring context is all that's needed to plug in support for
 * another format - nothing here needs to change.
 */
@Component
public class PreviewThumbnailGeneratorRegistry {

  private final Map<FileType, PreviewThumbnailGenerator> generatorsByType;

  public PreviewThumbnailGeneratorRegistry(List<PreviewThumbnailGenerator> generators) {
    this.generatorsByType = generators.stream()
        .collect(Collectors.toMap(PreviewThumbnailGenerator::supportedType, Function.identity()));
  }

  public Optional<PreviewThumbnailGenerator> forType(FileType type) {
    return Optional.ofNullable(generatorsByType.get(type));
  }
}
