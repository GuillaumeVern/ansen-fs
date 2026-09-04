import { FileType } from '../services/store';

/** Icon representing each file type, used for fallback previews (grid view) and the bin's list view. */
export const TYPE_ICON: Record<FileType, string> = {
  FOLDER: 'folder',
  IMAGE: 'file-image',
  VIDEO: 'video-camera',
  AUDIO: 'sound',
  PDF: 'file-pdf',
  DOCUMENT: 'file-text',
  ARCHIVE: 'file-zip',
  TEXT: 'file-text',
  OTHER: 'file',
};
