package com.invoiceapp.service;

import com.invoiceapp.dto.*;
import com.invoiceapp.entity.BankDetails;
import com.invoiceapp.entity.CompanyInfo;
import com.invoiceapp.entity.User;
import com.invoiceapp.repository.CompanyInfoRepository;
import com.invoiceapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyInfoRepository companyInfoRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private FileStorageService fileStorageService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest loginRequest) {
        // Validate both email and password are provided
        if (loginRequest.getEmail() == null || loginRequest.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (loginRequest.getPassword() == null || loginRequest.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }

        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail().toLowerCase().trim());
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        User user = userOpt.get();

        // Verify password using bcrypt
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Generate JWT token with only userId and email
        String token = jwtService.generateToken(user.getId(), user.getEmail());

        // Return only token, userId, and email (NOT full user object)
        return new LoginResponse(token, user.getId(), user.getEmail());
    }

    public SignupResponse signup(SignupRequest signupRequest) throws Exception {
        // Validate email and password are provided
        if (signupRequest.getEmail() == null || signupRequest.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (signupRequest.getPassword() == null || signupRequest.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (signupRequest.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        // Check if user already exists
        if (userRepository.existsByEmail(signupRequest.getEmail().toLowerCase().trim())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Create user
        User user = new User();
        user.setEmail(signupRequest.getEmail().toLowerCase().trim());
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        user.setName(signupRequest.getName().trim());
        user.setCreatedAt(java.time.LocalDateTime.now());

        User savedUser = userRepository.save(user);

        // Store company logo if provided
        String logoUrl = null;
        if (signupRequest.getCompanyLogo() != null && !signupRequest.getCompanyLogo().isEmpty()) {
            try {
                logoUrl = fileStorageService.storeFile(signupRequest.getCompanyLogo(), savedUser.getId());
            } catch (Exception e) {
                // If logo upload fails, continue without logo
                System.err.println("Failed to store company logo: " + e.getMessage());
            }
        }

        // Create bank details entity
        BankDetails bankDetails = null;
        if (signupRequest.getBankDetails() != null) {
            BankDetailsDTO bankDto = signupRequest.getBankDetails();
            bankDetails = new BankDetails(
                    bankDto.getBankName(),
                    bankDto.getAccountNumber(),
                    bankDto.getAccountHolderName(),
                    bankDto.getIfscCode(),
                    bankDto.getBranchName(),
                    bankDto.getBranchCode(),
                    bankDto.getAccountType() // Added accountType
            );
        }

        // Create company info
        CompanyInfo companyInfo = new CompanyInfo();
        companyInfo.setUserId(savedUser.getId());
        companyInfo.setCompanyName(signupRequest.getCompanyName().trim());
        companyInfo.setCompanyAddress(signupRequest.getCompanyAddress().trim());
        companyInfo.setCompanyLogoUrl(logoUrl);
        companyInfo.setInvoiceFormat(signupRequest.getInvoiceFormat());
        companyInfo.setBankDetails(bankDetails);
        companyInfo.setCreatedAt(java.time.LocalDateTime.now());
        companyInfo.setUpdatedAt(java.time.LocalDateTime.now());

        CompanyInfo savedCompanyInfo = companyInfoRepository.save(companyInfo);

        // Update user with company info reference
        savedUser.setCompanyInfoId(savedCompanyInfo.getId());
        userRepository.save(savedUser);

        // Generate JWT token
        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());

        // Convert to DTOs
        BankDetailsDTO bankDetailsDTO = null;
        if (bankDetails != null) {
            bankDetailsDTO = new BankDetailsDTO();
            bankDetailsDTO.setBankName(bankDetails.getBankName());
            bankDetailsDTO.setAccountNumber(bankDetails.getAccountNumber());
            bankDetailsDTO.setAccountHolderName(bankDetails.getAccountHolderName());
            bankDetailsDTO.setIfscCode(bankDetails.getIfscCode());
            bankDetailsDTO.setBranchName(bankDetails.getBranchName());
            bankDetailsDTO.setBranchCode(bankDetails.getBranchCode());
            bankDetailsDTO.setAccountType(bankDetails.getAccountType());
        }

        CompanyInfoDTO companyInfoDTO = new CompanyInfoDTO();
        companyInfoDTO.setId(savedCompanyInfo.getId());
        companyInfoDTO.setCompanyName(savedCompanyInfo.getCompanyName());
        companyInfoDTO.setCompanyAddress(savedCompanyInfo.getCompanyAddress());
        companyInfoDTO.setCompanyLogoUrl(savedCompanyInfo.getCompanyLogoUrl());
        companyInfoDTO.setBankDetails(bankDetailsDTO);

        return new SignupResponse(token, savedUser.getId(), savedUser.getEmail(), companyInfoDTO);
    }

    // Helper method to hash password (for creating users manually or via admin)
    public String hashPassword(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    // Get company info by userId
    public java.util.Optional<com.invoiceapp.entity.CompanyInfo> getCompanyInfoByUserId(String userId) {
        return companyInfoRepository.findByUserId(userId);
    }
}
