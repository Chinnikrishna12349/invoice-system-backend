package com.invoiceapp.dto;

import com.invoiceapp.entity.ServiceItem;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Transient;
import java.util.List;

public class InvoiceDTO {
    private String id;
    @NotBlank(message = "Invoice number is required")
    private String invoiceNumber;
    @NotBlank(message = "Date is required")
    private String date;
    private String dueDate; // Added due date
    @NotBlank(message = "Employee name is required")
    private String employeeName;
    @NotBlank(message = "Employee email is required")
    @Email(message = "Email should be valid")
    private String employeeEmail;
    @NotBlank(message = "Employee address is required")
    private String employeeAddress;
    @NotBlank(message = "Employee mobile is required")
    private String employeeMobile;
    @NotNull(message = "Services list is required")
    private List<ServiceItem> services;
    @NotNull(message = "Tax rate is required")
    private Double taxRate;
    private Double cgstRate;
    private Double sgstRate;
    private String createdAt;
    private String updatedAt;
    private String country;
    private String userId; // For data isolation
    private com.invoiceapp.dto.CompanyInfoDTO companyInfo; // Snapshot
    private Boolean showConsumptionTax;

    @Transient
    private byte[] pdfContent;

    public InvoiceDTO() {
    }

    public InvoiceDTO(String id, String invoiceNumber, String date, String employeeName,
            String employeeEmail, String employeeAddress, String employeeMobile,
            List<ServiceItem> services, Double taxRate, String createdAt, String updatedAt) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.date = date;
        this.employeeName = employeeName;
        this.employeeEmail = employeeEmail;
        this.employeeAddress = employeeAddress;
        this.employeeMobile = employeeMobile;
        this.services = services;
        this.taxRate = taxRate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public void setEmployeeEmail(String employeeEmail) {
        this.employeeEmail = employeeEmail;
    }

    public String getEmployeeAddress() {
        return employeeAddress;
    }

    public void setEmployeeAddress(String employeeAddress) {
        this.employeeAddress = employeeAddress;
    }

    public String getEmployeeMobile() {
        return employeeMobile;
    }

    public void setEmployeeMobile(String employeeMobile) {
        this.employeeMobile = employeeMobile;
    }

    public List<ServiceItem> getServices() {
        return services;
    }

    public void setServices(List<ServiceItem> services) {
        this.services = services;
    }

    public Double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(Double taxRate) {
        this.taxRate = taxRate;
    }

    public Double getCgstRate() {
        return cgstRate;
    }

    public void setCgstRate(Double cgstRate) {
        this.cgstRate = cgstRate;
    }

    public Double getSgstRate() {
        return sgstRate;
    }

    public void setSgstRate(Double sgstRate) {
        this.sgstRate = sgstRate;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Double getSubTotal() {
        return services.stream().mapToDouble(ServiceItem::getTotal).sum();
    }

    public Double getTaxAmount() {
        if ("india".equalsIgnoreCase(country)) {
            double cgst = (cgstRate != null ? cgstRate : 0.0);
            double sgst = (sgstRate != null ? sgstRate : 0.0);
            return getSubTotal() * ((cgst + sgst) / 100.0);
        }
        // For Japan, only calculate tax if showConsumptionTax is true
        if ("japan".equalsIgnoreCase(country) && (showConsumptionTax == null || !showConsumptionTax)) {
            return 0.0;
        }
        return getSubTotal() * (taxRate / 100.0);
    }

    public Double getGrandTotal() {
        return getSubTotal() + getTaxAmount();
    }

    public byte[] getPdfContent() {
        return pdfContent;
    }

    public void setPdfContent(byte[] pdfContent) {
        this.pdfContent = pdfContent;
    }

    public String getCompanyName() {
        if (companyInfo != null && companyInfo.getCompanyName() != null) {
            return companyInfo.getCompanyName();
        }
        return "Your Company Name";
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public com.invoiceapp.dto.CompanyInfoDTO getCompanyInfo() {
        return companyInfo;
    }

    public void setCompanyInfo(com.invoiceapp.dto.CompanyInfoDTO companyInfo) {
        this.companyInfo = companyInfo;
    }

    public Boolean getShowConsumptionTax() {
        return showConsumptionTax;
    }

    public void setShowConsumptionTax(Boolean showConsumptionTax) {
        this.showConsumptionTax = showConsumptionTax;
    }
}
