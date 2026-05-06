import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Upload } from './components/upload/upload';
import {Store} from './components/store/store';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Upload, Store],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('frontend');
}
