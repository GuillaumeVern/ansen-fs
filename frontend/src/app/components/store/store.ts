import {
  Component,
  computed,
  effect,
  ElementRef,
  inject, input,
  OnInit,
  signal,
  viewChild,
  ViewChild,
} from '@angular/core';
import { ScrollingModule, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { StoreService } from '../../services/store';
import { StoreElement } from './store-element/store-element';
import {NzBreadCrumbComponent, NzBreadCrumbItemComponent} from 'ng-zorro-antd/breadcrumb';
import {Upload} from '../upload/upload';
import {NzIconDirective} from 'ng-zorro-antd/icon';
import {NzButtonComponent} from 'ng-zorro-antd/button';

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
  imports: [ScrollingModule, StoreElement, NzBreadCrumbComponent, NzBreadCrumbItemComponent, NzIconDirective, NzButtonComponent],
  templateUrl: './store.html',
  styleUrl: './store.scss',
})
export class Store implements OnInit {
  private storeService = inject(StoreService);
  private container = viewChild<ElementRef>('container');

  @ViewChild(CdkVirtualScrollViewport) viewport!: CdkVirtualScrollViewport;

  protected fileNodes = this.storeService.fileNodes;
  protected isLoading = this.storeService.isLoading;
  protected arianeHistory = this.storeService.ariane;

  protected isDraggingOver = false;

  protected readonly itemHeight = 320;
  protected readonly itemWidth = 200;
  private containerWidth = signal(0);

  public uploadController = input<Upload | null>(null);

  protected columns = computed(() => {
    const width = this.containerWidth();
    if (width === 0) return 1;

    return Math.max(1, Math.floor(width / (this.itemWidth + 20)));
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

  navigateToIndex(targetIndex: number) {
    this.storeService.jumpToBreadcrumbIndex(targetIndex);
  }

  deleteItem(uuid: string): void {
    this.storeService.deleteItem(uuid).then(success => {
      if (!success) {
        alert('Could not complete the file deletion action. Please try again.');
      }
    });
  }
}
