package com.ridelink.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridelink.account.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ensures a missing/invalid/expired token produces the same ApiError JSON shape as every
 * other error in this service, instead of Spring Security's default empty 401 body. The
 * assignment brief asks for consistent error responses across the API.
 *
 * Uses the same ObjectMapper Spring Boot auto-configures for the rest of the app (injected,
 * not a separately-constructed one) so timestamp/date formatting is identical everywhere -
 * a locally built ObjectMapper can format java.time types differently.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        ApiError body = ApiError.of(401, "Unauthorized", "A valid access token is required for this endpoint");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
