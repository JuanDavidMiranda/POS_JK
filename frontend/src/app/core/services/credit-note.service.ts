import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { db } from '../db/pos-offline.database';
import { environment } from '../../../environments/environment';
import { round2, type CreditNoteRecord } from '../models/pos.models';

export interface CreateCreditNoteInput {
  id: string;
  saleId: string;
  terminalId: string;
  reasonCode: '1' | '2' | '3';
  reasonDescription: string;
  items: { productId: string; quantity: number }[];
}

@Injectable({ providedIn: 'root' })
export class CreditNoteService {
  private readonly http = inject(HttpClient);

  async create(input: CreateCreditNoteInput): Promise<CreditNoteRecord> {
    if (!navigator.onLine) {
      throw new Error('Se requiere conexión para transmitir la nota crédito a la DIAN');
    }

    const response = await firstValueFrom(
      this.http.post<CreditNoteRecord>(`${environment.apiBaseUrl}/api/v1/credit-notes`, input),
    );

    await db.transaction('rw', db.salesHistory, db.products, db.creditNotes, db.inventoryMovements, async () => {
      const sale = await db.salesHistory.get(input.saleId);
      if (sale) {
        const creditNotes = [...(sale.creditNotes ?? []), response];
        await db.salesHistory.put({
          ...sale,
          status: (response.saleStatus as any) ?? sale.status,
          creditNotes,
        });
      }

      for (const item of response.items) {
        const product = await db.products.get(item.productId);
        const previous = product?.currentStock ?? 0;
        const next = round2(previous + item.quantity);
        if (product) {
          await db.products.update(item.productId, {
            currentStock: next,
            updatedAt: response.createdAt,
          });
        }
        await db.inventoryMovements.put({
          id: `${response.id}-${item.productId}`,
          productId: item.productId,
          movementType: 'CREDIT_NOTE_IN',
          quantity: item.quantity,
          previousStock: previous,
          newStock: next,
          referenceId: response.id,
          notes: `Nota crédito ${response.fullDocumentNumber} - ${response.reasonDescription}`,
          createdAt: response.createdAt,
          pendingSync: false,
        });
      }

      await db.creditNotes.put(response);
    });

    return response;
  }
}
