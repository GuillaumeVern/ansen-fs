package com.losvernos.anzenfs.files.preview;

import com.losvernos.anzenfs.files.FileType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewThumbnailGeneratorRegistryTest {

    private static PreviewThumbnailGenerator generatorFor(FileType type) {
        return new PreviewThumbnailGenerator() {
            @Override
            public FileType supportedType() {
                return type;
            }

            @Override
            public Optional<Path> generate(Path source, Path targetJpeg) {
                return Optional.of(targetJpeg);
            }
        };
    }

    @Test
    void resolvesTheGeneratorRegisteredForAType() {
        PreviewThumbnailGenerator videoGenerator = generatorFor(FileType.VIDEO);
        PreviewThumbnailGeneratorRegistry registry =
                new PreviewThumbnailGeneratorRegistry(List.of(videoGenerator));

        assertThat(registry.forType(FileType.VIDEO)).contains(videoGenerator);
    }

    @Test
    void returnsEmptyWhenNoGeneratorIsRegisteredForAType() {
        PreviewThumbnailGeneratorRegistry registry =
                new PreviewThumbnailGeneratorRegistry(List.of(generatorFor(FileType.VIDEO)));

        assertThat(registry.forType(FileType.PDF)).isEmpty();
        assertThat(registry.forType(FileType.IMAGE)).isEmpty();
    }

    @Test
    void supportsMultipleRegisteredGenerators() {
        PreviewThumbnailGenerator videoGenerator = generatorFor(FileType.VIDEO);
        PreviewThumbnailGenerator pdfGenerator = generatorFor(FileType.PDF);
        PreviewThumbnailGeneratorRegistry registry =
                new PreviewThumbnailGeneratorRegistry(List.of(videoGenerator, pdfGenerator));

        assertThat(registry.forType(FileType.VIDEO)).contains(videoGenerator);
        assertThat(registry.forType(FileType.PDF)).contains(pdfGenerator);
    }
}
