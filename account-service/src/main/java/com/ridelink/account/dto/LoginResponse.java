package com.ridelink.account.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AccountResponse account
) {
    public static LoginResponse of(String token, long expiresInSeconds, AccountResponse account) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, account);
    }
}
