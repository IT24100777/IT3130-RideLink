package com.ridelink.account.dto;

import com.ridelink.account.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank
        String fullName,

        String phoneNumber,

        @NotNull(message = "Role must be PASSENGER or DRIVER")
        Role role
) {
}
