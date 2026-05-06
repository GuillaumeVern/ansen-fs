import {Component, inject, Input} from '@angular/core';
import {StoreService} from '../../../services/store';
import {FileNode} from '../store';

@Component({
  selector: 'app-store-element',
  imports: [],
  templateUrl: './store-element.html',
  styleUrl: './store-element.scss',
})
export class StoreElement {
  @Input({ required: true }) data!: FileNode;
  private storeService = inject(StoreService);

  open() {
    if (this.data.type === 'FOLDER') {
      this.storeService.changeParent.emit(this.data.uuid);
    }
  }
}
