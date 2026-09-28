package com.alqaseh.ecommerce.infrastructure.security;

import com.alqaseh.ecommerce.infrastructure.user.entity.Role;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Read access to the authenticated caller of the current thread. */
public final class SecurityUtils {

    private static final String ADMIN_AUTHORITY = "ROLE_" + Role.ADMIN.name();

    private SecurityUtils() {
    }

    public static Optional<String> getCurrentUsername() {
        return authentication().map(Authentication::getName);
    }

    public static Optional<UUID> getCurrentUserId() {
        return authentication()
                .map(Authentication::getPrincipal)
                .filter(UserPrincipal.class::isInstance)
                .map(principal -> ((UserPrincipal) principal).getId());
    }

    /** For code that is only reachable by authenticated users (guarded by {@code @PreAuthorize}). */
    public static UUID requireCurrentUserId() {
        return getCurrentUserId().orElseThrow(() -> new IllegalStateException("No authenticated user in the security context"));
    }

    public static boolean isCurrentUserAdmin() {
        return authentication()
                .map(auth -> auth.getAuthorities().stream().anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority())))
                .orElse(false);
    }

    private static Optional<Authentication> authentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(auth -> !(auth instanceof AnonymousAuthenticationToken));
    }
}
