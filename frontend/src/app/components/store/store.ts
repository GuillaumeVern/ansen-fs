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
import { FileNode, StoreService } from '../../services/store';
import { StoreElement } from './store-element/store-element';
import {NzBreadCrumbComponent, NzBreadCrumbItemComponent} from 'ng-zorro-antd/breadcrumb';
import {Upload} from '../upload/upload';
import {NzIconModule} from 'ng-zorro-antd/icon';
import {NzButtonComponent} from 'ng-zorro-antd/button';
import {NzCheckboxModule} from 'ng-zorro-antd/checkbox';
import {NzTableModule, NzTableSortFn} from 'ng-zorro-antd/table';
import {NzContextMenuService, NzDropdownMenuComponent, NzDropdownModule} from 'ng-zorro-antd/dropdown';
import {NzMenuModule} from 'ng-zorro-antd/menu';
import {TransferService} from '../../services/transfer';
import {downloadFile} from '../../shared/file-download';
import {formatBytes} from '../../shared/format';
import {TYPE_ICON} from '../../shared/file-icons';

interface ListColumn {
  key: string;
  label: string;
  sortFn: NzTableSortFn<FileNode>;
}

const LIST_COLUMNS: ListColumn[] = [
  { key: 'name', label: 'Name', sortFn: (a, b) => a.name.localeCompare(b.name) },
  { key: 'type', label: 'Type', sortFn: (a, b) => a.type.localeCompare(b.type) },
  { key: 'size', label: 'Size', sortFn: (a, b) => a.size - b.size },
];

@Component({
  selector: 'app-store',
  imports: [
    ScrollingModule,
    StoreElement,
    NzBreadCrumbComponent,
    NzBreadCrumbItemComponent,
    NzIconModule,
    NzButtonComponent,
    NzCheckboxModule,
    NzTableModule,
    NzDropdownModule,
    NzMenuModule,
  ],
  templateUrl: './store.html',
  styleUrl: './store.scss',
})
export class Store implements OnInit {
  private storeService = inject(StoreService);
  private transferService = inject(TransferService);
  private nzContextMenuService = inject(NzContextMenuService);
  private container = viewChild<ElementRef>('container');

  @ViewChild(CdkVirtualScrollViewport) viewport!: CdkVirtualScrollViewport;

  protected fileNodes = this.storeService.fileNodes;
  protected isLoading = this.storeService.isLoading;
  protected isExhausted = this.storeService.isExhausted;
  protected arianeHistory = this.storeService.ariane;

  protected isDraggingOver = false;
  protected isListView = signal(false);
  protected readonly listColumns = LIST_COLUMNS;

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

  protected downloadingUuids = signal<Set<string>>(new Set());
  protected activeNode: FileNode | null = null;

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

  async ngOnInit() {
    await this.storeService.initHome();
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

  onCrumbKeydown(event: KeyboardEvent, index: number, isLast: boolean): void {
    if (isLast) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.navigateToIndex(index);
    }
  }

  deleteItem(uuid: string): void {
    this.storeService.deleteItem(uuid).then(success => {
      if (!success) {
        alert('Could not complete the file deletion action. Please try again.');
      }
    });
  }

  toggleListView(checked: boolean): void {
    this.isListView.set(checked);
  }

  loadMore(): void {
    this.storeService.loadFiles(false);
  }

  openRow(node: FileNode): void {
    if (node.type === 'FOLDER') {
      this.storeService.changeParent(node.name, node.uuid);
    }
  }

  onRowKeydown(event: KeyboardEvent, node: FileNode): void {
    if (node.type !== 'FOLDER') return;

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.openRow(node);
    }
  }

  isDownloading(uuid: string): boolean {
    return this.downloadingUuids().has(uuid);
  }

  downloadNode(node: FileNode): void {
    this.downloadingUuids.update(s => new Set(s).add(node.uuid));

    downloadFile(this.storeService, this.transferService, node.uuid, node.name, () => {
      this.downloadingUuids.update(s => {
        const next = new Set(s);
        next.delete(node.uuid);
        return next;
      });
    });
  }

  onDeleteRowClick(node: FileNode | null): void {
    if (!node) return;

    if (confirm(`Are you sure you want to move "${node.name}" to the bin?`)) {
      this.deleteItem(node.uuid);
    }
  }

  openContextMenu(event: MouseEvent, menu: NzDropdownMenuComponent, node: FileNode): void {
    this.activeNode = node;
    this.nzContextMenuService.create(event, menu);
  }

  typeIcon(node: FileNode): string {
    return TYPE_ICON[node.type];
  }

  displaySize(node: FileNode): string {
    const formatted = formatBytes(node.size);
    return node.type === 'FOLDER' ? `${formatted} (folder)` : formatted;
  }
}
