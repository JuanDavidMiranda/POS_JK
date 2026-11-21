package com.possaas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class WompiService {

    private final ObjectMapper objectMapper;

    @Value("${app.wompi.mode:SANDBOX}")
    private String mode;

    @Value("${app.wompi.public-key:pub_test_placeholder}")
    private String publicKey;

    @Value("${app.wompi.integrity-secret:integrity_test_placeholder}")
    private String integritySecret;

    @Value("${app.wompi.events-secret:events_test_placeholder}")
    private String eventsSecret;

    @Value("${app.wompi.redirect-url:http://localhost:4200/admin/billing}")
    private String redirectUrl;

    @Value("${app.wompi.currency:COP}")
    private String currency;

    public WompiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isSandbox() {
        return !"PROD".equalsIgnoreCase(mode);
    }

    public String getPublicKey() { return publicKey; }
    public String getRedirectUrl() { return redirectUrl; }
    public String getCurrency() { return currency; }

    public String buildIntegritySignature(String reference, long amountInCents, String currencyCode) {
        String payload = reference + amountInCents + currencyCode + integritySecret;
        return sha256Hex(payload);
    }

    public void verifyWebhookSignature(String rawBody, String eventChecksumHeader, JsonNode root) {
        if (isSandbox() && "events_test_placeholder".equals(eventsSecret)) {
            return; // allow local sandbox simulator/testing without real secret
        }
        if (root != null) {
            JsonNode sig = root.path("signature");
            if (!sig.isMissingNode() && !sig.isNull()) {
                String checksum = sig.path("checksum").asText(null);
                if (checksum != null && !checksum.isBlank()) {
                    String computed = computeSignatureChecksum(root);
                    if (computed != null && constantTimeEquals(computed, checksum)) {
                        return;
                    }
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de webhook Wompi inválida");
                }
            }
        }
        if (eventChecksumHeader != null && !eventChecksumHeader.isBlank()) {
            String computedRaw = sha256Hex((rawBody == null ? "" : rawBody) + eventsSecret);
            if (constantTimeEquals(computedRaw, eventChecksumHeader.trim())) {
                return;
            }
            if (constantTimeEquals(sha256Hex(rawBody == null ? "" : rawBody), eventChecksumHeader.trim())) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de webhook Wompi inválida");
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook Wompi sin firma");
    }

    private String computeSignatureChecksum(JsonNode root) {
        try {
            JsonNode sig = root.path("signature");
            JsonNode properties = sig.path("properties");
            JsonNode data = root.path("data");
            if (!properties.isArray()) return null;
            StringBuilder sb = new StringBuilder();
            for (JsonNode prop : properties) {
                String path = prop.asText();
                String value = extractPathValue(data, path);
                sb.append(value == null ? "" : value);
            }
            JsonNode ts = root.path("timestamp");
            if (ts.isMissingNode() || ts.isNull()) ts = sig.path("timestamp");
            if (!ts.isMissingNode() && !ts.isNull()) sb.append(ts.asText(""));
            sb.append(eventsSecret);
            return sha256Hex(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractPathValue(JsonNode data, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = data;
        for (String p : parts) {
            if (current == null || current.isMissingNode()) return null;
            current = current.path(p);
        }
        if (current == null || current.isMissingNode() || current.isNull()) return null;
        if (current.isValueNode()) return current.asText();
        try { return objectMapper.writeValueAsString(current); } catch (Exception e) { return current.toString(); }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular SHA-256", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.toLowerCase().getBytes(StandardCharsets.UTF_8), b.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
