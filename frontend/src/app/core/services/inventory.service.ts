import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { v7 as uuidv7 } from 'uuid';
import { db } from '../db/pos-offline.database';
import { environment } from '../../../environments/environment';
import { round2, type InventoryMovementRecord, type MovementType } from '../models/pos.models';

export interface LowStockProduct {
  id: string;
  branchId?: string;
  sku?: string;
  name: string;
  currentStock: number;
  minStock: number;
  allowNegativeStock?: boolean;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type KardexPage = PageResponse<InventoryMovementRecord>;

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly http = inject(HttpClient);

  async lowStock(branchId?: string | null): Promise<LowStockProduct[]> {
    if (navigator.onLine) {
      try {
        const params: Record<string, string> = {};
        if (branchId) params['branchId'] = branchId;
        const remote = await firstValueFrom(
          this.http.get<LowStockProduct[]>(`${environment.apiBaseUrl}/api/v1/inventory/low-stock`, { params }),
        );
        return remote;
      } catch {
        // fallback local
      }
    }
    const products = await db.products.toArray();
    return products
      .filter((p) => Number(p.currentStock) <= Number(p.minStock ?? 0))
      .sort((a, b) => Number(a.currentStock) - Number(b.currentStock))
      .map((p) => ({
        id: p.id,
        sku: p.sku,
        name: p.name,
        currentStock: Number(p.currentStock),
        minStock: Number(p.minStock ?? 0),
        allowNegativeStock: false,
      }));
  }

  async kardex(productId: string, page = 0, size = 20, branchId?: string | null): Promise<KardexPage> {
    const remotePromise = navigator.onLine
      ? firstValueFrom(
          this.http.get<KardexPage>(`${environment.apiBaseUrl}/api/v1/inventory/kardex/${productId}`, {
            params: { page: String(page), size: String(size), ...(branchId ? { branchId } : {}) },
          }),
        ).catch(() => null)
      : Promise.resolve(null);
    const remote = await remotePromise;
    if (remote) {
      await db.inventoryMovements.bulkPut(remote.content.map((m) => ({ ...m, pendingSync: false })));
      return remote;
    }

    const all = await db.inventoryMovements
      .where('productId')
      .equals(productId)
      .filter((m) => !branchId || m.branchId === branchId)
      .reverse()
      .sortBy('createdAt');
    const start = page * size;
    const content = all.slice(start, start + size);
    return { content, page, size, totalElements: all.length, totalPages: Math.max(1, Math.ceil(all.length / size)) };
  }

  async registerMovement(input: {
    branchId?: string | null;
    productId: string;
    movementType: MovementType;
    quantity: number;
    notes: string;
  }): Promise<InventoryMovementRecord> {
    if (input.quantity <= 0) {
      throw new Error('La cantidad debe ser mayor a cero');
    }
    if (!input.notes.trim()) {
      throw new Error('La observación es obligatoria');
    }
    if (navigator.onLine) {
      try {
        const response = await firstValueFrom(
          this.http.post<InventoryMovementRecord>(`${environment.apiBaseUrl}/api/v1/inventory/movements`, input),
        );
        await this.applyLocalMovement(response, false);
        return response;
      } catch (error) {
        if (navigator.onLine) {
          throw error;
        }
      }
    }
    return this.registerLocalMovement(input);
  }

  private async registerLocalMovement(input: {
    branchId?: string | null;
    productId: string;
    movementType: MovementType;
    quantity: number;
    notes: string;
  }): Promise<InventoryMovementRecord> {
    const now = new Date().toISOString();
    return db.transaction('rw', db.products, db.inventoryMovements, async () => {
      const product = await db.products.get(input.productId);
      if (!product) {
        throw new Error('Producto no encontrado');
      }
      const previous = product.currentStock;
      const delta =
        input.movementType === 'PURCHASE_IN' || input.movementType === 'ADJUSTMENT_IN' || input.movementType === 'INITIAL_BALANCE' || input.movementType === 'CREDIT_NOTE_IN' || input.movementType === 'TRANSFER_IN'
          ? input.quantity
          : -input.quantity;
      const next = round2(previous + delta);
      if (next < 0) {
        throw new Error(`Stock insuficiente para ${product.name} (actual: ${previous})`);
      }
      await db.products.update(product.id, { currentStock: next, updatedAt: now });
      const movement: InventoryMovementRecord = {
        id: uuidv7(),
        branchId: input.branchId ?? null,
        productId: product.id,
        sku: product.sku,
        productName: product.name,
        movementType: input.movementType,
        quantity: input.quantity,
        previousStock: previous,
        newStock: next,
        referenceId: null,
        notes: input.notes,
        createdBy: null,
        createdAt: now,
        pendingSync: true,
      };
      await db.inventoryMovements.put(movement);
      return movement;
    });
  }

  private async applyLocalMovement(response: InventoryMovementRecord, pendingSync: boolean): Promise<void> {
    await db.transaction('rw', db.products, db.inventoryMovements, async () => {
      await db.products.update(response.productId, {
        currentStock: response.newStock ?? (response as any).resultingStock,
        updatedAt: response.createdAt,
      });
      await db.inventoryMovements.put({ ...response, pendingSync });
    });
  }
}
