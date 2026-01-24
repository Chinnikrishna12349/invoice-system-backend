package com.invoiceapp.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "invoices")
public class Invoice {
    @Id
    private String id;

    private String invoiceNumber;
    private String date;
    private String dueDate; // Added due date
    private String employeeName;
    private String employeeId;
    private String employeeEmail;
    private String employeeAddress;
    private String employeeMobile;
    private List<ServiceItem> services;
    private Double taxRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String country; // india or japan
    private String userId; // Explicit user ID for isolation
    private com.invoiceapp.entity.CompanyInfo companyInfo; // Snapshot

    public Invoice() {
    }

    public Invoice(String id, String invoiceNumber, String date, String employeeName, String employeeId,
            String employeeEmail, String employeeAddress, String employeeMobile,
            List<ServiceItem> services, Double taxRate, LocalDateTime createdAt,
            LocalDateTime updatedAt, String createdBy, String country) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.date = date;
        this.employeeName = employeeName;
        this.employeeId = employeeId;
        this.employeeEmail = employeeEmail;
        this.employeeAddress = employeeAddress;
        this.employeeMobile = employeeMobile;
        this.services = services;
        this.taxRate = taxRate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.country = country;
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

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public com.invoiceapp.entity.CompanyInfo getCompanyInfo() {
        return companyInfo;
    }

    public void setCompanyInfo(com.invoiceapp.entity.CompanyInfo companyInfo) {
        this.companyInfo = companyInfo;
    }

    public String getBillToName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBillToName'");
    }

    public class InvoiceItem {
    }

    public Object getSubtotal() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSubtotal'");
    }
}
