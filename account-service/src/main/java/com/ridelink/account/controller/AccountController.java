package com.ridelink.account.controller;

import com.ridelink.account.dto.AccountResponse;
import com.ridelink.account.dto.StatusUpdateRequest;
import com.ridelink.account.dto.UpdateProfileRequest;
import com.ridelink.account.entity.Role;
import com.ridelink.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "View the caller's own account profile")
    @GetMapping("/me")
    public ResponseEntity<AccountResponse> getMyProfile(Authentication authentication) {
        UUID callerId = currentUserId(authentication);
        return ResponseEntity.ok(accountService.getById(callerId));
    }

    @Operation(summary = "Update the caller's own profile (name, phone number)")
    @PutMapping("/me")
    public ResponseEntity<AccountResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID callerId = currentUserId(authentication);
        return ResponseEntity.ok(accountService.updateProfile(callerId, request));
    }

    @Operation(summary = "Fetch account info by id - only the account owner or an admin may call this. " +
            "Other RideLink services should call this using an admin/service-level token, not a passenger's own token.")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    @Operation(summary = "List accounts, optionally filtered by role (admin only)")
    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts(@RequestParam(required = false) Role role) {
        return ResponseEntity.ok(accountService.listAccounts(role));
    }

    @Operation(summary = "Suspend, reactivate or deactivate an account (admin only)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(accountService.updateStatus(id, request));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
