package com.invoiceapp.service;

import com.invoiceapp.dto.InvoiceDTO;
import com.invoiceapp.entity.Invoice;
import com.invoiceapp.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    public InvoiceDTO createInvoice(InvoiceDTO invoiceDTO) {
        System.out.println("Creating new invoice: " + invoiceDTO.getInvoiceNumber());

        Invoice invoice = convertToEntity(invoiceDTO);
        // Let MongoDB generate the ID for new invoices
        invoice.setId(null);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setUpdatedAt(LocalDateTime.now());

        Invoice savedInvoice = invoiceRepository.save(invoice);
        System.out.println("Invoice saved with ID: " + savedInvoice.getId());
        return convertToDTO(savedInvoice);
    }

    public InvoiceDTO updateInvoice(String id, InvoiceDTO invoiceDTO) {
        System.out.println("Updating invoice: " + id);

        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));

        invoice.setInvoiceNumber(invoiceDTO.getInvoiceNumber());
        invoice.setDate(invoiceDTO.getDate());
        invoice.setDueDate(invoiceDTO.getDueDate()); // Added due date mapping
        invoice.setEmployeeName(invoiceDTO.getEmployeeName());
        invoice.setEmployeeId(invoiceDTO.getEmployeeId());
        invoice.setEmployeeEmail(invoiceDTO.getEmployeeEmail());
        invoice.setEmployeeAddress(invoiceDTO.getEmployeeAddress());
        invoice.setEmployeeMobile(invoiceDTO.getEmployeeMobile());
        invoice.setServices(invoiceDTO.getServices());
        invoice.setTaxRate(invoiceDTO.getTaxRate());
        invoice.setUpdatedAt(LocalDateTime.now());

        Invoice updatedInvoice = invoiceRepository.save(invoice);
        return convertToDTO(updatedInvoice);
    }

    public void deleteInvoice(String id) {
        System.out.println("Deleting invoice: " + id);
        invoiceRepository.deleteById(id);
    }

    public InvoiceDTO getInvoiceById(String id) {
        System.out.println("Fetching invoice: " + id);
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
        return convertToDTO(invoice);
    }

    public List<InvoiceDTO> getAllInvoices(String userId) {
        System.out.println("Fetching all invoices for user: " + userId);
        if (userId == null || userId.isEmpty()) {
            return invoiceRepository.findAllByOrderByCreatedAtDesc()
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }
        return invoiceRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InvoiceDTO> getAllInvoices() {
        return getAllInvoices(null);
    }

    public List<InvoiceDTO> getInvoicesByEmployeeId(String employeeId) {
        System.out.println("Fetching invoices for employee: " + employeeId);
        return invoiceRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Autowired
    private com.invoiceapp.repository.CompanyInfoRepository companyInfoRepository;

    public String getNextInvoiceNumber(String userId) {
        String format = "INV-"; // Default
        if (userId != null) {
            com.invoiceapp.entity.CompanyInfo companyInfo = companyInfoRepository.findByUserId(userId).orElse(null);
            if (companyInfo != null && companyInfo.getInvoiceFormat() != null) {
                format = companyInfo.getInvoiceFormat();
            }
        }

        long count = userId != null ? invoiceRepository.countByUserId(userId) : invoiceRepository.count();
        // If isolate is strictly enforced, we should rely on countByUserId.
        // Logic: count + 1.
        // e.g. 0 invoices -> count=0 -> next=1
        return format + (count + 1);
    }

    private Invoice convertToEntity(InvoiceDTO dto) {
        Invoice invoice = new Invoice();
        invoice.setId(dto.getId());
        invoice.setInvoiceNumber(dto.getInvoiceNumber());
        invoice.setDate(dto.getDate());
        invoice.setDueDate(dto.getDueDate()); // Added due date mapping
        invoice.setEmployeeName(dto.getEmployeeName());
        invoice.setEmployeeId(dto.getEmployeeId());
        invoice.setEmployeeEmail(dto.getEmployeeEmail());
        invoice.setEmployeeAddress(dto.getEmployeeAddress());
        invoice.setEmployeeMobile(dto.getEmployeeMobile());
        invoice.setServices(dto.getServices());
        invoice.setTaxRate(dto.getTaxRate());
        invoice.setCountry(dto.getCountry());
        invoice.setUserId(dto.getUserId());
        // Map company info if needed (DTO to Entity) - skipping complex mapping for now
        // or need helper
        return invoice;
    }

    private InvoiceDTO convertToDTO(Invoice entity) {
        InvoiceDTO dto = new InvoiceDTO();
        dto.setId(entity.getId());
        dto.setInvoiceNumber(entity.getInvoiceNumber());
        dto.setUserId(entity.getUserId());
        // Map company info entity to DTO if needed

        dto.setDate(entity.getDate());
        dto.setDueDate(entity.getDueDate()); // Added due date mapping
        dto.setEmployeeName(entity.getEmployeeName());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeEmail(entity.getEmployeeEmail());
        dto.setEmployeeAddress(entity.getEmployeeAddress());
        dto.setEmployeeMobile(entity.getEmployeeMobile());
        dto.setServices(entity.getServices());
        dto.setTaxRate(entity.getTaxRate());
        dto.setCountry(entity.getCountry());
        dto.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        dto.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return dto;
    }
}
