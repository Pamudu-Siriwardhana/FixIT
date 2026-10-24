package com.serviceportal.dto;

import com.serviceportal.model.Role;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RegistrationForm {

    @NotNull(message = "Select an account type")
    private Role role;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    private String email;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Enter a valid contact number (9-15 digits)")
    private String contactNumber;

    @NotBlank(message = "Select a gender")
    private String gender;

    @NotEmpty(message = "Select at least one language")
    private List<Long> languageIds = new ArrayList<>();

    // Sri Lankan NIC: old format (9 digits + V/X) or new format (12 digits). Adjust if needed.
    @NotBlank(message = "NIC number is required")
    @Pattern(regexp = "^([0-9]{9}[vVxX]|[0-9]{12})$", message = "Enter a valid NIC number")
    private String nic;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Confirm your password")
    private String confirmPassword;
}
