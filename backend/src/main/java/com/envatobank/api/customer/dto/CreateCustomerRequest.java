package com.envatobank.api.customer.dto;

import com.envatobank.api.customer.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "national id must be 11 digits") String nationalId,
        @NotBlank @Pattern(regexp = "\\+?\\d{10,15}", message = "phone must be 10-15 digits") String phone,
        @NotBlank @Size(min = 8, max = 128) String password,
        Role role
) {
}
