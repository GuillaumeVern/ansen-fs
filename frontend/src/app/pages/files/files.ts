import { Component } from '@angular/core';
import {Store} from '../../components/store/store';

@Component({
  selector: 'app-files',
  imports: [
    Store
  ],
  templateUrl: './files.html',
  styleUrl: './files.scss',
})
export class Files {}
