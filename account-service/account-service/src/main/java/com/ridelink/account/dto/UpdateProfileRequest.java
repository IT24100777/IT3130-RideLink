package com.ridelink.account.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank
        String fullName,

        String phoneNumber
) {
}
