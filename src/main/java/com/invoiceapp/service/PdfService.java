package com.invoiceapp.service;

import com.invoiceapp.dto.InvoiceDTO;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.colors.ColorConstants;
import org.springframework.stereotype.Service;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Service
public class PdfService {

        private static final float PAGE_HEIGHT = 841.89f;

        public byte[] generateInvoicePdf(InvoiceDTO invoice) throws IOException {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                // ✅ ENABLE MAX COMPRESSION & OPTIMIZATIONS
                WriterProperties props = new WriterProperties()
                                .setCompressionLevel(CompressionConstants.BEST_COMPRESSION) // Use maximum compression
                                .useSmartMode()
                                .setFullCompressionMode(true)
                                .addXmpMetadata() // Add XMP metadata for better compression
                                .setPdfVersion(PdfVersion.PDF_2_0); // Use latest PDF version for better compression

                PdfWriter pdfWriter = new PdfWriter(baos, props);
                PdfDocument pdfDoc = new PdfDocument(pdfWriter);
                Document document = new Document(pdfDoc);

                try {
                        // Document compression is handled by WriterProperties

                        // Load Fonts with Unicode Support (Essential for Rupee Symbol)
                        PdfFont boldFont;
                        PdfFont regularFont;
                        try {
                                boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                                regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                        } catch (Exception e) {
                                // Fallback
                                boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                                regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                        }

                        String formattedDate = formatDate(invoice.getDate());
                        float yPos = convertMmToPoints(18);

                        /*
                         * ======================================================
                         * ✅ STATIC STAMP/LOGO ABOVE SIGNATURE
                         * ======================================================
                         */
                        // Removed header logo to match requested professional layout if needed,
                        // but keeping it for now if it was intended.
                        // The user specifically asked for the logo ABOVE SIGNATURE.

                        com.invoiceapp.dto.CompanyInfoDTO company = invoice.getCompanyInfo();
                        String cName = (company != null && company.getCompanyName() != null) ? company.getCompanyName()
                                        : "Your Company";
                        String cAddress = (company != null && company.getCompanyAddress() != null)
                                        ? company.getCompanyAddress()
                                        : "";

                        // ✅ TOP LOGO (Optional)
                        if (company != null && company.getCompanyLogoUrl() != null) {
                                try {
                                        // Attempt to load the company logo
                                        String logoPath = company.getCompanyLogoUrl();
                                        // If it's a URL, use it directly; if a relative path, we'd need to resolve it.
                                        // For now, assume it's accessible or handled gracefully.
                                        if (logoPath != null && !logoPath.isEmpty()) {
                                                String localPath = logoPath;
                                                if (localPath.startsWith("/uploads/")) {
                                                        localPath = localPath.substring("/uploads/".length());
                                                }
                                                File localFile = new File("uploads/" + localPath);

                                                ImageData logoData;
                                                if (localFile.exists()) {
                                                        logoData = ImageDataFactory.create(localFile.getAbsolutePath());
                                                } else {
                                                        // Fallback to URL resolution
                                                        if (logoPath.contains("localhost:8080")
                                                                        || !logoPath.startsWith("http")) {
                                                                logoPath = logoPath.replace("http://localhost:8080",
                                                                                "https://invoice-system-backend-owhd.onrender.com");
                                                                if (!logoPath.startsWith("http")) {
                                                                        logoPath = "https://invoice-system-backend-owhd.onrender.com"
                                                                                        + (logoPath.startsWith("/") ? ""
                                                                                                        : "/")
                                                                                        + logoPath;
                                                                }
                                                        }
                                                        logoData = ImageDataFactory.create(logoPath);
                                                }

                                                Image logoImg = new Image(logoData).setHeight(convertMmToPoints(15));
                                                document.add(logoImg.setFixedPosition(convertMmToPoints(14),
                                                                PAGE_HEIGHT - convertMmToPoints(25),
                                                                convertMmToPoints(40)));
                                        }
                                } catch (Exception e) {
                                        System.err.println("Could not load company logo: " + e.getMessage());
                                }
                        }

                        Paragraph companyHeader = new Paragraph()
                                        .add(new Text(cName + "\n").setFont(boldFont).setFontSize(11))
                                        .add(new Text(cAddress).setFont(regularFont).setFontSize(9))
                                        .setFixedPosition(convertMmToPoints(14),
                                                        PAGE_HEIGHT - convertMmToPoints(25),
                                                        convertMmToPoints(100));
                        document.add(companyHeader);

                        float rightX = convertMmToPoints(160);
                        yPos = convertMmToPoints(18);

                        document.add(new Paragraph("Invoice #: " + invoice.getInvoiceNumber())
                                        .setFont(boldFont)
                                        .setFontSize(11)
                                        .setFixedPosition(rightX, PAGE_HEIGHT - yPos, convertMmToPoints(50))
                                        .setTextAlignment(TextAlignment.RIGHT));

                        yPos += convertMmToPoints(7);

                        document.add(new Paragraph("Date: " + formattedDate)
                                        .setFont(boldFont)
                                        .setFontSize(11)
                                        .setFixedPosition(rightX, PAGE_HEIGHT - yPos, convertMmToPoints(50))
                                        .setTextAlignment(TextAlignment.RIGHT));

                        // ==================== Bill To Section ====================
                        float startY = PAGE_HEIGHT - yPos - convertMmToPoints(10);

                        // Employee/Client Info
                        Paragraph toPara = new Paragraph()
                                        .add(new Text("Bill To:\n").setFont(boldFont).setFontSize(10))
                                        .add(new Text(getValue(invoice.getEmployeeName()) + "\n").setFont(regularFont)
                                                        .setFontSize(10))
                                        .add(new Text(getValue(invoice.getEmployeeAddress()) + "\n")
                                                        .setFont(regularFont).setFontSize(10))
                                        .add(new Text("Email: " + getValue(invoice.getEmployeeEmail()) + "\n")
                                                        .setFont(regularFont)
                                                        .setFontSize(10))
                                        .add(new Text("Phone: " + getValue(invoice.getEmployeeMobile()))
                                                        .setFont(regularFont)
                                                        .setFontSize(10));

                        document.add(toPara.setFixedPosition(convertMmToPoints(14), startY - convertMmToPoints(25),
                                        convertMmToPoints(80)));

                        // Due Date (aligned with Bill To)
                        // Calculate Due Date (e.g. +30 days or custom) - For now just show placeholder
                        // or omitted if not in DTO

                        yPos += convertMmToPoints(45);

                        // ==================== Services Table ====================
                        Table table = new Table(UnitValue.createPercentArray(new float[] { 40, 15, 20, 25 }))
                                        .setWidth(UnitValue.createPercentValue(100))
                                        .setMarginTop(convertMmToPoints(10));

                        // Header
                        String[] headers = { "Description", "Hours", "Rate", "Amount" };
                        for (String header : headers) {
                                table.addCell(new Cell()
                                                .add(new Paragraph(header).setFont(boldFont)
                                                                .setFontColor(ColorConstants.WHITE))
                                                .setBackgroundColor(new DeviceRgb(6, 81, 237)) // Brand Blue
                                                .setTextAlignment(TextAlignment.CENTER)
                                                .setPadding(6)
                                                .setFontSize(10));
                        }

                        double subtotal = 0;
                        // Rows
                        if (invoice.getServices() != null) {
                                for (com.invoiceapp.entity.ServiceItem item : invoice.getServices()) {
                                        table.addCell(new Cell()
                                                        .add(new Paragraph(item.getDescription()))
                                                        .setTextAlignment(TextAlignment.LEFT)
                                                        .setPadding(6)
                                                        .setFontSize(10));

                                        table.addCell(new Cell()
                                                        .add(new Paragraph(String.valueOf(item.getHours())))
                                                        .setTextAlignment(TextAlignment.CENTER)
                                                        .setPadding(6)
                                                        .setFontSize(10));

                                        table.addCell(new Cell()
                                                        .add(new Paragraph(formatCurrency(item.getRate(),
                                                                        invoice.getCountry())))
                                                        .setTextAlignment(TextAlignment.RIGHT)
                                                        .setPadding(6)
                                                        .setFontSize(10));

                                        double amount = item.getTotal();
                                        subtotal += amount;
                                        table.addCell(new Cell()
                                                        .add(new Paragraph(
                                                                        formatCurrency(amount, invoice.getCountry())))
                                                        .setTextAlignment(TextAlignment.RIGHT)
                                                        .setPadding(6)
                                                        .setFontSize(10));
                                }
                        }
                        document.add(table);

                        // ==================== Totals Section ====================
                        Table totalTable = new Table(UnitValue.createPercentArray(new float[] { 70, 30 }))
                                        .setWidth(UnitValue.createPercentValue(50))
                                        .setHorizontalAlignment(HorizontalAlignment.RIGHT)
                                        .setMarginTop(20);

                        double taxRate = invoice.getTaxRate() != null ? invoice.getTaxRate() : 0.0;
                        double tax = subtotal * (taxRate / 100.0);
                        double grandTotal = subtotal + tax;

                        totalTable.addCell(createTotalCell("SubTotal", regularFont, 11));
                        totalTable.addCell(createTotalCell(formatCurrency(subtotal, invoice.getCountry()), regularFont,
                                        11));

                        String taxLabel = "japan".equalsIgnoreCase(invoice.getCountry())
                                        ? String.format("Consumption Tax (%.0f%%)", taxRate)
                                        : String.format("Tax (%.0f%%)", taxRate);

                        totalTable.addCell(createTotalCell(taxLabel, regularFont, 11));
                        totalTable.addCell(createTotalCell(formatCurrency(tax, invoice.getCountry()), regularFont, 11));

                        totalTable.addCell(createTotalCell("Grand Total", boldFont, 12)
                                        .setBackgroundColor(new DeviceRgb(245, 245, 245))
                                        .setPadding(8));
                        totalTable.addCell(
                                        createTotalCell(formatCurrency(grandTotal, invoice.getCountry()), boldFont, 12)
                                                        .setBackgroundColor(new DeviceRgb(245, 245, 245))
                                                        .setPadding(8));

                        document.add(totalTable);

                        // ==================== Footer: Bank Details & Signature ====================
                        Table footerTable = new Table(UnitValue.createPercentArray(new float[] { 60, 40 }))
                                        .setWidth(UnitValue.createPercentValue(100))
                                        .setMarginTop(50);

                        com.invoiceapp.dto.BankDetailsDTO bank = (company != null) ? company.getBankDetails() : null;
                        String bName = (bank != null && bank.getBankName() != null) ? bank.getBankName() : "";
                        String bAcc = (bank != null && bank.getAccountNumber() != null) ? bank.getAccountNumber() : "";
                        String bIfsc = (bank != null && bank.getIfscCode() != null) ? bank.getIfscCode() : "";
                        String bHolder = (bank != null && bank.getAccountHolderName() != null)
                                        ? bank.getAccountHolderName()
                                        : "";
                        String bBranch = (bank != null && bank.getBranchName() != null) ? bank.getBranchName() : "";

                        Paragraph bankPara = new Paragraph()
                                        .add(new Text("Bank Details:\n").setFont(boldFont).setFontSize(10))
                                        .add(new Text(
                                                        "Bank: " + bName + "\n" +
                                                                        "Acc Name: " + bHolder + "\n" +
                                                                        "Acc No: " + bAcc + "\n" +
                                                                        "IFSC: " + bIfsc
                                                                        + (bBranch.isEmpty() ? ""
                                                                                        : "\nBranch: " + bBranch))
                                                        .setFont(regularFont).setFontSize(10));

                        footerTable.addCell(new Cell().setBorder(Border.NO_BORDER).add(bankPara));

                        // Load and add the static VisionAI stamp
                        try (InputStream stampStream = getClass().getClassLoader()
                                        .getResourceAsStream("visionai-stamp.png")) {
                                if (stampStream != null) {
                                        ImageData stampData = ImageDataFactory.create(stampStream.readAllBytes());
                                        Image stamp = new Image(stampData);
                                        float stampSize = convertMmToPoints(25);

                                        // Position the stamp above the signature in the second cell
                                        Cell signatureCell = new Cell().setBorder(Border.NO_BORDER);

                                        // Add stamp to signature cell
                                        stamp.setWidth(stampSize);
                                        stamp.setHorizontalAlignment(HorizontalAlignment.CENTER);
                                        signatureCell.add(stamp);

                                        // Add Signature text
                                        Paragraph signPara = new Paragraph("Authorised Signature")
                                                        .setFont(boldFont)
                                                        .setFontSize(10)
                                                        .setTextAlignment(TextAlignment.CENTER)
                                                        .setMarginTop(8);

                                        signatureCell.add(signPara);
                                        footerTable.addCell(signatureCell);
                                } else {
                                        Paragraph signPara = new Paragraph()
                                                        .add("\n\n\n")
                                                        .add("______________________\n")
                                                        .add("Authorised Signature")
                                                        .setFont(boldFont)
                                                        .setFontSize(10)
                                                        .setTextAlignment(TextAlignment.CENTER);

                                        footerTable.addCell(new Cell()
                                                        .setBorder(Border.NO_BORDER)
                                                        .setVerticalAlignment(VerticalAlignment.BOTTOM)
                                                        .add(signPara));
                                }
                        }

                        document.add(footerTable);

                        document.close();

                } catch (Exception e) {
                        throw new IOException("Failed to generate PDF", e);
                }

                return baos.toByteArray();
        }

        // Helper to avoid null values
        private String getValue(String value) {
                return value != null ? value : "";
        }

        // Helper for clean total rows
        private Cell createTotalCell(String text, PdfFont font, float fontSize) {
                return new Cell()
                                .add(new Paragraph(text)
                                                .setFont(font)
                                                .setFontSize(fontSize))
                                .setBorder(Border.NO_BORDER)
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setPadding(5);
        }

        // Helper for dynamic currency formatting
        private String formatCurrency(double amount, String country) {
                if ("japan".equalsIgnoreCase(country)) {
                        return String.format(Locale.US, "Yen %,.0f", amount);
                } else {
                        return String.format(Locale.US, "Rs %,.2f", amount);
                }
        }

        private float convertMmToPoints(float mm) {
                return mm * 2.83465f;
        }

        private String formatDate(String dateStr) {
                try {
                        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
                        SimpleDateFormat out = new SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH);
                        Date d = in.parse(dateStr);
                        return out.format(d);
                } catch (Exception e) {
                        return dateStr;
                }
        }
}
