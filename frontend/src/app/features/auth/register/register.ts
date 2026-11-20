import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
  <div class="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-emerald-900 flex items-center justify-center p-4">
    <div class="w-full max-w-2xl rounded-2xl bg-white shadow-xl p-8">
      <div class="mb-6 text-center">
        <div class="inline-flex items-center gap-2 font-bold text-slate-800 text-xl mb-1">
          <span class="rounded-lg bg-emerald-600 px-2 py-1 text-white">POS</span> Crea tu cuenta
        </div>
        <p class="text-slate-500 text-sm">Prueba gratis por 14 días. Sin tarjeta de crédito.</p>
      </div>

      @if (error()) {
        <div class="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error() }}</div>
      }

      <form (ngSubmit)="submit()" class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="md:col-span-2 text-sm font-semibold text-slate-700 border-b pb-1">Datos del comercio</div>
        <label class="block">
          <span class="text-sm font-medium text-slate-700">NIT *</span>
          <input [(ngModel)]="nit" name="nit" required class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" placeholder="900123456-1" />
        </label>
        <label class="block">
          <span class="text-sm font-medium text-slate-700">Nombre comercial *</span>
          <input [(ngModel)]="tradeName" name="tradeName" required class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" placeholder="Mi Negocio" />
        </label>
        <label class="block md:col-span-2">
          <span class="text-sm font-medium text-slate-700">Razón social *</span>
          <input [(ngModel)]="businessName" name="businessName" required class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" placeholder="Mi Negocio S.A.S." />
        </label>
        <label class="block">
          <span class="text-sm font-medium text-slate-700">Ciudad</span>
          <input [(ngModel)]="city" name="city" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
        </label>
        <label class="block">
          <span class="text-sm font-medium text-slate-700">Teléfono</span>
          <input [(ngModel)]="phone" name="phone" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
        </label>

        <div class="md:col-span-2 text-sm font-semibold text-slate-700 border-b pb-1 mt-2">Usuario administrador</div>
        <label class="block">
          <span class="text-sm font-medium text-slate-700">Nombre completo *</span>
          <input [(ngModel)]="adminName" name="adminName" required class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
        </label>
        <label class="block">
          <span class="text-sm font-medium text-slate-700">Correo electrónico *</span>
          <input [(ngModel)]="adminEmail" name="adminEmail" type="email" required class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
        </label>
        <label class="block md:col-span-2">
          <span class="text-sm font-medium text-slate-700">Contraseña * (mín. 6 caracteres)</span>
          <input [(ngModel)]="password" name="password" type="password" required minlength="6" class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5" />
        </label>

        <div class="md:col-span-2 pt-2">
          <button type="submit" [disabled]="loading()"
                  class="w-full rounded-lg bg-emerald-600 px-4 py-3 font-semibold text-white hover:bg-emerald-700 disabled:opacity-60">
            {{ loading() ? 'Creando cuenta…' : 'Comenzar prueba gratis de 14 días' }}
          </button>
        </div>
      </form>

      <div class="mt-6 text-center text-sm text-slate-600">
        ¿Ya tienes cuenta? <a routerLink="/login" class="font-semibold text-emerald-700 hover:underline">Inicia sesión</a>
      </div>
    </div>
  </div>
  `
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  nit = ''; businessName = ''; tradeName = ''; city = ''; phone = '';
  adminName = ''; adminEmail = ''; password = '';
  readonly loading = signal(false);
  readonly error = signal('');

  async submit(): Promise<void> {
    if (!this.nit.trim() || !this.businessName.trim() || !this.tradeName.trim() || !this.adminName.trim() || !this.adminEmail.trim() || this.password.length < 6) {
      this.error.set('Completa los campos obligatorios. La contraseña debe tener al menos 6 caracteres.');
      return;
    }
    this.error.set(''); this.loading.set(true);
    try {
      await this.auth.register({
        nit: this.nit.trim(), businessName: this.businessName.trim(), tradeName: this.tradeName.trim(),
        city: this.city.trim() || undefined, phone: this.phone.trim() || undefined,
        adminName: this.adminName.trim(), adminEmail: this.adminEmail.trim(), password: this.password
      });
      this.auth.clearOnboardingDeferral();
      await this.router.navigateByUrl('/onboarding');
    } catch (e: any) {
      this.error.set(e?.error?.message || e?.message || 'No fue posible completar el registro.');
    } finally {
      this.loading.set(false);
    }
  }
}
