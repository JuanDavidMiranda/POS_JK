import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
  <div class="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-emerald-900 flex items-center justify-center p-4">
    <div class="w-full max-w-md rounded-2xl bg-white shadow-xl p-8">
      <div class="mb-6 text-center">
        <div class="inline-flex items-center gap-2 font-bold text-slate-800 text-xl mb-1">
          <span class="rounded-lg bg-emerald-600 px-2 py-1 text-white">POS</span> SaaS Colombia
        </div>
        <p class="text-slate-500 text-sm">Inicia sesión en tu cuenta</p>
      </div>

      @if (error()) {
        <div class="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error() }}</div>
      }

      <form (ngSubmit)="submit()" class="space-y-4">
        <label class="block">
          <span class="text-sm font-medium text-slate-700">Correo electrónico</span>
          <input [(ngModel)]="email" name="email" type="email" required autocomplete="email"
                 class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-200" placeholder="admin@tuempresa.com" />
        </label>
        <label class="block">
          <span class="text-sm font-medium text-slate-700">Contraseña</span>
          <input [(ngModel)]="password" name="password" type="password" required autocomplete="current-password"
                 class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-200" placeholder="••••••••" />
        </label>
        <button type="submit" [disabled]="loading()"
                class="w-full rounded-lg bg-emerald-600 px-4 py-2.5 font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">
          {{ loading() ? 'Ingresando…' : 'Ingresar' }}
        </button>
      </form>

      <div class="mt-6 text-center text-sm text-slate-600">
        ¿No tienes cuenta?
        <a routerLink="/register" class="font-semibold text-emerald-700 hover:underline">Regístrate gratis (14 días)</a>
      </div>
      <p class="mt-4 text-xs text-slate-400 text-center">Super Admin demo: superadmin@poscolombia.co / SuperAdmin123!</p>
    </div>
  </div>
  `
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  email = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal('');

  async submit(): Promise<void> {
    this.error.set('');
    this.loading.set(true);
    try {
      const res = await this.auth.login({ email: this.email.trim(), password: this.password });
      if (res.user.role === 'SUPER_ADMIN') {
        await this.router.navigateByUrl('/super-admin');
      } else if (res.tenant && !res.tenant.onboardingCompleted) {
        this.auth.clearOnboardingDeferral();
        await this.router.navigateByUrl('/onboarding');
      } else {
        await this.router.navigateByUrl('/pos');
      }
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible iniciar sesión.');
    } finally {
      this.loading.set(false);
    }
  }
}
