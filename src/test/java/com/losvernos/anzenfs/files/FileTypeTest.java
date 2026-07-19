package com.losvernos.anzenfs.files;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeTest {

    @Test
    void classifiesImages() {
        assertThat(FileType.fromFilename("photo.jpg")).isEqualTo(FileType.IMAGE);
        assertThat(FileType.fromFilename("photo.jpeg")).isEqualTo(FileType.IMAGE);
        assertThat(FileType.fromFilename("icon.png")).isEqualTo(FileType.IMAGE);
        assertThat(FileType.fromFilename("anim.gif")).isEqualTo(FileType.IMAGE);
        assertThat(FileType.fromFilename("photo.webp")).isEqualTo(FileType.IMAGE);
    }

    @Test
    void classifiesVideos() {
        assertThat(FileType.fromFilename("clip.mp4")).isEqualTo(FileType.VIDEO);
        assertThat(FileType.fromFilename("clip.webm")).isEqualTo(FileType.VIDEO);
        assertThat(FileType.fromFilename("clip.mov")).isEqualTo(FileType.VIDEO);
        assertThat(FileType.fromFilename("clip.mkv")).isEqualTo(FileType.VIDEO);
    }

    @Test
    void classifiesAudio() {
        assertThat(FileType.fromFilename("song.mp3")).isEqualTo(FileType.AUDIO);
        assertThat(FileType.fromFilename("song.wav")).isEqualTo(FileType.AUDIO);
        assertThat(FileType.fromFilename("song.flac")).isEqualTo(FileType.AUDIO);
    }

    @Test
    void classifiesPdf() {
        assertThat(FileType.fromFilename("report.pdf")).isEqualTo(FileType.PDF);
    }

    @Test
    void classifiesDocuments() {
        assertThat(FileType.fromFilename("resume.docx")).isEqualTo(FileType.DOCUMENT);
        assertThat(FileType.fromFilename("budget.xlsx")).isEqualTo(FileType.DOCUMENT);
        assertThat(FileType.fromFilename("slides.pptx")).isEqualTo(FileType.DOCUMENT);
    }

    @Test
    void classifiesArchives() {
        assertThat(FileType.fromFilename("bundle.zip")).isEqualTo(FileType.ARCHIVE);
        assertThat(FileType.fromFilename("bundle.tar.gz")).isEqualTo(FileType.ARCHIVE);
        assertThat(FileType.fromFilename("bundle.7z")).isEqualTo(FileType.ARCHIVE);
    }

    @Test
    void classifiesText() {
        assertThat(FileType.fromFilename("notes.txt")).isEqualTo(FileType.TEXT);
        assertThat(FileType.fromFilename("readme.md")).isEqualTo(FileType.TEXT);
        assertThat(FileType.fromFilename("data.csv")).isEqualTo(FileType.TEXT);
    }

    @Test
    void classificationIsCaseInsensitive() {
        assertThat(FileType.fromFilename("PHOTO.JPG")).isEqualTo(FileType.IMAGE);
        assertThat(FileType.fromFilename("Clip.Mp4")).isEqualTo(FileType.VIDEO);
    }

    @Test
    void unknownExtensionsClassifyAsOther() {
        assertThat(FileType.fromFilename("binary.exe")).isEqualTo(FileType.OTHER);
        assertThat(FileType.fromFilename("data.bin")).isEqualTo(FileType.OTHER);
    }

    @Test
    void handlesFilenamesWithoutAnExtension() {
        assertThat(FileType.fromFilename("README")).isEqualTo(FileType.OTHER);
        assertThat(FileType.fromFilename("trailing.")).isEqualTo(FileType.OTHER);
        assertThat(FileType.fromFilename(null)).isEqualTo(FileType.OTHER);
    }

    @Test
    void folderIsNotReachableThroughClassification() {
        assertThat(FileType.fromFilename("folder")).isNotEqualTo(FileType.FOLDER);
    }
}
