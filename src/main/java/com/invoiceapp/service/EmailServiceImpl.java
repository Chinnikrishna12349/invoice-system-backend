// src/main/java/com/invoiceapp/service/EmailServiceImpl.java
package com.invoiceapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.invoiceapp.dto.InvoiceDTO;

@Service
public class EmailServiceImpl implements EmailService {
    
    @Autowired
    private BrevoEmailService brevoEmailService;
    
    @Value("${brevo.sender.email:KarumanchiPavanKumar2001@gmail.com}")
    private String fromEmail;

    @Value("${brevo.sender.name:Invoice System}")
    private String fromName;
    
    @Override
    public void sendInvoiceEmail(InvoiceDTO invoice) {
        try {
            System.out.println("=== Email Configuration ===");
            System.out.println("From Email: " + fromEmail);
            System.out.println("To Email: " + invoice.getEmployeeEmail());
            System.out.println("========================");
            
            brevoEmailService.sendInvoiceEmail(invoice, invoice.getEmployeeEmail());
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendInvoiceEmailWithPdf(InvoiceDTO invoice, byte[] pdfBytes) {
        try {
            System.out.println("=== Sending Email with PDF Attachment ===");
            System.out.println("From: " + fromEmail);
            System.out.println("To: " + invoice.getEmployeeEmail());
            System.out.println("PDF Size: " + (pdfBytes != null ? pdfBytes.length : 0) + " bytes");
            System.out.println("===============================");
            
            brevoEmailService.sendInvoiceEmailWithPdf(invoice, pdfBytes);
        } catch (Exception e) {
            System.err.println("Error sending email with PDF: " + e.getMessage());
            throw new RuntimeException("Failed to send email with PDF: " + e.getMessage(), e);
        }
    }
}