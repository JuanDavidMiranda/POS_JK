import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';

export interface SupervisorPinResult {
  authorizedBy: string;
  authorizedName: string;
}

@Component({
  selector: 'app-supervisor-pin-modal',
  templateUrl: './supervisor-pin-modal.html',
  styleUrl: './supervisor-pin-modal.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SupervisorPinModal {
  @Input() open = false;
  @Input() title = 'Autorización de supervisor';
  @Input() message = 'Ingresa el PIN de supervisor para continuar.';
  @Input() error = '';
  @Input() busy = false;

  @Output() pinSubmit = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  readonly pin = signal('');

  press(key: string): void {
    if (this.busy) return;
    const current = this.pin();
    if (key === 'back') {
      this.pin.set(current.slice(0, -1));
      return;
    }
    if (key === 'clear') {
      this.pin.set('');
      return;
    }
    if (current.length >= 6) return;
    if (/^\d$/.test(key)) {
      this.pin.set(current + key);
    }
  }

  submit(): void {
    if (this.busy) return;
    const value = this.pin();
    if (value.length < 4) return;
    this.pinSubmit.emit(value);
  }

  close(): void {
    if (this.busy) return;
    this.pin.set('');
    this.cancelled.emit();
  }

  masked(): string {
    return '•'.repeat(this.pin().length);
  }
}