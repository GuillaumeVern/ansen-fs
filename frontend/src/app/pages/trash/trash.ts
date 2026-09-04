import { Component, OnInit, inject, signal } from '@angular/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzContextMenuService, NzDropdownMenuComponent, NzDropdownModule } from 'ng-zorro-antd/dropdown';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzTableModule } from 'ng-zorro-antd/table';

import { StoreService, TrashedFileNode } from '../../services/store';
import { formatBytes } from '../../shared/format';
import { TYPE_ICON } from '../../shared/file-icons';

@Component({
  selector: 'app-trash',
  imports: [NzButtonModule, NzIconModule, NzDropdownModule, NzMenuModule, NzTableModule],
  templateUrl: './trash.html',
  styleUrl: './trash.scss',
})
export class Trash implements OnInit {
  private storeService = inject(StoreService);
  private nzContextMenuService = inject(NzContextMenuService);

  protected items = signal<TrashedFileNode[]>([]);
  protected isLoading = signal(false);
  protected activeItem: TrashedFileNode | null = null;

  async ngOnInit() {
    await this.refresh();
  }

  async refresh(): Promise<void> {
    this.isLoading.set(true);
    try {
      this.items.set(await this.storeService.getTrash());
    } catch (err) {
      console.error('Failed to load the bin', err);
      alert('Could not load the bin. Please try again.');
    } finally {
      this.isLoading.set(false);
    }
  }

  openContextMenu(event: MouseEvent, menu: NzDropdownMenuComponent, item: TrashedFileNode): void {
    this.activeItem = item;
    this.nzContextMenuService.create(event, menu);
  }

  async restore(item: TrashedFileNode | null): Promise<void> {
    if (!item) return;

    const success = await this.storeService.restoreItem(item.uuid);
    if (success) {
      this.items.update((list) => list.filter((i) => i.uuid !== item.uuid));
    } else {
      alert(`Could not restore "${item.name}". Please try again.`);
    }
  }

  async deleteForever(item: TrashedFileNode | null): Promise<void> {
    if (!item) return;
    if (!confirm(`Permanently delete "${item.name}"? This cannot be undone.`)) return;

    const success = await this.storeService.deleteForever(item.uuid);
    if (success) {
      this.items.update((list) => list.filter((i) => i.uuid !== item.uuid));
    } else {
      alert(`Could not delete "${item.name}". Please try again.`);
    }
  }

  protected typeIcon(item: TrashedFileNode): string {
    return TYPE_ICON[item.type];
  }

  protected displaySize(item: TrashedFileNode): string {
    const formatted = formatBytes(item.size);
    return item.type === 'FOLDER' ? `${formatted} (folder)` : formatted;
  }

  protected formattedDeletedAt(deletedAt: string): string {
    const date = new Date(deletedAt.replace(' ', 'T') + 'Z');
    return isNaN(date.getTime()) ? deletedAt : date.toLocaleString();
  }
}
