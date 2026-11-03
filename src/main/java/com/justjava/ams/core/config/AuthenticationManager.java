package com.justjava.ams.core.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationManager {

    public Object get(String fieldName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof DefaultOidcUser defaultOidcUser) {
            return defaultOidcUser.getClaims().get(fieldName);
        }

        return null;
    }

    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    @SuppressWarnings("unchecked")
    public boolean isAdmin() {
        List<String> groups = (List<String>) this.get("groups");
        return groups != null && groups.contains("/admin");
    }

    @SuppressWarnings("unchecked")
    public boolean isSystemAdmin() {
        List<String> groups = (List<String>) this.get("groups");
        return groups != null && groups.contains("/systemAdmin");
    }

    @SuppressWarnings("unchecked")
    public boolean isAccountant() {
        List<String> groups = (List<String>) this.get("groups");
        return groups != null && groups.contains("/accountant");
    }

    @SuppressWarnings("unchecked")
    public boolean isAuditor() {
        List<String> groups = (List<String>) this.get("groups");
        return groups != null && groups.contains("/auditor");
    }

    @SuppressWarnings("unchecked")
    public boolean isCfo() {
        List<String> groups = (List<String>) this.get("groups");
        return groups != null && groups.contains("/cfo");
    }

    @SuppressWarnings("unchecked")
    public boolean isFinanceAdmin() {
        List<String> groups = (List<String>) this.get("groups");
        return groups != null && groups.contains("/financeAdmin");
    }

    public Object getAllAttributes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof DefaultOidcUser defaultOidcUser) {
            return defaultOidcUser.getClaims();
        }

        return null;
    }
}
