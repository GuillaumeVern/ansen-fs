package com.losvernos.anzenfs.database;

import com.losvernos.anzenfs.files.FileRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Adds the deleted_at column for databases created before soft-delete (the bin/trash feature)
 * existed. schema.sql's CREATE TABLE IF NOT EXISTS only applies to brand-new databases, so
 * existing installs need this one-time, idempotent migration instead. Runs on every boot but
 * is a no-op once the column already exists.
 */
@Component
public class FileDeletedAtBackfillRunner implements CommandLineRunner {

  private final FileRepository fileRepository;

  public FileDeletedAtBackfillRunner(FileRepository fileRepository) {
    this.fileRepository = fileRepository;
  }

  @Override
  public void run(String... args) {
    if (fileRepository.hasDeletedAtColumn()) {
      return;
    }

    fileRepository.addDeletedAtColumn();
  }
}
