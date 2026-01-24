package com.invoiceapp.service;

import com.invoiceapp.dto.InvoiceDTO;


public interface EmailService {
    void sendInvoiceEmail(InvoiceDTO invoice);
    void sendInvoiceEmailWithPdf(InvoiceDTO invoice, byte[] pdfBytes);
}

