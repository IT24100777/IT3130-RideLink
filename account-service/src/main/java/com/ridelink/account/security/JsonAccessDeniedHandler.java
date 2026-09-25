package com.ridelink.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.account.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ensures an authenticated-but-not-authorized request (e.g. a passenger calling an
 * admin-only endpoint, or a user trying to view someone else's account) produces the same
 * ApiError JSON shape as every other error in this service, instead of an empty 403 body.
 *
 * Uses the same ObjectMapper Spring Boot auto-configures for the rest of the app (injected,
 * not a separately-constructed one) so timestamp/date formatting is identical everywhere.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        ApiError body = ApiError.of(403, "Forbidden", "You do not have permission to perform this action");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
