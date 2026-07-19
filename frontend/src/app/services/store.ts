import { inject, Injectable, signal, computed } from '@angular/core';
import {HttpClient, HttpEvent, HttpParams} from '@angular/common/http';
import {firstValueFrom, Observable} from 'rxjs';

export type FileType =
  | 'FOLDER'
  | 'IMAGE'
  | 'VIDEO'
  | 'AUDIO'
  | 'PDF'
  | 'DOCUMENT'
  | 'ARCHIVE'
  | 'TEXT'
  | 'OTHER';

export interface FileNode {
  uuid: string;
  parentUuid: string;
  name: string;
  type: FileType;
  hash: string;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class StoreService {
  private http = inject(HttpClient);

  private nodes = signal<FileNode[]>([]);
  private arianeHistory = signal<string[]>([]);
  public ariane = this.arianeHistory.asReadonly();
  private uuidHistory = signal<string[]>([]);

  public fileNodes = this.nodes.asReadonly();
  public isLoading = signal(false);
  public currentParentUuid = computed(() => this.uuidHistory()[this.uuidHistory().length - 1]);

  public isExhausted = signal(false);

  private requestToken = 0;

  reset(): void {
    this.requestToken++;
    this.nodes.set([]);
    this.arianeHistory.set([]);
    this.uuidHistory.set([]);
    this.isLoading.set(false);
    this.isExhausted.set(false);
  }

  async initHome(): Promise<void> {
    const home = await firstValueFrom(this.http.get<FileNode>('/api/files/home'));
    this.arianeHistory.set([home.name]);
    this.uuidHistory.set([home.uuid]);
  }

  async loadFiles(replace = false): Promise<boolean> {
    if (this.isLoading() || (!replace && this.isExhausted())) return true;

    if (replace) {
      this.isExhausted.set(false);
    }

    const currentNodes = this.nodes();
    const lastNode = currentNodes[currentNodes.length - 1];
    const parentUuid = this.currentParentUuid();

    let params = new HttpParams();
    if (parentUuid) params = params.set('parentUuid', parentUuid);
    if (!replace && lastNode) params = params.set('lastFileName', lastNode.name);

    const token = this.requestToken;
    this.isLoading.set(true);
    try {
      const newNodes = await firstValueFrom(this.http.get<FileNode[]>("/api/files", { params }));
      if (token !== this.requestToken) return false;

      if (!newNodes || newNodes.length === 0) {
        if (replace) {
          this.nodes.set([]);
        }
        this.isExhausted.set(true);
        return true;
      }

      if (replace) {
        this.nodes.set(newNodes);
      } else {
        this.nodes.update(old => {
          const existingUuuids = new Set(old.map(n => n.uuid));
          const filteredNew = newNodes.filter(n => !existingUuuids.has(n.uuid));

          if (filteredNew.length === 0) {
            this.isExhausted.set(true);
          }
          return [...old, ...filteredNew];
        });
      }
      return true;
    } catch (err) {
      console.error("Failed to load files", err);
      return false;
    } finally {
      if (token === this.requestToken) {
        this.isLoading.set(false);
      }
    }
  }

  async changeParent(folderName: string, uuid: string) {
    this.arianeHistory.update(prev => [...prev, folderName]);
    this.uuidHistory.update(prev => [...prev, uuid]);

    const succeeded = await this.loadFiles(true);
    if (!succeeded) {
      this.arianeHistory.update(prev => prev.slice(0, -1));
      this.uuidHistory.update(prev => prev.slice(0, -1));
    }
  }

  goBack() {
    if (this.uuidHistory().length <= 1) return;
    this.arianeHistory.update(prev => prev.slice(0, -1));
    this.uuidHistory.update(prev => prev.slice(0, -1));
    this.loadFiles(true);
  }

  jumpToBreadcrumbIndex(targetIndex: number) {
    if (targetIndex >= this.uuidHistory().length) return;

    this.arianeHistory.update(prev => prev.slice(0, targetIndex + 1));
    this.uuidHistory.update(prev => prev.slice(0, targetIndex + 1));
    this.loadFiles(true);
  }

  downloadFile(uuid: string): Observable<HttpEvent<Blob>> {
    return this.http.get(`/api/files/download/${uuid}`, {
      responseType: 'blob',
      reportProgress: true,
      observe: 'events'
    });
  }

  /**
   * Fetched as a blob through HttpClient (rather than a plain <img>/<iframe> src) so the
   * auth interceptor attaches the JWT bearer token - browsers never attach it to native
   * resource loads, so a bare URL binding gets a 403 from the backend.
   */
  getPreview(uuid: string): Observable<Blob> {
    return this.http.get(`/api/files/preview/${uuid}`, { responseType: 'blob' });
  }

  async deleteItem(uuid: string): Promise<boolean> {
    try {
      await firstValueFrom(
        this.http.delete(`/api/files/${uuid}`, { responseType: 'text' })
      );

      this.nodes.update(currentNodes => currentNodes.filter(node => node.uuid !== uuid));

      console.log(`Resource ${uuid} successfully removed from server and state grid layout.`);
      return true;

    } catch (err) {
      console.error(`Failed to delete resource item ${uuid}:`, err);
      return false;
    }
  }
}
