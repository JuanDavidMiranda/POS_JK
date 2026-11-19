package com.possaas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.domain.CreditNote;
import com.possaas.domain.Sale;
import com.possaas.dto.DianCreditNoteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class HttpElectronicBillingProvider implements ElectronicBillingProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpElectronicBillingProvider.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;
    private final String mode;
    private final String endpoint;
    private final String apiKey;

    public HttpElectronicBillingProvider(ObjectMapper objectMapper,
                                         @Value("${app.dian.mode:SIMULATED}") String mode,
                                         @Value("${app.dian.endpoint:}") String endpoint,
                                         @Value("${app.dian.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.mode = mode == null ? "SIMULATED" : mode;
        this.endpoint = endpoint == null ? "" : endpoint;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public DianCreditNoteResult sendCreditNote(UUID tenantId, CreditNote creditNote, Sale sale) {
        if (!"HTTP".equalsIgnoreCase(mode) || endpoint.isBlank()) {
            String cude = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
            log.info("DIAN simulado: nota {} aceptada para venta {} (CUDE {})",
                    creditNote.getFullDocumentNumber(), sale.getFullDocumentNumber(), cude);
            return new DianCreditNoteResult("ACCEPTED", cude, "Nota crédito aceptada (simulada)");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("creditNoteId", creditNote.getId());
            payload.put("saleId", sale.getId());
            payload.put("saleDocument", sale.getFullDocumentNumber());
            payload.put("creditNoteDocument", creditNote.getFullDocumentNumber());
            payload.put("reasonCode", creditNote.getReasonCode());
            payload.put("reasonDescription", creditNote.getReasonDescription());
            payload.put("subtotal", creditNote.getSubtotal());
            payload.put("taxTotal", creditNote.getTaxTotal());
            payload.put("grandTotal", creditNote.getGrandTotal());

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String cude = null;
                try {
                    JsonNode node = objectMapper.readTree(response.body());
                    if (node.hasNonNull("cude")) {
                        cude = node.get("cude").asText();
                    }
                } catch (Exception ignored) {
                    // Cuerpo no JSON: continuar con aceptación sin CUDE.
                }
                return new DianCreditNoteResult("ACCEPTED", cude, "Nota crédito aceptada por el proveedor DIAN");
            }

            return new DianCreditNoteResult("REJECTED", null,
                    "Proveedor DIAN respondió " + response.statusCode() + ": " + truncate(response.body(), 200));
        } catch (Exception ex) {
            log.warn("Error transmitiendo nota crédito a la DIAN: {}", ex.getMessage());
            return new DianCreditNoteResult("REJECTED", null, "No fue posible transmitir a la DIAN: " + ex.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
