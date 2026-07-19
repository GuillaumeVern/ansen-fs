package com.losvernos.anzenfs.files.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.losvernos.anzenfs.files.FileType;

/**
 * Extracts the first frame from a video as a JPEG thumbnail. Shells out to the {@code ffmpeg}
 * binary - if it isn't installed, or a given video fails to process, generation is skipped
 * rather than failing the upload: video files without a thumbnail simply fall back to a
 * generic icon in the UI.
 *
 * <p>Deliberately grabs the very first frame rather than seeking a second or two in: seeking
 * past a short clip's duration silently produces no output, so anchoring on frame zero is the
 * one strategy that works uniformly regardless of video length. The explicit
 * {@code format=yuvj420p} conversion works around ffmpeg's MJPEG encoder rejecting the
 * non-full-range pixel formats some encoders (including ffmpeg's own test sources) produce.
 */
@Component
public class FfmpegVideoThumbnailGenerator implements PreviewThumbnailGenerator {

  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  @Override
  public FileType supportedType() {
    return FileType.VIDEO;
  }

  @Override
  public Optional<Path> generate(Path source, Path targetJpeg) {
    try {
      Files.createDirectories(targetJpeg.getParent());

      ProcessBuilder builder = new ProcessBuilder(
          "ffmpeg", "-y",
          "-i", source.toAbsolutePath().toString(),
          "-frames:v", "1",
          "-update", "1",
          "-vf", "scale=320:-1,format=yuvj420p",
          targetJpeg.toAbsolutePath().toString());
      builder.redirectErrorStream(true);
      builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

      Process process = builder.start();
      boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

      if (!finished) {
        process.destroyForcibly();
        System.err.println("Video thumbnail generation timed out for: " + source);
        return Optional.empty();
      }

      if (process.exitValue() != 0 || !Files.exists(targetJpeg) || Files.size(targetJpeg) == 0) {
        System.err.println("Video thumbnail generation failed for: " + source);
        return Optional.empty();
      }

      return Optional.of(targetJpeg);
    } catch (IOException e) {
      System.err.println("ffmpeg unavailable or failed to start for " + source + ": " + e.getMessage());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
