$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8080/api/v1/sync'
$headers = @{
  'X-Tenant-Id' = '11111111-1111-1111-1111-111111111111'
  'Content-Type' = 'application/json'
}
$docker = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'

$pull = $null
for ($i = 0; $i -lt 60; $i++) {
  try {
    $pull = Invoke-RestMethod -Uri "$base/pull?since=1970-01-01T00:00:00Z" -Headers $headers -TimeoutSec 3
    break
  } catch {
    Start-Sleep -Seconds 2
  }
}
if (-not $pull) { throw 'El backend no respondió en 120 s' }

Write-Output "PULL: productos=$($pull.products.Count) clientes=$($pull.customers.Count) consecutivo=$($pull.terminal.nextSequence)"

$product = $pull.products | Where-Object { $_.sku -eq 'BEB-001' }
$customer = $pull.customers | Where-Object { $_.documentNumber -eq '222222222222' }
$saleId = [guid]::NewGuid().ToString()
$itemId = [guid]::NewGuid().ToString()
$idempotencyKey = [guid]::NewGuid().ToString()
$soldAt = [DateTime]::UtcNow.ToString('o')

$eventPayload = @{
  id                   = $saleId
  terminalId           = $pull.terminal.id
  customerId           = $customer.id
  documentType         = 'FV'
  dianPrefix           = 'POS1'
  sequenceNumber       = [long]$pull.terminal.nextSequence
  fullDocumentNumber   = 'POS1-000001'
  dianStatus           = 'PENDING'
  subtotal             = 5000.00
  taxTotal             = 950.00
  grandTotal           = 5950.00
  paymentMethod        = 'EFECTIVO'
  soldAt               = $soldAt
  items                = @(@{
    id         = $itemId
    productId  = $product.id
    quantity   = 2
    unitPrice  = 2500.00
    taxRate    = 0.19
    taxAmount  = 950.00
    lineTotal  = 5950.00
  })
}
$body = @{ events = @(@{
  idempotencyKey = $idempotencyKey
  entityType     = 'SALE'
  payload        = $eventPayload
}) } | ConvertTo-Json -Depth 12

$first = Invoke-RestMethod -Method Post -Uri "$base/push" -Headers $headers -Body $body
Write-Output "PUSH 1: $($first.results[0].status) → $($first.results[0].fullDocumentNumber)"

$second = Invoke-RestMethod -Method Post -Uri "$base/push" -Headers $headers -Body $body
Write-Output "PUSH 2 (reintento): $($second.results[0].status)"

$after = Invoke-RestMethod -Uri "$base/pull?since=1970-01-01T00:00:00Z" -Headers $headers
$stock = ($after.products | Where-Object { $_.sku -eq 'BEB-001' }).currentStock
Write-Output "STOCK LOCAL SERVIDOR: $stock (esperado 46)"
Write-Output "CONSECUTIVO SERVIDOR: $($after.terminal.lastSyncedSequence) (esperado 1)"

$sql = "select 'sales='||count(*) from sales union all select 'items='||count(*) from sale_items union all select 'inbox='||count(*) from sync_inbox_log union all select 'stock='||current_stock::text from products where sku='BEB-001' union all select 'seq='||last_synced_sequence::text from pos_terminals where dian_prefix='POS1';"
& $docker exec pos-saas-postgres psql -U pos -d pos_saas -tAc $sql
