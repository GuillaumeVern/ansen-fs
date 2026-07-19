package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.files.FileType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * One-time (per row), idempotent reclassification of files persisted under the old generic
 * "FILE" type, back when {@link FileType} only distinguished folders from everything else.
 * Runs on every boot but is a no-op once every row has been reclassified.
 */
@Component
public class FileTypeBackfillRunner implements CommandLineRunner {

  private static final String LEGACY_GENERIC_TYPE = "FILE";

  private final FileRepository fileRepository;

  public FileTypeBackfillRunner(FileRepository fileRepository) {
    this.fileRepository = fileRepository;
  }

  @Override
  public void run(String... args) {
    for (FileRepository.IdAndName row : fileRepository.findIdsAndNamesByRawType(LEGACY_GENERIC_TYPE)) {
      FileType classified = FileType.fromFilename(row.name());
      fileRepository.updateType(row.fileId(), classified);
    }
  }
}
