package com.invoiceapp.service;

import com.invoiceapp.dto.InvoiceDTO;
import com.invoiceapp.entity.ServiceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;

@Service
public class BrevoEmailService implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(BrevoEmailService.class);
    private static final String BREVO_API_PATH = "/smtp/email";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long MAX_PDF_SIZE_BYTES = 9 * 1024 * 1024; // 9 MB Brevo limit

    private final WebClient brevoWebClient;
    private final FileStorageService fileStorageService;
    private final PdfService pdfService;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email:KarumanchiPavanKumar2001@gmail.com}")
    private String senderEmail;

    @Value("${brevo.sender.name:Invoice System}")
    private String senderName;

    @Value("${app.base-url:https://invoice-system-backend-owhd.onrender.com}")
    private String baseUrl;

    @Autowired
    public BrevoEmailService(WebClient.Builder webClientBuilder,
            FileStorageService fileStorageService,
            PdfService pdfService) {
        this.fileStorageService = Objects.requireNonNull(fileStorageService, "FileStorageService cannot be null");
        this.pdfService = Objects.requireNonNull(pdfService, "PdfService cannot be null");
        this.brevoWebClient = webClientBuilder
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("accept", "application/json")
                .build();
    }

    @Override
    public void afterPropertiesSet() {
        if (!StringUtils.hasText(brevoApiKey) || "NONE".equals(brevoApiKey)) {
            System.err.println("CRITICAL: Brevo API key is NOT configured or set to 'NONE' in environment.");
        } else {
            String cleanedKey = cleanKey(brevoApiKey);
            System.out.println("BrevoEmailService initialized. Raw length: " + brevoApiKey.length()
                    + ", Cleaned length: " + cleanedKey.length());
            if (cleanedKey.startsWith("xkeysib-")) {
                System.out.println("API Key format looks correct (starts with xkeysib-).");
            } else {
                System.err.println("WARNING: API Key does NOT start with 'xkeysib-'. Current start: "
                        + (cleanedKey.length() > 8 ? cleanedKey.substring(0, 8) : cleanedKey));
            }
        }
        System.out.println("Brevo sender configured as: " + senderName + " <" + senderEmail + ">");
    }

    private String cleanKey(String key) {
        if (key == null)
            return "";
        String cleaned = key.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        } else if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    @Async
    public void sendInvoiceEmail(InvoiceDTO invoice) {
        try {
            System.out.println("Generating PDF on background for invoice #" + invoice.getInvoiceNumber());
            byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);
            sendInvoiceEmailWithPdf(invoice, pdfBytes);
        } catch (Exception e) {
            System.err.println("Error generating PDF in background: " + e.getMessage());
        }
    }

    @Async
    public void sendInvoiceEmail(InvoiceDTO invoice, String recipientEmail) {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null");
        }
        if (!isValidEmail(recipientEmail)) {
            throw new IllegalArgumentException("Invalid recipient email: " + recipientEmail);
        }
        try {
            System.out.println("Preparing to send text-only email for invoice #" + invoice.getInvoiceNumber() + " to "
                    + recipientEmail);

            int attempt = 0;
            while (attempt < MAX_RETRIES) {
                try {
                    attempt++;
                    System.out.println("Sending email attempt " + attempt + " of " + MAX_RETRIES);

                    Map<String, Object> response = sendEmailWithRetry(invoice, recipientEmail);
                    String messageId = response != null ? String.valueOf(response.get("messageId")) : "unknown";
                    System.out.println("Successfully sent email for invoice #" + invoice.getInvoiceNumber()
                            + ". Message ID: " + messageId);
                    return;

                } catch (Exception e) {
                    System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
                    if (attempt == MAX_RETRIES)
                        throw e;
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }
            }
        } catch (Exception e) {
            System.err.println("Unexpected error sending email: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Async
    public void sendInvoiceEmailWithPdf(InvoiceDTO invoice, byte[] pdfBytes) {
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice cannot be null");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF content cannot be null or empty");
        }

        logger.info("Original PDF size: {} KB", pdfBytes.length / 1024);

        // Check if PDF needs compression
        byte[] finalPdfBytes = pdfBytes;
        if (pdfBytes.length > MAX_PDF_SIZE_BYTES) {
            logger.warn("PDF size ({} MB) exceeds maximum allowed size ({} MB). Attempting to compress...",
                    pdfBytes.length / (1024 * 1024.0),
                    MAX_PDF_SIZE_BYTES / (1024 * 1024.0));

            try {
                // First try standard compression
                byte[] compressed = compressPdf(pdfBytes);

                // If still too large, try more aggressive compression
                if (compressed.length > MAX_PDF_SIZE_BYTES) {
                    logger.warn("Standard compression insufficient ({} KB), trying aggressive compression...",
                            compressed.length / 1024);
                    compressed = compressPdfAggressive(pdfBytes);
                }

                // If still too large, send download link
                if (compressed.length > MAX_PDF_SIZE_BYTES) {
                    logger.warn("Compressed PDF still too large ({} KB), sending download link instead",
                            compressed.length / 1024);
                    sendEmailWithDownloadLink(invoice, compressed);
                    return;
                }

                finalPdfBytes = compressed;
                logger.info("Using compressed PDF of size: {} KB", finalPdfBytes.length / 1024);

            } catch (Exception e) {
                logger.error("Error during PDF compression, sending download link", e);
                sendEmailWithDownloadLink(invoice, pdfBytes);
                return;
            }
        }

        String recipientEmail = invoice.getEmployeeEmail();
        if (!isValidEmail(recipientEmail)) {
            throw new IllegalArgumentException("Invalid recipient email: " + recipientEmail);
        }

        String subject = String.format("Invoice #%s - %s",
                invoice.getInvoiceNumber(),
                StringUtils.hasText(invoice.getCompanyName()) ? invoice.getCompanyName() : "Your Invoice");

        String recipientName = StringUtils.hasText(invoice.getEmployeeName()) ? invoice.getEmployeeName()
                : "Valued Customer";

        try {
            // Convert PDF to Base64
            String pdfBase64 = Base64.getEncoder().encodeToString(finalPdfBytes);
            String filename = String.format("Invoice_%s.pdf", invoice.getInvoiceNumber());

            // Build email content using the existing method
            String htmlContent = buildEmailBody(invoice);

            // Create email request
            Map<String, Object> request = new HashMap<>();

            // Sender
            request.put("sender", Map.of(
                    "name", senderName,
                    "email", senderEmail));

            // Recipient
            request.put("to", List.of(Map.of(
                    "email", recipientEmail,
                    "name", recipientName)));

            request.put("subject", subject);
            request.put("htmlContent", htmlContent);

            // Add PDF attachment
            request.put("attachment", List.of(Map.of(
                    "name", filename,
                    "content", pdfBase64)));

            logger.info("Sending email with PDF attachment for invoice #{}", invoice.getInvoiceNumber());

            // Send the email with retry logic
            int attempt = 0;
            while (attempt < MAX_RETRIES) {
                try {
                    attempt++;
                    logger.debug("Sending email attempt {}/{}", attempt, MAX_RETRIES);

                    Map<String, Object> response = brevoWebClient.post()
                            .uri(BREVO_API_PATH)
                            .header("api-key", brevoApiKey != null ? brevoApiKey.trim() : "")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(request)
                            .retrieve()
                            .onStatus(
                                    httpStatus -> httpStatus.isError(),
                                    clientResponse -> clientResponse.bodyToMono(String.class)
                                            .defaultIfEmpty("No error details provided")
                                            .flatMap(errorBody -> {
                                                String statusCode = clientResponse.statusCode().toString();
                                                String errorMessage = String.format(
                                                        "Brevo API request failed with status %s: %s",
                                                        statusCode, errorBody);
                                                logger.error(errorMessage);

                                                if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
                                                    String cleanedKey = cleanKey(brevoApiKey);
                                                    System.err.println("CRITICAL: 401 Unauthorized from Brevo. Error: "
                                                            + errorBody);
                                                    System.err.println("Cleaned key length: " + cleanedKey.length());
                                                    if (cleanedKey.length() > 0) {
                                                        System.err.println("Key prefix: " + (cleanedKey.length() > 10
                                                                ? cleanedKey.substring(0, 10)
                                                                : "short"));
                                                        System.err.println("Key suffix: " + (cleanedKey.length() > 5
                                                                ? cleanedKey.substring(cleanedKey.length() - 5)
                                                                : "short"));
                                                    }
                                                    return Mono.error(new SecurityException(
                                                            "Invalid Brevo API key. Brevo says: " + errorBody));
                                                } else if (clientResponse
                                                        .statusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                                                    return Mono.error(new IllegalStateException(
                                                            "Rate limit exceeded. Please try again later."));
                                                } else {
                                                    return Mono.error(new RuntimeException(errorMessage));
                                                }
                                            }))
                            .bodyToMono(Map.class)
                            .block();

                    if (response != null) {
                        String messageId = response.get("messageId") != null ? response.get("messageId").toString()
                                : "unknown";
                        logger.info("Successfully sent email with PDF attachment for invoice #{}. Message ID: {}",
                                invoice.getInvoiceNumber(), messageId);
                        return;
                    } else {
                        logger.warn("No response received from Brevo API for invoice #{}", invoice.getInvoiceNumber());
                        throw new RuntimeException("No response received from Brevo API");
                    }

                } catch (Exception e) {
                    if (attempt >= MAX_RETRIES) {
                        String errorMsg = String.format("Failed to send invoice #%s after %d attempts: %s",
                                invoice.getInvoiceNumber(), MAX_RETRIES, e.getMessage());
                        logger.error(errorMsg, e);
                        throw new RuntimeException(errorMsg, e);
                    }

                    try {
                        long delay = RETRY_DELAY_MS * attempt;
                        logger.debug("Retrying in {} ms...", delay);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Email sending interrupted", ie);
                    }
                }
            }

        } catch (WebClientResponseException e) {
            String errorResponse = e.getResponseBodyAsString();
            logger.error("Error from Brevo API ({}): {}", e.getStatusCode(), errorResponse, e);
            throw new RuntimeException("Failed to send email with PDF: " + e.getStatusCode() + " - " + errorResponse,
                    e);
        } catch (Exception e) {
            String errorMsg = String.format("Error sending email with PDF for invoice #%s: %s",
                    invoice != null ? invoice.getInvoiceNumber() : "unknown",
                    e.getMessage());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendEmailWithRetry(InvoiceDTO invoice, String recipientEmail) {
        try {
            if (brevoApiKey == null || brevoApiKey.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Brevo API key is not configured. Please check your application properties.");
            }

            if (invoice == null) {
                throw new IllegalArgumentException("Invoice cannot be null");
            }

            if (!isValidEmail(recipientEmail)) {
                throw new IllegalArgumentException("Invalid recipient email: " + recipientEmail);
            }

            // Build email request with recipient
            Map<String, Object> emailRequest = buildEmailRequest(invoice, recipientEmail);

            if (emailRequest == null || emailRequest.isEmpty()) {
                throw new IllegalStateException("Failed to build email request: Empty or null request");
            }

            return Optional.ofNullable(brevoWebClient.post()
                    .uri(BREVO_API_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", brevoApiKey != null ? brevoApiKey.trim() : "")
                    .bodyValue(emailRequest)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            response -> response.bodyToMono(String.class)
                                    .defaultIfEmpty("No error details provided")
                                    .flatMap(errorBody -> {
                                        String statusCode = response.statusCode().toString();
                                        String errorMessage = String.format(
                                                "Brevo API request failed with status %s: %s",
                                                statusCode, errorBody);
                                        logger.error(errorMessage);

                                        if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
                                            return Mono.error(new SecurityException(
                                                    "Invalid Brevo API key. Please check your configuration."));
                                        } else if (response.statusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                                            return Mono.error(new IllegalStateException(
                                                    "Rate limit exceeded. Please try again later."));
                                        } else {
                                            return Mono.error(new RuntimeException(errorMessage));
                                        }
                                    }))
                    .bodyToMono(Map.class)
                    .block())
                    .orElse(Collections.emptyMap());
        } catch (Exception e) {
            logger.error("Error sending email via Brevo API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildEmailRequest(InvoiceDTO invoice, String recipientEmail) {
        Map<String, Object> request = new HashMap<>();

        // Sender
        Map<String, String> sender = new HashMap<>();
        sender.put("email", senderEmail);
        sender.put("name", senderName);
        request.put("sender", sender);

        // Recipient
        Map<String, String> to = new HashMap<>();
        to.put("email", recipientEmail);
        to.put("name", StringUtils.hasText(invoice.getEmployeeName()) ? invoice.getEmployeeName() : "Valued Customer");
        request.put("to", List.of(to));

        // Email content
        request.put("subject", "Your Invoice #" + invoice.getInvoiceNumber());
        request.put("htmlContent", buildEmailBody(invoice));

        return request;
    }

    private String buildEmailBody(InvoiceDTO invoice) {
        try {
            if (invoice == null) {
                throw new IllegalArgumentException("Invoice cannot be null");
            }

            StringBuilder body = new StringBuilder();
            body.append("<html><head><meta charset=\"UTF-8\"></head><body>");
            body.append(
                    "<div style='max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif;'>");
            body.append("<h2 style='color: #2c3e50;'>Invoice Details</h2>");

            String recipientName = StringUtils.hasText(invoice.getEmployeeName()) ? invoice.getEmployeeName()
                    : "Valued Customer";
            body.append("<p>Dear ").append(escapeHtml(recipientName)).append(",</p>");
            body.append("<p>Please find your invoice details below:</p>");

            // Add invoice details
            body.append("<table style='width: 100%; border-collapse: collapse; margin: 15px 0;'>");
            body.append("<tr style='background-color: #f8f9fa;'>");
            body.append("<th style='padding: 12px; border: 1px solid #ddd; text-align: left;'>Description</th>");
            body.append("<th style='padding: 12px; border: 1px solid #ddd; text-align: right;'>Hours</th>");
            body.append("<th style='padding: 12px; border: 1px solid #ddd; text-align: right;'>Rate</th>");
            body.append("<th style='padding: 12px; border: 1px solid #ddd; text-align: right;'>Total</th>");
            body.append("</tr>");

            // Add service items
            if (!CollectionUtils.isEmpty(invoice.getServices())) {
                for (ServiceItem item : invoice.getServices()) {
                    if (item != null) {
                        body.append("<tr>");
                        String description = item.getDescription() != null ? item.getDescription() : "";
                        double hours = item.getHours() != null ? item.getHours() : 0.0;
                        double rate = item.getRate() != null ? item.getRate() : 0.0;
                        double total = item.getTotal() != null ? item.getTotal() : 0.0;

                        body.append("<td style='padding: 10px; border: 1px solid #ddd;'>")
                                .append(escapeHtml(description)).append("</td>");
                        body.append("<td style='padding: 10px; border: 1px solid #ddd; text-align: right;'>")
                                .append(String.format("%.2f", hours)).append("</td>");
                        body.append("<td style='padding: 10px; border: 1px solid #ddd; text-align: right;'>")
                                .append(String.format("%.2f", rate)).append("</td>");
                        body.append("<td style='padding: 10px; border: 1px solid #ddd; text-align: right;'>")
                                .append(String.format("%.2f", total)).append("</td>");
                        body.append("</tr>");
                    }
                }
            } else {
                body.append(
                        "<tr><td colspan='4' style='padding: 10px; text-align: center;'>No services found</td></tr>");
            }

            // Add subtotal, tax, and grand total
            body.append("<tr style='font-weight: bold; background-color: #f8f9fa;'>");
            body.append(
                    "<td colspan='3' style='padding: 12px; border: 1px solid #ddd; text-align: right;'>Subtotal:</td>");
            body.append("<td style='padding: 12px; border: 1px solid #ddd; text-align: right;'>")
                    .append(String.format("%.2f", invoice.getSubTotal())).append("</td>");
            body.append("</tr>");

            if (invoice.getTaxRate() != null && invoice.getTaxRate() > 0) {
                body.append("<tr style='font-weight: bold; background-color: #f8f9fa;'>");
                body.append("<td colspan='3' style='padding: 12px; border: 1px solid #ddd; text-align: right;'>Tax (")
                        .append(String.format("%.2f", invoice.getTaxRate())).append("%):</td>");
                body.append("<td style='padding: 12px; border: 1px solid #ddd; text-align: right;'>")
                        .append(String.format("%.2f", invoice.getTaxAmount())).append("</td>");
                body.append("</tr>");

                body.append("<tr style='font-weight: bold; background-color: #f0f0f0;'>");
                body.append(
                        "<td colspan='3' style='padding: 12px; border: 1px solid #ddd; text-align: right;'>Grand Total:</td>");
                body.append("<td style='padding: 12px; border: 1px solid #ddd; text-align: right;'>")
                        .append(String.format("%.2f", invoice.getGrandTotal())).append("</td>");
                body.append("</tr>");
            }

            // Add payment instructions and footer
            body.append(
                    "<div style='margin-top: 20px; padding: 15px; background-color: #f8f9fa; border-left: 4px solid #3498db;'>");
            body.append("<h3 style='margin-top: 0; color: #2c3e50;'>Payment Instructions</h3>");
            body.append("<p>Please make the payment to the following account details:</p>");
            body.append("<p>Bank: [Your Bank Name]<br>");
            body.append("Account Name: [Your Account Name]<br>");
            body.append("Account Number: [Your Account Number]<br>");
            body.append("Reference: Invoice #").append(invoice.getInvoiceNumber()).append("</p>");
            body.append("</div>");

            // Add footer
            body.append(
                    "<div style='margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 0.9em; color: #7f8c8d;'>");
            body.append("<p>If you have any questions about this invoice, please contact our support team at ");
            body.append("<a href='mailto:support@example.com' style='color: #3498db; text-decoration: none;'>");
            body.append("support@example.com</a> or call us at +1 (123) 456-7890.</p>");
            body.append("<p>Thank you for your business!</p>");
            body.append("</div>");

            body.append("</div>"); // Close the main container
            body.append("</body></html>");

            return body.toString();
        } catch (Exception e) {
            logger.error("Error building email body: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate email content", e);
        }
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Compresses a PDF file to reduce its size using PDFBox
     * 
     * @param pdfBytes The PDF content as byte array
     * @return Compressed PDF as byte array
     */
    private byte[] compressPdf(byte[] pdfBytes) {
        try (PDDocument document = PDDocument.load(pdfBytes);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Remove all security settings that might prevent optimization
            document.setAllSecurityToBeRemoved(true);

            // Optimize PDF by compressing images and removing unnecessary objects
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources != null) {
                    for (COSName xObjectName : resources.getXObjectNames()) {
                        try {
                            if (resources.isImageXObject(xObjectName)) {
                                PDImageXObject image = (PDImageXObject) resources.getXObject(xObjectName);
                                // Apply compression to images
                                image.getCOSObject().setItem(COSName.FILTER, COSName.FLATE_DECODE);
                                image.getCOSObject().setInt(COSName.BITS_PER_COMPONENT, 8);
                            }
                        } catch (Exception e) {
                            logger.warn("Error optimizing image in PDF", e);
                            // Continue with next image
                        }
                    }
                }
            }

            // Save with compression
            document.save(baos);
            byte[] compressed = baos.toByteArray();

            logger.info("PDF compressed from {} KB to {} KB ({}% reduction)",
                    pdfBytes.length / 1024,
                    compressed.length / 1024,
                    (int) (100 - ((float) compressed.length / pdfBytes.length * 100)));

            return compressed;

        } catch (Exception e) {
            logger.error("Error compressing PDF, returning original", e);
            return pdfBytes; // Return original if compression fails
        }
    }

    /**
     * More aggressive PDF compression with reduced image quality
     */
    private byte[] compressPdfAggressive(byte[] pdfBytes) {
        try (PDDocument document = PDDocument.load(pdfBytes);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            document.setAllSecurityToBeRemoved(true);

            // More aggressive settings
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources != null) {
                    for (COSName xObjectName : resources.getXObjectNames()) {
                        try {
                            if (resources.isImageXObject(xObjectName)) {
                                PDImageXObject image = (PDImageXObject) resources.getXObject(xObjectName);
                                // More aggressive compression
                                image.getCOSObject().setItem(COSName.FILTER, COSName.FLATE_DECODE);
                                image.getCOSObject().setInt(COSName.BITS_PER_COMPONENT, 4); // Reduced color depth
                            }
                        } catch (Exception e) {
                            logger.warn("Error optimizing image in aggressive compression", e);
                        }
                    }
                }
            }

            // Save with compression
            document.save(baos);
            byte[] compressed = baos.toByteArray();

            logger.info("Aggressive PDF compression: {} KB to {} KB ({}% reduction)",
                    pdfBytes.length / 1024,
                    compressed.length / 1024,
                    (int) (100 - ((float) compressed.length / pdfBytes.length * 100)));

            return compressed;

        } catch (Exception e) {
            logger.error("Error in aggressive PDF compression, returning original", e);
            return pdfBytes;
        }
    }

    /**
     * Sends an email with a download link to the PDF instead of an attachment
     */
    private void sendEmailWithDownloadLink(InvoiceDTO invoice, byte[] pdfBytes) {
        try {
            // Save the PDF to a temporary location
            String filename = String.format("Invoice_%s_%d.pdf",
                    invoice.getInvoiceNumber(),
                    System.currentTimeMillis());

            // Store the file and get a download link
            String fileUrl = fileStorageService.storeFile(pdfBytes, filename);

            // Build the email content
            String subject = String.format("Invoice #%s - Download Link", invoice.getInvoiceNumber());

            String htmlContent = String.format(
                    "<html><body>" +
                            "<div style='max-width: 600px; margin: 0 auto; padding: 20px; font-family: Arial, sans-serif;'>"
                            +
                            "<h2 style='color: #2c3e50;'>Your Invoice is Ready</h2>" +
                            "<p>Dear %s,</p>" +
                            "<p>Your invoice #%s is ready for download. The file is too large to attach directly to this email.</p>"
                            +
                            "<p><a href='%s' style='display: inline-block; padding: 10px 20px; background-color: #3498db; color: white; text-decoration: none; border-radius: 4px;'>Download Invoice</a></p>"
                            +
                            "<p>This link will be valid for 7 days.</p>" +
                            "<p>If you have any questions, please contact our support team.</p>" +
                            "<p>Best regards,<br>%s</p>" +
                            "</div>" +
                            "</body></html>",
                    escapeHtml(invoice.getEmployeeName()),
                    invoice.getInvoiceNumber(),
                    fileUrl,
                    senderName);

            // Build the email request
            Map<String, Object> emailRequest = new HashMap<>();
            emailRequest.put("sender", Map.of("name", senderName, "email", senderEmail));
            emailRequest.put("to", List.of(Map.of(
                    "email", invoice.getEmployeeEmail(),
                    "name",
                    StringUtils.hasText(invoice.getEmployeeName()) ? invoice.getEmployeeName() : "Valued Customer")));
            emailRequest.put("subject", subject);
            emailRequest.put("htmlContent", htmlContent);

            logger.info("Sent email with download link for invoice #{}", invoice.getInvoiceNumber());

        } catch (Exception e) {
            logger.error("Failed to send email with download link for invoice #{}",
                    invoice != null ? invoice.getInvoiceNumber() : "unknown", e);
            throw new RuntimeException("Failed to send email with download link", e);
        }
    }
}