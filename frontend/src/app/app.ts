import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { AuthService } from './services/auth';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet, NzButtonModule, NzIconModule, NzLayoutModule, NzMenuModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private authService = inject(AuthService);
  private router = inject(Router);

  isCollapsed = false;
  isAuthenticated = this.authService.isAuthenticated;
  isAdmin = this.authService.isAdmin;

  logout() {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }
}
