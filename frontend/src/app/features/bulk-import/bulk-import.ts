import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { KeyValuePipe } from '@angular/common';
import {
  BulkImportService,
  type ImportEntityType,
  type ImportJobResponse,
  type ImportPreviewResponse,
} from '../../core/services/bulk-import.service';
import { SyncService } from '../../core/services/sync.service';

type Step = 1 | 2 | 3 | 4;

@Component({
  selector: 'app-bulk-import',
  templateUrl: './bulk-import.html',
  styleUrl: './bulk-import.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KeyValuePipe],
})
export class BulkImport implements OnInit, OnDestroy {
  private readonly importService = inject(BulkImportService);
  readonly sync = inject(SyncService);

  readonly step = signal<Step>(1);
  readonly entityType = signal<ImportEntityType>('PRODUCTS');
  readonly file = signal<File | null>(null);
  readonly dragOver = signal(false);

  readonly previewing = signal(false);
  readonly executing = signal(false);
  readonly preview = signal<ImportPreviewResponse | null>(null);
  readonly job = signal<ImportJobResponse | null>(null);
  readonly history = signal<ImportJobResponse[]>([]);
  readonly error = signal('');
  readonly info = signal('');
  readonly syncingCatalog = signal(false);

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  readonly progressPercent = computed(() => {
    const j = this.job();
    if (!j || j.totalRows <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((j.processedRows / j.totalRows) * 100));
  });

  async ngOnInit(): Promise<void> {
    await this.loadHistory();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  setEntityType(value: string): void {
    if (value === 'PRODUCTS' || value === 'CUSTOMERS') {
      this.entityType.set(value);
      this.resetFlow();
      this.error.set('');
      this.info.set('');
    }
  }

  async downloadTemplate(): Promise<void> {
    this.error.set('');
    try {
      await this.importService.downloadTemplate(this.entityType());
      this.info.set('Plantilla descargada correctamente.');
    } catch (e) {
      this.error.set(this.msg(e, 'No fue posible descargar la plantilla'));
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.setFile(file);
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0] ?? null;
    this.setFile(file);
  }

  private setFile(file: File | null): void {
    if (!file) {
      return;
    }
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      this.error.set('Solo se permiten archivos .xlsx');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.error.set('El archivo supera el tamaño máximo de 5 MB');
      return;
    }
    this.file.set(file);
    this.preview.set(null);
    this.job.set(null);
    this.step.set(2);
    this.error.set('');
    this.info.set(`Archivo seleccionado: ${file.name}`);
  }

  async runPreview(): Promise<void> {
    const file = this.file();
    if (!file) {
      this.error.set('Selecciona un archivo para continuar');
      return;
    }
    this.previewing.set(true);
    this.error.set('');
    this.info.set('');
    try {
      const preview = await this.importService.preview(this.entityType(), file);
      this.preview.set(preview);
      this.step.set(3);
    } catch (e) {
      this.error.set(this.msg(e, 'No fue posible generar la previsualización'));
    } finally {
      this.previewing.set(false);
    }
  }

  async execute(): Promise<void> {
    const file = this.file();
    if (!file) {
      this.error.set('Selecciona un archivo para continuar');
      return;
    }
    this.executing.set(true);
    this.error.set('');
    this.info.set('');
    try {
      const res = await this.importService.execute(this.entityType(), file);
      this.step.set(4);
      this.job.set({
        jobId: res.jobId,
        entityType: this.entityType(),
        fileName: file.name,
        status: 'PENDING',
        totalRows: this.preview()?.totalRows ?? 0,
        processedRows: 0,
        createdCount: 0,
        updatedCount: 0,
        rejectedCount: 0,
        hasRejectedFile: false,
        createdAt: new Date().toISOString(),
        finishedAt: null,
      });
      this.startPolling(res.jobId);
    } catch (e) {
      this.error.set(this.msg(e, 'No fue posible iniciar el cargue'));
    } finally {
      this.executing.set(false);
    }
  }

  private startPolling(jobId: string): void {
    this.stopPolling();
    const tick = async () => {
      try {
        const job = await this.importService.getJob(jobId);
        this.job.set(job);
        if (job.status === 'COMPLETED' || job.status === 'FAILED') {
          this.stopPolling();
          await this.loadHistory();
          if (job.status === 'COMPLETED') {
            this.info.set('Cargue finalizado. Revisa el resumen y sincroniza el catálogo POS si es necesario.');
          } else {
            this.error.set('El cargue finalizó con estado FAILED.');
          }
        }
      } catch (e) {
        // no cortar polling por un fallo transitorio de red; mostrar mensaje suave
        this.error.set(this.msg(e, 'No fue posible consultar el estado del cargue'));
      }
    };
    void tick();
    this.pollTimer = setInterval(() => void tick(), 1500);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  async downloadRejected(jobId?: string): Promise<void> {
    const id = jobId ?? this.job()?.jobId;
    if (!id) return;
    this.error.set('');
    try {
      await this.importService.downloadRejected(id);
    } catch (e) {
      this.error.set(this.msg(e, 'No fue posible descargar el archivo de rechazados'));
    }
  }

  async syncCatalogNow(): Promise<void> {
    this.syncingCatalog.set(true);
    this.error.set('');
    try {
      await this.sync.pull();
      this.info.set('Catálogo POS sincronizado correctamente.');
    } catch (e) {
      this.error.set(this.msg(e, 'No fue posible sincronizar el catálogo POS'));
    } finally {
      this.syncingCatalog.set(false);
    }
  }

  async loadHistory(): Promise<void> {
    try {
      this.history.set(await this.importService.history());
    } catch (e) {
      // historial es secundario; no bloquear la pantalla principal
      console.error(e);
    }
  }

  resetFlow(): void {
    this.stopPolling();
    this.step.set(1);
    this.file.set(null);
    this.preview.set(null);
    this.job.set(null);
  }

  backToStep2(): void {
    this.step.set(2);
    this.preview.set(null);
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'PENDING': return 'Pendiente';
      case 'PROCESSING': return 'Procesando';
      case 'COMPLETED': return 'Completado';
      case 'FAILED': return 'Fallido';
      default: return status;
    }
  }

  statusClass(status: string): string {
    switch (status) {
      case 'COMPLETED': return 'bg-emerald-100 text-emerald-800';
      case 'FAILED': return 'bg-rose-100 text-rose-800';
      case 'PROCESSING': return 'bg-sky-100 text-sky-800';
      default: return 'bg-slate-100 text-slate-700';
    }
  }

  entityLabel(type: string): string {
    return type === 'PRODUCTS' ? 'Productos' : 'Clientes';
  }

  formatDate(iso?: string | null): string {
    if (!iso) return '—';
    return new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));
  }

  rawKeys(row: { rawValues?: Record<string, unknown> } | null): string[] {
    return row?.rawValues ? Object.keys(row.rawValues) : [];
  }

  private msg(e: unknown, fallback: string): string {
    const any = e as any;
    return any?.error?.message || any?.error?.detail || any?.message || fallback;
  }
}
