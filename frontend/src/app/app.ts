import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { AuthService } from './services/auth';
import { TransferTray } from './components/shared/transfer-tray/transfer-tray';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet, NzButtonModule, NzIconModule, NzLayoutModule, NzMenuModule, TransferTray],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected authService = inject(AuthService);
  private router = inject(Router);

  isCollapsed = false;

  logout() {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }
}
