import { Component } from '@angular/core';
import {Store} from '../../components/store/store';
import {Upload} from '../../components/upload/upload';

@Component({
  selector: 'app-files',
  imports: [
    Store,
    Upload
  ],
  templateUrl: './files.html',
  styleUrl: './files.scss',
})
export class Files {}
