package com.losvernos.anzenfs.files.preview;

import com.losvernos.anzenfs.files.FileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Written to pass whether or not the {@code ffmpeg} binary is actually installed on the
 * machine running the tests: it only exercises failure paths, which must return an empty
 * result gracefully either way (missing binary vs. rejecting bad input both look the same
 * from the caller's perspective).
 */
class FfmpegVideoThumbnailGeneratorTest {

    private final FfmpegVideoThumbnailGenerator generator = new FfmpegVideoThumbnailGenerator();

    @Test
    void supportsVideoType() {
        assertThat(generator.supportedType()).isEqualTo(FileType.VIDEO);
    }

    @Test
    void returnsEmptyForNonVideoInput(@TempDir Path tempDir) throws Exception {
        Path notAVideo = tempDir.resolve("notavideo.mp4");
        Files.writeString(notAVideo, "this is definitely not a video file", StandardCharsets.UTF_8);
        Path target = tempDir.resolve("thumb.jpg");

        Optional<Path> result = generator.generate(notAVideo, target);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenSourceFileDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.mp4");
        Path target = tempDir.resolve("thumb.jpg");

        Optional<Path> result = generator.generate(missing, target);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotThrowWhenTargetDirectoryDoesNotYetExist(@TempDir Path tempDir) throws Exception {
        Path notAVideo = tempDir.resolve("notavideo.mp4");
        Files.writeString(notAVideo, "garbage", StandardCharsets.UTF_8);
        Path target = tempDir.resolve("nested/thumbnails/thumb.jpg");

        Optional<Path> result = generator.generate(notAVideo, target);

        assertThat(result).isEmpty();
    }
}
