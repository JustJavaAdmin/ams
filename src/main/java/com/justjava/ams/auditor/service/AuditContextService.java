package com.justjava.ams.auditor.service;

import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditContextService {
    private final UserRepository userRepository;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof DefaultOidcUser oidcUser) {
            Optional<User> bySubject = findByKeycloakId(oidcUser.getSubject());
            if (bySubject.isPresent()) {
                return bySubject;
            }

            Optional<User> byPreferredUsername = findByUsername(asString(oidcUser.getClaims().get("preferred_username")));
            if (byPreferredUsername.isPresent()) {
                return byPreferredUsername;
            }

            Optional<User> byEmail = findByEmail(asString(oidcUser.getClaims().get("email")));
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }

        return findByUsername(authentication.getName());
    }

    public User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audit user not found"));
    }

    public String currentIpAddress() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            return null;
        }

        String forwardedFor = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor;
        }

        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        return realIp != null ? realIp : trimToNull(request.getRemoteAddr());
    }

    public String currentUserAgent() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        return request != null ? trimToNull(request.getHeader("User-Agent")) : null;
    }

    private Optional<User> findByKeycloakId(String value) {
        String normalized = trimToNull(value);
        return normalized != null ? userRepository.findByKeycloakId(normalized) : Optional.empty();
    }

    private Optional<User> findByUsername(String value) {
        String normalized = trimToNull(value);
        return normalized != null ? userRepository.findByUsername(normalized) : Optional.empty();
    }

    private Optional<User> findByEmail(String value) {
        String normalized = trimToNull(value);
        return normalized != null ? userRepository.findByEmail(normalized) : Optional.empty();
    }

    private String firstForwardedIp(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String first = normalized.split(",", 2)[0];
        return trimToNull(first);
    }

    private String asString(Object value) {
        return value instanceof String string ? string : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
