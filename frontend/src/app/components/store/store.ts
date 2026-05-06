import {Component, inject, OnInit, signal, ViewChild, WritableSignal} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import { ScrollingModule, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import {StoreService} from '../../services/store';
import {StoreElement} from './store-element/store-element';

export interface FileNode {
  uuid: string
  parentUuid: string
  name: string
  type: string
  hash: string
  size: number
}

@Component({
  selector: 'app-store',
  imports: [ScrollingModule, StoreElement],
  templateUrl: './store.html',
  styleUrl: './store.scss',
})
export class Store implements OnInit {
  @ViewChild(CdkVirtualScrollViewport) viewport!: CdkVirtualScrollViewport;
  protected fileNodes: WritableSignal<FileNode[]> = signal([]);
  private http = inject(HttpClient);
  protected parentUuid = ""
  private isLoading = false;
  private storeService: StoreService = inject(StoreService)

  ngOnInit() {
    this.getMoreFiles(this.parentUuid)
    this.storeService.changeParent.subscribe((parentUuid) => this.changeParent(parentUuid))
  }

  onScrollIndexChange(index: number) {
    const end = this.viewport.getRenderedRange().end;
    const total = this.viewport.getDataLength();

    if (end === total && !this.isLoading && total > 0) {
      const lastNode = this.fileNodes().at(-1);
      if (lastNode) {
        this.getMoreFiles(this.parentUuid, lastNode.name);
      }
    }
  }

  getMoreFiles(parentUuid?: string, lastFileName?: string) {
    this.isLoading = true;
    let url: string = "/api/files";
    let httpParams: HttpParams = new HttpParams();
    if (parentUuid) {
      httpParams = httpParams.set('parentUuid', parentUuid);
    }
    if (lastFileName) {
      httpParams = httpParams.set('lastFileName', lastFileName);
    }
    this.http.get<FileNode[]>(url, {params: httpParams}).subscribe({
      next: (fileNodes) => {
        this.fileNodes.update((originalFileNodes: FileNode[]) => {
          return [...originalFileNodes, ...fileNodes]
        })
        console.log(this.fileNodes())
        this.isLoading = false;
      },
      error: (response) => {
        console.error(response);
        this.isLoading = false;
      }
    })
  }

  changeParent(parentUuid: string) {
    this.isLoading = true;
    let url: string = "/api/files";
    let httpParams: HttpParams = new HttpParams();
    if (parentUuid) {
      httpParams = httpParams.set('parentUuid', parentUuid);
      this.parentUuid = parentUuid;
    } else {
      this.parentUuid = "";
    }
    this.http.get<FileNode[]>(url, {params: httpParams}).subscribe({
      next: (fileNodes) => {
        this.fileNodes.set([...fileNodes])
        console.log(this.fileNodes())
        this.isLoading = false;
      },
      error: (response) => {
        console.error(response);
        this.isLoading = false;
      }
    })
  }

  goBack() {
    this.storeService.goBack();
  }
}
