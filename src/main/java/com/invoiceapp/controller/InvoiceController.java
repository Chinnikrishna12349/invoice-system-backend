package com.invoiceapp.controller;

import com.invoiceapp.dto.ApiResponse;
import com.invoiceapp.dto.InvoiceDTO;
import com.invoiceapp.service.EmailService;
import com.invoiceapp.service.InvoiceService;
import com.invoiceapp.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", methods = { RequestMethod.GET, RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE, RequestMethod.OPTIONS })
public class InvoiceController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceController.class);

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PdfService pdfService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceDTO>> createInvoice(@Valid @RequestBody InvoiceDTO invoiceDTO) {
        logger.info("Creating new invoice for customer: {}", invoiceDTO.getCustomerName());
        try {
            InvoiceDTO savedInvoice = invoiceService.createInvoice(invoiceDTO);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Invoice created successfully", savedInvoice));
        } catch (Exception e) {
            logger.error("Error creating invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to create invoice", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceDTO>> updateInvoice(
            @PathVariable String id,
            @Valid @RequestBody InvoiceDTO invoiceDTO) {
        logger.info("Updating invoice: {}", id);
        try {
            InvoiceDTO updatedInvoice = invoiceService.updateInvoice(id, invoiceDTO);
            return ResponseEntity.ok(ApiResponse.success("Invoice updated successfully", updatedInvoice));
        } catch (Exception e) {
            logger.error("Error updating invoice {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to update invoice", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvoiceDTO>>> getAllInvoices(@RequestParam(required = false) String userId) {
        logger.info("Fetching all invoices{}", (userId != null ? " for user: " + userId : ""));
        try {
            List<InvoiceDTO> invoices = invoiceService.getAllInvoices(userId);
            return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully", invoices));
        } catch (Exception e) {
            logger.error("Error fetching invoices: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch invoices", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceDTO>> getInvoiceById(@PathVariable String id) {
        logger.info("Fetching invoice: {}", id);
        try {
            InvoiceDTO invoice = invoiceService.getInvoiceById(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoice));
        } catch (Exception e) {
            logger.error("Error fetching invoice {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Invoice not found", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable String id) {
        logger.info("Deleting invoice: {}", id);
        try {
            invoiceService.deleteInvoice(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice deleted successfully", null));
        } catch (Exception e) {
            logger.error("Error deleting invoice {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Failed to delete invoice", e.getMessage()));
        }
    }

    @GetMapping("/next-number")
    public ResponseEntity<ApiResponse<String>> getNextInvoiceNumber(@RequestParam String userId) {
        try {
            String nextNumber = invoiceService.getNextInvoiceNumber(userId);
            return ResponseEntity.ok(ApiResponse.success("Next invoice number fetched", nextNumber));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch next number", e.getMessage()));
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable String id, @RequestParam String invoiceNumber) {
        System.out.println("Downloading PDF for invoice: " + id);
        try {
            InvoiceDTO invoice = invoiceService.getInvoiceById(id);
            byte[] pdfBytes = pdfService.generateInvoicePdf(invoice);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"Invoice_" + invoiceNumber + ".pdf\"")
                    .body(pdfBytes);
        } catch (Exception e) {
            System.out.println("Error downloading PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/{id}/send-email", produces = { "application/json" })
    public ResponseEntity<ApiResponse<Void>> sendInvoiceEmail(@PathVariable String id,
            @RequestBody(required = false) byte[] pdfBytes) {
        logger.info("Sending invoice email: {}", id);
        try {
            InvoiceDTO invoice = invoiceService.getInvoiceById(id);

            // If PDF bytes are provided (from frontend), use them; otherwise generate on
            // backend
            if (pdfBytes != null && pdfBytes.length > 0) {
                logger.info("Using frontend-generated PDF ({} bytes)", pdfBytes.length);
                emailService.sendInvoiceEmailWithPdf(invoice, pdfBytes);
            } else {
                logger.info("Generating PDF on backend");
                emailService.sendInvoiceEmail(invoice);
            }

            return ResponseEntity.ok(ApiResponse.success("Email sent successfully"));
        } catch (Exception e) {
            logger.error("Error sending email for invoice {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to send email", e.getMessage()));
        }
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<InvoiceDTO>>> getInvoicesByEmployee(@PathVariable String employeeId) {
        System.out.println("Fetching invoices for employee: " + employeeId);
        try {
            List<InvoiceDTO> invoices = invoiceService.getInvoicesByEmployeeId(employeeId);
            return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully", invoices));
        } catch (Exception e) {
            System.out.println("Error fetching employee invoices: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch invoices", e.getMessage()));
        }
    }
}
