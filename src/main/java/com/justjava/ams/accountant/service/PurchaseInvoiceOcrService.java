package com.justjava.ams.accountant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.justjava.ams.accountant.dto.PurchaseInvoiceOcrResult;
import com.justjava.ams.accountant.dto.PurchaseLineItemDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class PurchaseInvoiceOcrService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public PurchaseInvoiceOcrService(
            ObjectMapper objectMapper,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model) {
        this.objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public PurchaseInvoiceOcrResult extractFromImage(byte[] fileBytes, String filename) {
        System.out.println("[PURCHASE-INVOICE-OCR] Starting extraction for file: " + filename);
        if (StringUtils.hasText(apiKey)) {
            try {
                PurchaseInvoiceOcrResult result = extractWithOpenAi(fileBytes, filename);
                System.out.println("[PURCHASE-INVOICE-OCR] Successful OpenAI extraction result: " + result);
                return result;
            } catch (Exception ex) {
                System.err.println("[PURCHASE-INVOICE-OCR] OpenAI extraction failed: " + ex.getMessage());
                ex.printStackTrace(System.err);
                PurchaseInvoiceOcrResult fallback = extractWithFallback(fileBytes, filename, "OpenAI OCR failed: " + ex.getMessage());
                System.out.println("[PURCHASE-INVOICE-OCR] Using fallback extraction: " + fallback);
                return fallback;
            }
        }
        PurchaseInvoiceOcrResult fallback = extractWithFallback(fileBytes, filename, "OpenAI API key not configured. Using local fallback extraction.");
        System.out.println("[PURCHASE-INVOICE-OCR] No API key configured; fallback result: " + fallback);
        return fallback;
    }

    private PurchaseInvoiceOcrResult extractWithOpenAi(byte[] fileBytes, String filename) {
        String dataUrl = "data:" + detectMimeType(filename) + ";base64," + Base64.getEncoder().encodeToString(fileBytes);
        String prompt = "You are extracting data from a supplier invoice image. Use the visual content to recover the most accurate values possible. " +
                "Return a strict JSON object with exactly these fields: purchaseOrderNumber, vendorName, vendorEmail, vendorPhone, vendorAddress, purchaseDate, dueDate, subtotal, totalAmount, notes, rawText, lineItems. " +
                "For lineItems use objects with fields description, quantity, unitPrice, lineTotal, accountName. " +
                "Do not guess extra fields. If a field is not visible, use null. Dates must be ISO yyyy-MM-dd. " +
                "Numbers must be plain numeric JSON values without currency symbols or commas. " +
                "Use careful reasoning and prefer invoice header details for vendor and invoice number. " +
                "The rawText field should include all clearly readable invoice text. " +
                "Return only valid JSON, with no markdown fences and no explanatory text.";

        OpenAiRequest request = new OpenAiRequest(model, List.of(
                new OpenAiMessage("user", List.of(
                        new OpenAiContent("text", prompt, null),
                        new OpenAiContent("image_url", null, new OpenAiImageUrl(dataUrl))
                ))
        ));

        System.out.println("[PURCHASE-INVOICE-OCR] Sending invoice image to OpenAI model: " + model);
        String responseBody = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(String.class);

        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Empty response from OpenAI");
        }

        System.out.println("[PURCHASE-INVOICE-OCR] OpenAI raw response: " + responseBody);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = null;
            if (contentNode.isArray()) {
                for (JsonNode part : contentNode) {
                    if (part.path("type").asText("text").equalsIgnoreCase("text") && part.has("text")) {
                        content = part.get("text").asText();
                        break;
                    }
                }
            } else if (contentNode.isTextual()) {
                content = contentNode.asText();
            }

            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("OpenAI did not return a text extraction payload.");
            }
            System.out.println("[PURCHASE-INVOICE-OCR] Parsed OpenAI content payload: " + content);
            return parseResult(content);
        } catch (Exception ex) {
            throw new IllegalStateException("OpenAI OCR extraction failed: " + ex.getMessage(), ex);
        }
    }

    private PurchaseInvoiceOcrResult parseResult(String content) throws JsonProcessingException {
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        PurchaseInvoiceOcrResult result = objectMapper.readValue(cleaned, PurchaseInvoiceOcrResult.class);
        if (result.getLineItems() == null) {
            result.setLineItems(new ArrayList<>());
        }
        result.setPurchaseDate(parseDate(result.getPurchaseDate()));
        result.setDueDate(parseDate(result.getDueDate()));
        if (result.getSubtotal() != null) result.setSubtotal(normalizeMoney(result.getSubtotal()));
        if (result.getTotalAmount() != null) result.setTotalAmount(normalizeMoney(result.getTotalAmount()));
        for (PurchaseLineItemDTO item : result.getLineItems()) {
            if (item.getQuantity() != null) item.setQuantity(normalizeMoney(item.getQuantity()));
            if (item.getUnitPrice() != null) item.setUnitPrice(normalizeMoney(item.getUnitPrice()));
            if (item.getLineTotal() != null) item.setLineTotal(normalizeMoney(item.getLineTotal()));
        }
        return result;
    }

    private PurchaseInvoiceOcrResult extractWithFallback(byte[] fileBytes, String filename, String note) {
        PurchaseInvoiceOcrResult result = new PurchaseInvoiceOcrResult();
        result.setPurchaseOrderNumber(extractFileReference(filename));
        result.setVendorName("Supplier");
        result.setNotes(note);
        result.setRawText("Invoice uploaded: " + (filename == null ? "invoice" : filename));
        result.setLineItems(new ArrayList<>());
        result.setSubtotal(BigDecimal.ZERO);
        result.setTotalAmount(BigDecimal.ZERO);
        result.setPurchaseDate(LocalDate.now());
        result.setDueDate(LocalDate.now());
        System.out.println("[PURCHASE-INVOICE-OCR] Fallback extraction used. File: " + filename + ". Result: " + result);
        return result;
    }

    private String extractFileReference(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "INV-UNKNOWN";
        }
        String cleaned = filename.replaceFirst("\\.[^.]+$", "");
        return cleaned.length() > 0 ? cleaned.toUpperCase(Locale.ROOT) : "INV-UNKNOWN";
    }

    private LocalDate parseDate(LocalDate value) {
        return value;
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private String detectMimeType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    private record OpenAiRequest(String model, List<OpenAiMessage> messages) {}
    private record OpenAiMessage(String role, List<OpenAiContent> content) {}
    private record OpenAiContent(String type, String text, OpenAiImageUrl image_url) {}
    private record OpenAiImageUrl(String url) {}
}
