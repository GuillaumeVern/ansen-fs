import {EventEmitter, Injectable, OnInit} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class StoreService implements OnInit {
  protected ariane: string[] = [];
  changeParent: EventEmitter<string> = new EventEmitter<string>();
  ngOnInit() {
    this.ariane.push("")
    this.changeParent.subscribe((parentUuid) => {
      this.ariane.push(parentUuid)
    })
  }

  goBack(position?: number) {
    if (!position) {
      this.changeParent.emit(this.ariane[-1]);
      this.ariane.pop()
    } else {
      this.changeParent.emit(this.ariane[position])
      this.ariane.slice(0, position);
    }
  }
}
