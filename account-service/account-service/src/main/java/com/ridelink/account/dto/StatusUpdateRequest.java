package com.ridelink.account.dto;

import com.ridelink.account.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull
        AccountStatus status
) {
}
