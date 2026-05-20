import { inject, Injectable, signal, computed } from '@angular/core';
import {HttpClient, HttpEvent, HttpParams} from '@angular/common/http';
import {firstValueFrom, Observable} from 'rxjs';

export interface FileNode {
  uuid: string;
  parentUuid: string;
  name: string;
  type: string;
  hash: string;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class StoreService {
  private http = inject(HttpClient);

  private nodes = signal<FileNode[]>([]);
  private arianeHistory = signal<string[]>(["Root"]);
  public ariane = this.arianeHistory.asReadonly();
  private uuidHistory = signal<string[]>(['']);

  public fileNodes = this.nodes.asReadonly();
  public isLoading = signal(false);
  public currentParentUuid = computed(() => this.uuidHistory()[this.uuidHistory().length - 1]);

  public isExhausted = signal(false);

  async loadFiles(replace = false) {
    if (this.isLoading() || (!replace && this.isExhausted())) return;

    if (replace) {
      this.isExhausted.set(false);
    }

    const currentNodes = this.nodes();
    const lastNode = currentNodes[currentNodes.length - 1];
    const parentUuid = this.currentParentUuid();

    let params = new HttpParams();
    if (parentUuid) params = params.set('parentUuid', parentUuid);
    if (!replace && lastNode) params = params.set('lastFileName', lastNode.name);

    this.isLoading.set(true);
    try {
      const newNodes = await firstValueFrom(this.http.get<FileNode[]>("/api/files", { params }));

      if (!newNodes || newNodes.length === 0) {
        this.isExhausted.set(true);
        return;
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
    } catch (err) {
      console.error("Failed to load files", err);
    } finally {
      this.isLoading.set(false);
    }
  }

  changeParent(folderName: string, uuid: string) {
    this.arianeHistory.update(prev => [...prev, folderName]);
    this.uuidHistory.update(prev => [...prev, uuid]);
    this.loadFiles(true);
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
}
