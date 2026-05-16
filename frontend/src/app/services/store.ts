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
  private ariane = signal<string[]>([""]); // Breadcrumbs

  public fileNodes = this.nodes.asReadonly();
  public isLoading = signal(false);
  public currentParentUuid = computed(() => this.ariane()[this.ariane().length - 1]);

  async loadFiles(replace = false) {
    if (this.isLoading()) return;

    const currentNodes = this.nodes();
    const lastNode = currentNodes[currentNodes.length - 1];
    const parentUuid = this.currentParentUuid();

    let params = new HttpParams();
    if (parentUuid) params = params.set('parentUuid', parentUuid);
    if (!replace && lastNode) params = params.set('lastFileName', lastNode.name);

    this.isLoading.set(true);
    try {
      const newNodes = await firstValueFrom(this.http.get<FileNode[]>("/api/files", { params }));

      if (replace) {
        this.nodes.set(newNodes);
      } else {
        this.nodes.update(old => [...old, ...newNodes]);
      }
    } catch (err) {
      console.error("Failed to load files", err);
    } finally {
      this.isLoading.set(false);
    }
  }

  changeParent(uuid: string) {
    this.ariane.update(prev => [...prev, uuid]);
    this.loadFiles(true);
  }

  goBack() {
    this.ariane.update(prev => {
      if (prev.length <= 1) return prev;
      const next = [...prev];
      next.pop();
      return next;
    });
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
