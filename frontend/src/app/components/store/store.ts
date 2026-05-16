import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
  ViewChild,
} from '@angular/core';
import { ScrollingModule, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { StoreService } from '../../services/store';
import { StoreElement } from './store-element/store-element';

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
  private storeService = inject(StoreService);
  private container = viewChild<ElementRef>('container');

  @ViewChild(CdkVirtualScrollViewport) viewport!: CdkVirtualScrollViewport;

  protected fileNodes = this.storeService.fileNodes;
  protected isLoading = this.storeService.isLoading;

  protected readonly itemHeight = 200;
  protected readonly minItemWidth = 200;
  private containerWidth = signal(0);

  protected columns = computed(() => {
    return Math.max(1, Math.floor(this.containerWidth() / this.minItemWidth));
  });

  protected rows = computed(() => {
    const nodes = this.fileNodes();
    const cols = this.columns();
    const chunked = [];
    for (let i = 0; i < nodes.length; i += cols) {
      chunked.push(nodes.slice(i, i + cols));
    }
    return chunked;
  });

  constructor() {
    effect((onCleanup) => {
      const el = this.container()?.nativeElement;
      if (!el) return;

      const observer = new ResizeObserver(entries => {
        if (!entries || entries.length === 0) return;

        this.containerWidth.set(entries[0].contentRect.width);
      });

      observer.observe(el);
      onCleanup(() => observer.disconnect());
    });
  }

  ngOnInit() {
    this.storeService.loadFiles(true);
  }

  onScrollIndexChange(index: number) {
    const totalRows = this.rows().length;
    if (totalRows === 0 || this.isLoading()) return;

    const viewportHeight = this.viewport.elementRef.nativeElement.clientHeight;
    const rowsPerPage = Math.ceil(viewportHeight / this.itemHeight);

    if (index >= totalRows - rowsPerPage - 2) {
      this.storeService.loadFiles(false);
    }
  }

  goBack() {
    this.storeService.goBack();
  }

  trackByRow(i: number, row: any[]) {
    return row[0]?.uuid || i;
  }
}
