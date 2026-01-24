package com.invoiceapp.dto;

public class CompanyInfoDTO {
    private String id;
    private String companyName;
    private String companyAddress;
    private String companyLogoUrl;
    private BankDetailsDTO bankDetails;
    
    public CompanyInfoDTO() {}
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    
    public String getCompanyAddress() { return companyAddress; }
    public void setCompanyAddress(String companyAddress) { this.companyAddress = companyAddress; }
    
    public String getCompanyLogoUrl() { return companyLogoUrl; }
    public void setCompanyLogoUrl(String companyLogoUrl) { this.companyLogoUrl = companyLogoUrl; }
    
    public BankDetailsDTO getBankDetails() { return bankDetails; }
    public void setBankDetails(BankDetailsDTO bankDetails) { this.bankDetails = bankDetails; }
}

