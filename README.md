# POS SaaS Offline-First — MVP 1 (Colombia / DIAN-ready)

Monorepo con:

- `backend/`: Java 21 + Spring Boot 3.3 + PostgreSQL 16 + Flyway. Multi-tenant por `X-Tenant-Id`, sync idempotente.
- `frontend/`: Angular PWA + Tailwind + Dexie (IndexedDB). UUID v7, outbox, consecutivo DIAN local.
- `docker-compose.yml`: PostgreSQL 16 con `shared_buffers=256MB`.

## Arranque

En tres terminales distintas, desde la raíz del monorepo:

```powershell
# 1) Base de datos
docker compose up -d

# 2) Backend (el wrapper usa el JDK 21 y Maven instalados en esta máquina)
cd backend; .\mvnw.cmd spring-boot:run

# 3) Frontend
cd frontend; npm start
```

Abrir http://localhost:4200. El tenant demo se envía automáticamente en el header `X-Tenant-Id`:
`11111111-1111-1111-1111-111111111111`.

> Nota: el contenedor publica PostgreSQL en `localhost:55432` para evitar el conflicto con las
> instancias nativas que ocupan 5432/5433 en esta máquina. El backend ya apunta a ese puerto.
> Para cambiarlo, ajusta `docker-compose.yml` y la variable `DB_URL` del backend.

## Flujo de prueba offline

1. Con internet: esperar a que el header muestre **Online** y que el catálogo se descargue (pull inicial).
2. Agregar productos al carrito, elegir medio de pago y **Cobrar**.
3. Cortar la red (DevTools → Network → Offline o desactivar Wi-Fi). El header pasa a **Offline**; cobrar sigue funcionando y el consecutivo `POS1-000001` se incrementa localmente.
4. Restaurar la red: el worker sincroniza cada 15 s (o pulsar **Sincronizar ahora**). El histórico cambia de `OFFLINE_PENDING` a `SYNCED`.
5. Repetir el envío del mismo lote no duplica ventas ni vuelve a descontar stock: el `idempotency_key` se registra en `sync_inbox_log`.

## Contrato sync v1

- `GET /api/v1/sync/pull?since={isoTimestamp}` → `{ tenantId, serverTime, batchId, terminal, products[], customers[] }`.
- `POST /api/v1/sync/push` → `{ batchId, processedAt, results[] }` con eventos
  `{ idempotencyKey, entityType: "SALE", payload: { ...sale, items[] } }`.
