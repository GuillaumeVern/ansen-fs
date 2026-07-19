package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import com.losvernos.anzenfs.files.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Adds the size_bytes column and backfills it from disk for databases created before file
 * sizes were tracked. schema.sql's CREATE TABLE IF NOT EXISTS only applies to brand-new
 * databases, so existing installs need this one-time, idempotent migration instead. Runs on
 * every boot but is a no-op once the column already exists.
 */
@Component
public class FileSizeBackfillRunner implements CommandLineRunner {

  private final FileRepository fileRepository;
  private final Path storageRoot = new File(FileUtils.getDataDir(), "data").toPath();

  public FileSizeBackfillRunner(FileRepository fileRepository) {
    this.fileRepository = fileRepository;
  }

  @Override
  public void run(String... args) {
    if (fileRepository.hasSizeColumn()) {
      return;
    }

    fileRepository.addSizeColumn();

    for (FileRepository.IdAndExternalId row : fileRepository.findNonFolderIdsAndExternalIds()) {
      try {
        String relativePath = fileRepository.getFullPath(row.externalId());
        if (relativePath == null) continue;

        Path physicalPath = storageRoot.resolve(relativePath).normalize();
        if (Files.exists(physicalPath) && !Files.isDirectory(physicalPath)) {
          fileRepository.updateFileSize(row.fileId(), Files.size(physicalPath));
        }
      } catch (IOException e) {
        System.err.println("Failed to backfill size for " + row.externalId() + ": " + e.getMessage());
      }
    }
  }
}
