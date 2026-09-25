package com.ridelink.account.security;

import com.ridelink.account.entity.AccountStatus;
import com.ridelink.account.entity.User;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies the JWT signature/expiry locally (no callback needed), then re-checks the
 * account's current status in the database on every request. A token stays valid for its
 * full lifetime, but a suspended/deactivated account is blocked from the very next request -
 * not just at their next login - closing the gap where an already-issued token would
 * otherwise keep working after suspension.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtService.isValid(token)) {
                Claims claims = jwtService.parseClaims(token);
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                Optional<User> currentUser = safeFindById(userId);

                if (currentUser.isPresent() && currentUser.get().getStatus() == AccountStatus.ACTIVE) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                // If the account no longer exists or is not ACTIVE, we simply leave the
                // SecurityContext unauthenticated - the request falls through as anonymous
                // and Spring Security rejects it with 401 for any protected endpoint.
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> safeFindById(String userId) {
        try {
            return userRepository.findById(UUID.fromString(userId));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
