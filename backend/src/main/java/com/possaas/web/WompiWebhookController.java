package com.possaas.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.service.BillingService;
import com.possaas.service.WompiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/public/webhooks")
@RequiredArgsConstructor
public class WompiWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WompiWebhookController.class);

    private final WompiService wompiService;
    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/wompi")
    public ResponseEntity<Map<String, Object>> handle(@RequestBody String rawBody, HttpServletRequest request) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            String checksumHeader = request.getHeader("X-Event-Checksum");
            if (checksumHeader == null) checksumHeader = request.getHeader("x-event-checksum");

            wompiService.verifyWebhookSignature(rawBody, checksumHeader, root);

            String eventName = root.path("event").asText("");
            JsonNode data = root.path("data");
            JsonNode tx = data.path("transaction");
            if (tx.isMissingNode() || tx.isNull()) {
                // sometimes flat
                tx = data.path("transaction") != null ? data.path("transaction") : data;
            }

            String reference = tx.path("reference").asText(null);
            String gatewayTxId = tx.path("id").asText(null);
            String status = tx.path("status").asText(null);
            String paymentMethodType = tx.path("payment_method_type").asText(null);

            if (reference == null || reference.isBlank()) {
                log.warn("Webhook Wompi sin reference, ignorado. event={}", eventName);
                return ResponseEntity.ok(Map.of("received", true, "ignored", true));
            }

            if ("APPROVED".equalsIgnoreCase(status)) {
                billingService.processApprovedPayment(reference, gatewayTxId, paymentMethodType, rawBody);
            } else if (status != null && !status.isBlank()) {
                Set<String> known = Set.of("DECLINED", "VOIDED", "ERROR", "PENDING");
                if ((eventName != null && eventName.contains("transaction")) || known.contains(status.toUpperCase())) {
                    billingService.processNonApprovedPayment(reference, gatewayTxId, status, paymentMethodType, rawBody);
                }
            }

            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception e) {
            log.warn("Webhook Wompi rechazado/procesado con error controlado: {}", e.getMessage());
            if (e instanceof org.springframework.web.server.ResponseStatusException rse) {
                if (rse.getStatusCode().value() == 401 || rse.getStatusCode().value() == 403) {
                    return ResponseEntity.status(rse.getStatusCode()).body(Map.of("received", false, "error", rse.getReason() == null ? "unauthorized" : rse.getReason()));
                }
                // idempotent/conflict cases: acknowledge to avoid retries
                return ResponseEntity.ok(Map.of("received", true, "note", rse.getReason() == null ? "ignored" : rse.getReason()));
            }
            return ResponseEntity.ok(Map.of("received", true, "error", "ignored"));
        }
    }
}
