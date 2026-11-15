package com.justjava.ams.invoicing.delivery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.dto.InvoiceLineItemDTO;
import com.justjava.ams.accountant.dto.InvoiceTemplateDTO;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import com.justjava.ams.accountant.service.InvoiceTemplateService;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.UserRepository;
import com.justjava.ams.invoicing.delivery.dto.InvoiceDispatchDTO;
import com.justjava.ams.invoicing.delivery.entity.InvoiceDispatch;
import com.justjava.ams.invoicing.delivery.repository.InvoiceDispatchRepository;
import com.justjava.ams.invoicing.document.dto.InvoiceDocumentDTO;
import com.justjava.ams.invoicing.document.service.InvoiceDocumentService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ResponseStatusException.class)
public class InvoiceDeliveryService {

    private static final String DEFAULT_FONT = "Arial,Helvetica,sans-serif";
    private static final Pattern SAFE_COLOR = Pattern.compile(
            "^(#[0-9a-fA-F]{3,8}|transparent|rgba?\\([0-9\\s.,%]+\\)|[a-zA-Z]{3,20})$");
    private static final Pattern SAFE_FONT = Pattern.compile("^[A-Za-z0-9 ,\\-]{1,120}$");

    private final CustomerInvoiceService customerInvoiceService;
    private final InvoiceDocumentService invoiceDocumentService;
    private final InvoiceTemplateService invoiceTemplateService;
    private final InvoiceDispatchRepository invoiceDispatchRepository;
    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public InvoiceDispatchDTO sendCustomerInvoice(Long invoiceId, String requestedBy) {
        return sendCustomerInvoice(invoiceId, requestedBy, null);
    }

    public InvoiceDispatchDTO sendCustomerInvoice(Long invoiceId, String requestedBy, String senderEmail) {
        debug("Starting invoice send flow for invoiceId=" + invoiceId + ", requestedBy=" + requestedBy
                + ", senderEmail=" + senderEmail);
        CustomerInvoiceDTO invoice = customerInvoiceService.getInvoice(invoiceId);
        InvoiceDispatch dispatch = invoiceDispatchRepository.save(InvoiceDispatch.builder()
                .invoiceId(invoice.getId())
                .channel(InvoiceDispatch.Channel.EMAIL)
                .target(trimToNull(invoice.getCustomerEmail()))
                .status(InvoiceDispatch.Status.PENDING)
                .attemptCount(0)
                .idempotencyKey(UUID.randomUUID().toString())
                .createdBy(userName(requestedBy))
                .build());
        debug("Created pending dispatch id=" + dispatch.getId() + " target=" + dispatch.getTarget());
        return attemptEmailDispatch(dispatch, requestedBy, senderEmail);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDispatchDTO> getCustomerInvoiceDispatches(Long invoiceId) {
        customerInvoiceService.getInvoice(invoiceId);
        return invoiceDispatchRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public InvoiceDispatchDTO retryCustomerInvoiceDispatch(Long invoiceId, Long dispatchId, String requestedBy) {
        customerInvoiceService.getInvoice(invoiceId);
        InvoiceDispatch dispatch = invoiceDispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice dispatch not found"));
        if (!invoiceId.equals(dispatch.getInvoiceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dispatch does not belong to invoice");
        }
        if (InvoiceDispatch.Status.SUCCESS.equals(dispatch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Successful dispatches cannot be retried");
        }
        dispatch.setStatus(InvoiceDispatch.Status.RETRYING);
        dispatch.setNextRetryAt(null);
        return attemptEmailDispatch(invoiceDispatchRepository.save(dispatch), requestedBy, null);
    }

    // ------------------------------------------------------------------
    // Dispatch flow
    // ------------------------------------------------------------------

    private InvoiceDispatchDTO attemptEmailDispatch(InvoiceDispatch dispatch, String requestedBy, String senderEmail) {
        debug("Attempting invoice email send for dispatchId=" + dispatch.getId() + ", invoiceId=" + dispatch.getInvoiceId());
        dispatch.setAttemptCount(dispatch.getAttemptCount() != null ? dispatch.getAttemptCount() + 1 : 1);
        dispatch.setLastAttemptAt(LocalDateTime.now());
        dispatch.setResponseCode(null);
        dispatch.setResponseBody(null);
        dispatch.setErrorMessage(null);

        try {
            debug("Stage 1: validating customer email target");
            requireEmailTarget(dispatch);
            debug("Stage 1 passed: target=" + dispatch.getTarget());

            debug("Stage 2: generating invoice document");
            InvoiceDocumentDTO document = invoiceDocumentService.generateCustomerInvoiceDocument(
                    dispatch.getInvoiceId(),
                    "pdf",
                    userName(requestedBy));
            dispatch.setDocumentId(document.getId());
            debug("Stage 2 passed: documentId=" + document.getId() + ", fileName=" + document.getFileName());

            debug("Stage 3: fetching generated invoice content");
            InvoiceDocumentService.DownloadedInvoiceDocument invoiceDocument =
                    invoiceDocumentService.getCustomerInvoiceDocumentContent(document.getId());
            debug("Stage 3 passed: contentLength="
                    + (invoiceDocument != null && invoiceDocument.content() != null ? invoiceDocument.content().length : 0));

            debug("Stage 4: resolving sender identity");
            SenderIdentity sender = resolveSenderIdentity(senderEmail, requestedBy);
            debug("Stage 4 passed: fromEmail=" + sender.email() + ", fromName=" + sender.displayName());

            CustomerInvoiceDTO invoice = customerInvoiceService.getInvoice(dispatch.getInvoiceId());
            String customerName = resolveCustomerDisplayName(invoice);
            String html = renderInvoiceEmail(invoice, sender, customerName);

            debug("Stage 5: sending email to customer=" + dispatch.getTarget() + ", customerName=" + customerName);
            sendMimeEmail(dispatch.getTarget(), sender.email(), sender.displayName(), invoice,
                    invoiceDocument, html);
            debug("Stage 5 passed: SMTP send call returned without exception");

            dispatch.setStatus(InvoiceDispatch.Status.SUCCESS);
            dispatch.setResponseCode(200);
            dispatch.setResponseBody("Invoice PDF sent by email successfully.");
            dispatch.setNextRetryAt(null);
            debug("Invoice email completed successfully for dispatchId=" + dispatch.getId());
            return toDTO(invoiceDispatchRepository.save(dispatch));
        } catch (ResponseStatusException ex) {
            debug("Invoice email failed at a validation/HTTP stage: " + ex.getMessage());
            markFailed(dispatch, ex.getReason() != null ? ex.getReason() : ex.getMessage());
            invoiceDispatchRepository.save(dispatch);
            throw ex;
        } catch (MailException | MessagingException ex) {
            debug("Invoice email failed during SMTP send: " + ex.getMessage());
            markFailed(dispatch, ex.getMessage());
            invoiceDispatchRepository.save(dispatch);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invoice email could not be sent: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            debug("Invoice email failed with runtime exception: " + ex.getMessage());
            markFailed(dispatch, ex.getMessage());
            invoiceDispatchRepository.save(dispatch);
            throw ex;
        }
    }

    private void requireEmailTarget(InvoiceDispatch dispatch) {
        if (dispatch.getTarget() == null || dispatch.getTarget().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer email is required for invoice delivery");
        }
    }

    private void sendMimeEmail(String toEmail, String fromEmail, String fromName, CustomerInvoiceDTO invoice,
                               InvoiceDocumentService.DownloadedInvoiceDocument invoiceDocument, String htmlBody)
            throws MessagingException {
        if (javaMailSender == null) {
            debug("SMTP mail sender is not configured");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SMTP mail sender is not configured");
        }

        debug("SMTP send: creating MimeMessage for recipient=" + toEmail + ", sender=" + fromEmail);
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
        InternetAddress from;
        try {
            from = new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            debug("SMTP send: failed to encode sender address: " + ex.getMessage());
            throw new MessagingException("Unable to encode sender name for invoice email", ex);
        }
        String subjectRef = trimToNull(invoice.getInvoiceNumber()) != null
                ? invoice.getInvoiceNumber().trim()
                : invoiceDocument.fileName();

        helper.setFrom(from);
        helper.setTo(new InternetAddress(toEmail));
        helper.setSubject("Invoice " + subjectRef);
        helper.setText(buildPlainText(invoice, fromName), htmlBody);
        helper.addAttachment(invoiceDocument.fileName(), new ByteArrayResource(invoiceDocument.content()));
        debug("SMTP send: final send call invoked for file=" + invoiceDocument.fileName());
        javaMailSender.send(mimeMessage);
        debug("SMTP send: mail sent successfully");
    }

    // ------------------------------------------------------------------
    // Email rendering
    // ------------------------------------------------------------------

    /**
     * Builds the invoice email from the template the organisation saved (theme + blocks).
     * Falls back to a sensible default layout when no template exists.
     *
     * The saved blocks use percentage x/y/w/h positions meant for a canvas editor. Email
     * clients ignore absolute positioning, so blocks are grouped into rows by their y value,
     * ordered by x, and given column widths from w. On narrow screens the columns stack.
     */
    private String renderInvoiceEmail(CustomerInvoiceDTO invoice, SenderIdentity sender, String customerName) {
        InvoiceTemplateDTO template = null;
        if (invoice.getOrganizationId() != null) {
            try {
                template = invoiceTemplateService.getTemplate(invoice.getOrganizationId(), "default");
            } catch (Exception ex) {
                debug("Template lookup failed for orgId=" + invoice.getOrganizationId() + ": " + ex.getMessage());
            }
        }

        Map<String, Object> theme = parseJsonMap(template != null ? template.getTheme() : null, new LinkedHashMap<>());
        List<Map<String, Object>> blocks = parseJsonList(template != null ? template.getBlocks() : null, List.of());

        if (blocks == null || blocks.isEmpty()) {
            debug("No saved invoice blocks found; using default invoice layout");
            blocks = defaultBlocks();
        }

        String pageBg = color(theme.get("background"), "#f4f6f8");
        String textColor = color(theme.get("text"), "#111827");
        String font = font(theme.get("fontFamily"));

        List<Map<String, Object>> sorted = new ArrayList<>();
        for (Map<String, Object> b : blocks) {
            if (b != null) sorted.add(b);
        }
        sorted.sort(Comparator.<Map<String, Object>>comparingDouble(b -> num(b.get("y"), 0))
                .thenComparingDouble(b -> num(b.get("x"), 0)));

        List<List<Map<String, Object>>> rows = new ArrayList<>();
        double rowY = 0;
        double rowWidthSum = 0;
        for (Map<String, Object> b : sorted) {
            double y = num(b.get("y"), 0);
            double w = num(b.get("w"), 100);
            if (rows.isEmpty() || Math.abs(y - rowY) > 4 || rowWidthSum + w > 105) {
                rows.add(new ArrayList<>());
                rowY = y;
                rowWidthSum = 0;
            }
            rows.get(rows.size() - 1).add(b);
            rowWidthSum += w;
        }

        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html lang='en'><head>")
                .append("<meta charset='UTF-8'/>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'/>")
                .append("<meta name='x-apple-disable-message-reformatting'/>")
                .append("<title>Invoice ").append(escapeHtml(invoice.getInvoiceNumber())).append("</title>")
                .append("<style>")
                .append("body{margin:0;padding:0;width:100%!important;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;}")
                .append("table{border-collapse:collapse;mso-table-lspace:0;mso-table-rspace:0;}")
                .append("img{border:0;max-width:100%;}")
                .append("@media only screen and (max-width:620px){")
                .append(".wrap{padding:0!important;}")
                .append(".sheet{border-radius:0!important;border-left:0!important;border-right:0!important;}")
                .append(".col{display:block!important;width:100%!important;box-sizing:border-box!important;}")
                .append(".sp{display:none!important;width:0!important;}")
                .append(".company{font-size:24px!important;}")
                .append(".items th,.items td{padding:8px 6px!important;font-size:13px!important;}")
                .append("}")
                .append("</style></head>")
                .append("<body style='margin:0;padding:0;background:").append(pageBg)
                .append(";font-family:").append(font).append(";color:").append(textColor).append(";'>")
                .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='width:100%;background:")
                .append(pageBg).append(";'><tr>")
                .append("<td align='center' class='wrap' style='padding:24px 12px;'>")
                .append("<table role='presentation' class='sheet' width='100%' cellpadding='0' cellspacing='0' border='0' ")
                .append("style='width:100%;max-width:800px;background:#ffffff;border:1px solid #e5e7eb;border-radius:16px;border-collapse:separate;overflow:hidden;'>");

        for (List<Map<String, Object>> row : rows) {
            html.append(renderRow(row, invoice, theme, sender, customerName, font, textColor));
        }

        html.append("</table>")
                .append("<div style='max-width:800px;padding:14px 8px;font-size:12px;color:#6b7280;text-align:center;font-family:")
                .append(font).append(";'>The invoice PDF is attached to this email.</div>")
                .append("</td></tr></table></body></html>");
        return html.toString();
    }

    private String renderRow(List<Map<String, Object>> row, CustomerInvoiceDTO invoice, Map<String, Object> theme,
                             SenderIdentity sender, String customerName, String font, String textColor) {
        row.sort(Comparator.comparingDouble(b -> num(b.get("x"), 0)));

        // Work out column widths, adding spacer cells so the saved x offsets are respected.
        List<String> types = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        List<Map<String, Object>> cellBlocks = new ArrayList<>();
        double cursor = 0;
        for (Map<String, Object> b : row) {
            double x = num(b.get("x"), 0);
            double w = Math.max(5, num(b.get("w"), 100));
            if (x - cursor > 2) {
                types.add("spacer");
                widths.add(x - cursor);
                cellBlocks.add(null);
                cursor = x;
            }
            types.add("block");
            widths.add(w);
            cellBlocks.add(b);
            cursor += w;
        }
        double total = widths.stream().mapToDouble(Double::doubleValue).sum();
        double scale = total > 100 ? 100 / total : 1;

        StringBuilder sb = new StringBuilder();
        sb.append("<tr><td style='padding:0;'>")
                .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='width:100%;'><tr>");
        for (int i = 0; i < widths.size(); i++) {
            String pct = pct(widths.get(i) * scale);
            if ("spacer".equals(types.get(i))) {
                sb.append("<td class='sp' width='").append(pct).append("%' style='width:").append(pct).append("%;padding:0;'>&nbsp;</td>");
            } else {
                sb.append(renderBlock(cellBlocks.get(i), pct, invoice, theme, sender, customerName, font, textColor));
            }
        }
        sb.append("</tr></table></td></tr>");
        return sb.toString();
    }

    private String renderBlock(Map<String, Object> block, String pct, CustomerInvoiceDTO invoice,
                               Map<String, Object> theme, SenderIdentity sender, String customerName,
                               String font, String textColor) {
        String type = String.valueOf(block.getOrDefault("type", "text"));
        String text = block.get("text") == null ? "" : String.valueOf(block.get("text"));
        String color = color(block.get("color"), textColor);
        String bg = color(block.get("bg"), "transparent");
        String align = align(block.get("align"));
        double fontSize = clamp(num(block.get("fontSize"), "company".equals(type) ? 28 : 15), 11, 40);
        int fontWeight = (int) clamp(num(block.get("fontWeight"), "company".equals(type) ? 800 : 400), 100, 900);
        double radius = clamp(num(block.get("radius"), 0), 0, 40);

        String style = "padding:14px 20px;box-sizing:border-box;vertical-align:top;line-height:1.55;"
                + "width:" + pct + "%;"
                + "color:" + color + ";"
                + "background:" + bg + ";"
                + "border-radius:" + (int) radius + "px;"
                + "font-family:" + font + ";"
                + "font-size:" + (int) fontSize + "px;"
                + "font-weight:" + fontWeight + ";"
                + "text-align:" + align + ";";

        String open = "<td class='col' width='" + pct + "%' valign='top' style='" + style + "'>";
        String close = "</td>";

        switch (type) {
            case "company": {
                String name = !text.isBlank() ? text
                        : (sender != null && sender.displayName() != null && !sender.displayName().isBlank()
                           ? sender.displayName() : "AMS Invoice");
                return open.replace("class='col'", "class='col company'")
                        + "<span style='letter-spacing:-0.02em;'>" + escapeHtml(name) + "</span>" + close;
            }
            case "meta":
                return open
                        + labelValue("Invoice", invoice.getInvoiceNumber())
                        + labelValue("Issued", safeDate(invoice.getInvoiceDate()))
                        + labelValue("Due", safeDate(invoice.getDueDate()))
                        + close;
            case "customer":
                return open
                        + "<div style='font-size:12px;text-transform:uppercase;letter-spacing:.06em;opacity:.7;'>Bill to</div>"
                        + "<div style='font-weight:700;'>" + escapeHtml(customerName) + "</div>"
                        + optionalLine(invoice.getCustomerEmail())
                        + optionalLine(invoice.getCustomerAddress())
                        + close;
            case "table":
                return open + buildItemsTable(invoice) + close;
            case "totals":
                return open + buildTotals(invoice) + close;
            case "notes": {
                String notes = invoice.getNotes() != null && !invoice.getNotes().isBlank()
                        ? invoice.getNotes() : "Thank you for your business.";
                return open
                        + "<div style='font-size:12px;text-transform:uppercase;letter-spacing:.06em;opacity:.7;'>Notes</div>"
                        + "<div>" + escapeHtml(notes) + "</div>"
                        + close;
            }
            case "divider":
                return "<td class='col' width='" + pct + "%' style='width:" + pct + "%;padding:6px 20px;'>"
                        + "<div style='border-top:1px solid #d1d5db;font-size:0;line-height:0;height:0;'>&nbsp;</div></td>";
            default:
                return open + escapeHtml(text).replace("\n", "<br/>") + close;
        }
    }

    private String buildItemsTable(CustomerInvoiceDTO invoice) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table role='presentation' class='items' width='100%' cellpadding='0' cellspacing='0' border='0' ")
                .append("style='width:100%;border-collapse:collapse;font-size:inherit;'>")
                .append("<thead><tr>")
                .append(th("Item", "left"))
                .append(th("Qty", "right"))
                .append(th("Rate", "right"))
                .append(th("Amount", "right"))
                .append("</tr></thead><tbody>");
        if (invoice.getLineItems() == null || invoice.getLineItems().isEmpty()) {
            sb.append("<tr><td colspan='4' style='padding:10px 12px;opacity:.7;'>No line items</td></tr>");
        } else {
            for (InvoiceLineItemDTO line : invoice.getLineItems()) {
                sb.append("<tr>")
                        .append(td(escapeHtml(line.getDescription()), "left", false))
                        .append(td(escapeHtml(money(line.getQuantity())), "right", false))
                        .append(td(escapeHtml(money(line.getUnitPrice())), "right", false))
                        .append(td(escapeHtml(money(line.getLineTotal())), "right", true))
                        .append("</tr>");
            }
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String buildTotals(CustomerInvoiceDTO invoice) {
        return "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='width:100%;'>"
                + totalRow("Subtotal", money(invoice.getSubtotal()), false)
                + totalRow("Tax", money(invoice.getTaxAmount()), false)
                + totalRow("Total", money(invoice.getTotalAmount()), true)
                + "</table>";
    }

    private String totalRow(String label, String value, boolean strong) {
        String extra = strong
                ? "border-top:1px solid #cbd5e1;padding-top:10px;font-weight:800;font-size:1.1em;"
                : "";
        return "<tr><td style='padding:4px 0;" + extra + "'>" + escapeHtml(label) + "</td>"
                + "<td align='right' style='padding:4px 0;text-align:right;" + extra + "'>" + escapeHtml(value) + "</td></tr>";
    }

    private String th(String label, String align) {
        return "<th align='" + align + "' style='padding:10px 12px;text-align:" + align
                + ";background:#f1f5f9;border-bottom:1px solid #cbd5e1;font-weight:700;'>" + label + "</th>";
    }

    private String td(String content, String align, boolean strong) {
        return "<td align='" + align + "' style='padding:10px 12px;text-align:" + align
                + ";border-bottom:1px solid #e5e7eb;" + (strong ? "font-weight:700;" : "") + "'>" + content + "</td>";
    }

    private String labelValue(String label, String value) {
        return "<div><span style='opacity:.7;'>" + escapeHtml(label) + ":</span> <strong>"
                + escapeHtml(value == null ? "—" : value) + "</strong></div>";
    }

    private String optionalLine(String value) {
        return value == null || value.isBlank() ? "" : "<div>" + escapeHtml(value) + "</div>";
    }

    private List<Map<String, Object>> defaultBlocks() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(new LinkedHashMap<>(Map.of("type", "company", "y", 0, "x", 0, "w", 100, "fontSize", 28, "fontWeight", 800)));
        list.add(new LinkedHashMap<>(Map.of("type", "meta", "y", 14, "x", 0, "w", 50)));
        list.add(new LinkedHashMap<>(Map.of("type", "customer", "y", 14, "x", 50, "w", 50)));
        list.add(new LinkedHashMap<>(Map.of("type", "table", "y", 34, "x", 0, "w", 100)));
        list.add(new LinkedHashMap<>(Map.of("type", "totals", "y", 64, "x", 50, "w", 50)));
        list.add(new LinkedHashMap<>(Map.of("type", "notes", "y", 80, "x", 0, "w", 100)));
        return list;
    }

    private String buildPlainText(CustomerInvoiceDTO invoice, String fromName) {
        String customer = invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank()
                ? invoice.getCustomerName().trim() : "Customer";
        String sender = fromName != null && !fromName.isBlank() ? fromName : "Our team";
        return "Hello " + customer + ",\n\n"
                + "Invoice " + (invoice.getInvoiceNumber() == null ? "" : invoice.getInvoiceNumber()) + " is attached as a PDF.\n"
                + "Total due: " + money(invoice.getTotalAmount()) + "\n"
                + "Due date: " + safeDate(invoice.getDueDate()) + "\n\n"
                + "Thank you for your business.\n"
                + sender;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String money(Object value) {
        return value == null ? "—" : value.toString();
    }

    private String safeDate(Object value) {
        return value == null ? "—" : value.toString();
    }

    private static double num(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim().replace("%", "").replace("px", ""));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Only lets through plain CSS colour values so saved template data cannot inject markup or CSS. */
    private static String color(Object value, String fallback) {
        if (value == null) return fallback;
        String s = String.valueOf(value).trim();
        return SAFE_COLOR.matcher(s).matches() ? s : fallback;
    }

    private static String font(Object value) {
        if (value == null) return DEFAULT_FONT;
        String s = String.valueOf(value).trim();
        return SAFE_FONT.matcher(s).matches() ? s + ",Arial,sans-serif" : DEFAULT_FONT;
    }

    private static String align(Object value) {
        String s = value == null ? "left" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return s.equals("center") || s.equals("right") ? s : "left";
    }

    private Map<String, Object> parseJsonMap(String json, Map<String, Object> fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            debug("Unable to parse template JSON map: " + ex.getMessage());
            return fallback;
        }
    }

    private List<Map<String, Object>> parseJsonList(String json, List<Map<String, Object>> fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            debug("Unable to parse template JSON list: " + ex.getMessage());
            return fallback;
        }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private SenderIdentity resolveSenderIdentity(String senderEmail, String requestedBy) {
        debug("Resolving sender identity. overrideSenderEmail=" + senderEmail + ", requestedBy=" + requestedBy);
        if (senderEmail != null && !senderEmail.trim().isEmpty()) {
            return new SenderIdentity(senderEmail.trim(), defaultSenderName(senderEmail.trim()));
        }
        if (requestedBy != null && !requestedBy.trim().isEmpty()) {
            String identity = requestedBy.trim();
            if (identity.contains("@")) {
                return new SenderIdentity(identity, defaultSenderName(identity));
            }
            User user = userRepository.findByUsername(identity).orElse(null);
            if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                String displayName = user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername();
                return new SenderIdentity(user.getEmail(), displayName);
            }
            if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                String displayName = user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername();
                return new SenderIdentity(user.getUsername(), displayName);
            }
            debug("No matching app user found for requestedBy=" + identity);
        }
        String fallback = System.getenv().getOrDefault("SMTP_FROM", "");
        if (fallback != null && !fallback.trim().isEmpty()) {
            return new SenderIdentity(fallback.trim(), defaultSenderName(fallback.trim()));
        }
        debug("No sender identity available. SMTP_FROM is not configured and no user email was found.");
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No valid sender email configured. Set SMTP_FROM or log in with an email identity.");
    }

    private String defaultSenderName(String email) {
        if (email == null || email.isBlank()) return "AMS Team";
        int at = email.indexOf('@');
        if (at <= 0) return "AMS Team";
        return email.substring(0, at).replace('.', ' ').replace('_', ' ').trim();
    }

    private String resolveCustomerDisplayName(CustomerInvoiceDTO invoice) {
        if (invoice.getCustomerName() != null && !invoice.getCustomerName().trim().isEmpty()) {
            return invoice.getCustomerName().trim();
        }
        return "Customer";
    }

    private record SenderIdentity(String email, String displayName) {}

    private void markFailed(InvoiceDispatch dispatch, String message) {
        debug("Marking invoice dispatch failed: dispatchId=" + dispatch.getId() + ", reason=" + message);
        dispatch.setStatus(InvoiceDispatch.Status.FAILED);
        dispatch.setErrorMessage(message);
        dispatch.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
    }

    private void debug(String message) {
        log.info("[InvoiceDelivery] {}", message);
    }

    private InvoiceDispatchDTO toDTO(InvoiceDispatch dispatch) {
        return InvoiceDispatchDTO.builder()
                .id(dispatch.getId())
                .invoiceId(dispatch.getInvoiceId())
                .channel(dispatch.getChannel().name())
                .target(dispatch.getTarget())
                .status(dispatch.getStatus().name())
                .attemptCount(dispatch.getAttemptCount())
                .lastAttemptAt(dispatch.getLastAttemptAt())
                .nextRetryAt(dispatch.getNextRetryAt())
                .responseCode(dispatch.getResponseCode())
                .responseBody(dispatch.getResponseBody())
                .errorMessage(dispatch.getErrorMessage())
                .idempotencyKey(dispatch.getIdempotencyKey())
                .documentId(dispatch.getDocumentId())
                .createdBy(dispatch.getCreatedBy())
                .createdAt(dispatch.getCreatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String userName(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : "system";
    }
}