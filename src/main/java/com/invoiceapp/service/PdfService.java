package com.invoiceapp.service;

import com.invoiceapp.dto.InvoiceDTO;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.HorizontalAlignment;
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
                                // Try to load a Unicode font (common in Linux/Render environments)
                                String[] fontPaths = {
                                                "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                                                "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
                                                "C:/Windows/Fonts/arialbd.ttf" // Local Windows fallback for dev
                                };
                                String fontPath = null;
                                for (String path : fontPaths) {
                                        if (new File(path).exists()) {
                                                fontPath = path;
                                                break;
                                        }
                                }

                                if (fontPath != null) {
                                        boldFont = PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H);
                                        String regularPath = fontPath.replace("-Bold", "").replace("bd", ""); // Simple
                                                                                                              // attempt
                                                                                                              // to find
                                                                                                              // regular
                                        regularFont = PdfFontFactory.createFont(regularPath, PdfEncodings.IDENTITY_H);
                                } else {
                                        // Fallback to standard fonts (Rupee symbol might not show)
                                        boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                                        regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                                }
                        } catch (Exception e) {
                                // Ultimate Fallback
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

                        // ✅ TOP LOGO (Robust Loading)
                        boolean isVisionAI = "Vision AI LLC".equalsIgnoreCase(cName);

                        // ✅ TOP LOGO (Robust Loading) - ONLY for Vision AI
                        if (isVisionAI && company != null && company.getCompanyLogoUrl() != null) {
                                try {
                                        String logoPath = company.getCompanyLogoUrl();
                                        ImageData logoData = null;

                                        // Strategy 1: Check local uploads directory (stripped of URL)
                                        String filename = logoPath;
                                        if (filename.contains("/uploads/")) {
                                                filename = filename.substring(filename.indexOf("/uploads/") + 9);
                                        } else if (filename.contains("/")) {
                                                filename = filename.substring(filename.lastIndexOf("/") + 1);
                                        }

                                        File localFile = new File("uploads/" + filename);
                                        if (localFile.exists()) {
                                                logoData = ImageDataFactory.create(localFile.getAbsolutePath());
                                        }
                                        // Strategy 2: Try absolute path if provided
                                        else if (new File(logoPath).exists()) {
                                                logoData = ImageDataFactory.create(logoPath);
                                        }
                                        // Strategy 3: Try URL
                                        else {
                                                try {
                                                        logoData = ImageDataFactory.create(logoPath);
                                                } catch (Exception ignored) {
                                                        // URL fetch failed
                                                }
                                        }

                                        if (logoData != null) {
                                                Image logoImg = new Image(logoData);
                                                logoImg.setHeight(convertMmToPoints(20));
                                                logoImg.setAutoScale(true); // Maintain aspect ratio

                                                document.add(logoImg.setFixedPosition(convertMmToPoints(14),
                                                                PAGE_HEIGHT - convertMmToPoints(30),
                                                                convertMmToPoints(50)));
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

                        boolean showTax = !"japan".equalsIgnoreCase(invoice.getCountry())
                                        || (invoice.getShowConsumptionTax() != null && invoice.getShowConsumptionTax());
                        double taxRate = showTax ? (invoice.getTaxRate() != null ? invoice.getTaxRate() : 0.0) : 0.0;
                        double tax = subtotal * (taxRate / 100.0);
                        double grandTotal = subtotal + tax;

                        totalTable.addCell(createTotalCell("SubTotal", regularFont, 11));
                        totalTable.addCell(createTotalCell(formatCurrency(subtotal, invoice.getCountry()), regularFont,
                                        11));

                        if (showTax) {
                                String taxLabel = "japan".equalsIgnoreCase(invoice.getCountry())
                                                ? String.format("Consumption Tax (%.0f%%)", taxRate)
                                                : String.format("Tax (%.0f%%)", taxRate);

                                totalTable.addCell(createTotalCell(taxLabel, regularFont, 11));
                                totalTable.addCell(createTotalCell(formatCurrency(tax, invoice.getCountry()),
                                                regularFont, 11));
                        }

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

                        if (isVisionAI && bank != null) {
                                String bName = getValue(bank.getBankName());
                                String bAcc = getValue(bank.getAccountNumber());
                                String bIfsc = getValue(bank.getIfscCode());
                                String bHolder = getValue(bank.getAccountHolderName());
                                String bBranch = getValue(bank.getBranchName());
                                String bBranchCode = getValue(bank.getBranchCode());
                                String bAccType = getValue(bank.getAccountType());

                                Paragraph bankPara = new Paragraph()
                                                .add(new Text("Bank Details:\n").setFont(boldFont).setFontSize(10))
                                                .setMarginBottom(5);

                                if (!bName.isEmpty())
                                        bankPara.add(new Text("Bank Name: " + bName + "\n").setFont(boldFont)
                                                        .setFontSize(11));
                                if (!bBranch.isEmpty())
                                        bankPara.add(new Text("Branch: " + bBranch + "\n").setFont(boldFont)
                                                        .setFontSize(11));
                                if (!bBranchCode.isEmpty())
                                        bankPara.add(new Text("Branch Code: " + bBranchCode + "\n").setFont(boldFont)
                                                        .setFontSize(11));
                                if (!bAccType.isEmpty())
                                        bankPara.add(new Text("Account Type: " + bAccType + "\n").setFont(boldFont)
                                                        .setFontSize(11));
                                if (!bAcc.isEmpty())
                                        bankPara.add(new Text("Account No: " + bAcc + "\n").setFont(boldFont)
                                                        .setFontSize(11));
                                if (!bHolder.isEmpty())
                                        bankPara.add(new Text("Account Holder: " + bHolder + "\n").setFont(boldFont)
                                                        .setFontSize(11));
                                if (!bIfsc.isEmpty())
                                        bankPara.add(new Text("IFSC Code: " + bIfsc).setFont(boldFont).setFontSize(11));

                                footerTable.addCell(new Cell().setBorder(Border.NO_BORDER).add(bankPara));
                        } else {
                                footerTable.addCell(new Cell().setBorder(Border.NO_BORDER));
                        }

                        // Load and add the static VisionAI stamp ONLY for Vision AI
                        Cell signatureCell = new Cell().setBorder(Border.NO_BORDER);
                        if (isVisionAI) {
                                try (InputStream stampStream = getClass().getClassLoader()
                                                .getResourceAsStream("visionai-stamp.png")) {
                                        if (stampStream != null) {
                                                ImageData stampData = ImageDataFactory
                                                                .create(stampStream.readAllBytes());
                                                Image stamp = new Image(stampData);
                                                float stampSize = convertMmToPoints(25);

                                                stamp.setWidth(stampSize);
                                                stamp.setHorizontalAlignment(HorizontalAlignment.CENTER);
                                                signatureCell.add(stamp);
                                        }
                                } catch (Exception e) {
                                        signatureCell.add(new Paragraph("\n\n\n"));
                                }
                        } else {
                                signatureCell.add(new Paragraph("\n\n\n"));
                                signatureCell.add(new Paragraph("______________________\n")
                                                .setTextAlignment(TextAlignment.CENTER));
                        }

                        // Add Signature text
                        Paragraph signPara = new Paragraph("Authorised Signature")
                                        .setFont(boldFont)
                                        .setFontSize(10)
                                        .setTextAlignment(TextAlignment.CENTER)
                                        .setMarginTop(8);

                        signatureCell.add(signPara);
                        footerTable.addCell(signatureCell);

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
                        return String.format(Locale.US, "¥ %,.0f", amount);
                } else {
                        // Default to India/Rupee
                        return String.format(Locale.US, "₹ %,.2f", amount);
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
