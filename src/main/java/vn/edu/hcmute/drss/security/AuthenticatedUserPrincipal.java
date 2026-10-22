package vn.edu.hcmute.drss.security;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AuthenticatedUserPrincipal implements Principal {

    private final UUID userId;
    private final String email;
    private final String fullName;
    private final int authVersion;
    private final String activeRole;

    public AuthenticatedUserPrincipal(
            UUID userId,
            String email,
            String fullName,
            int authVersion,
            String activeRole
    ) {
        this.userId = Objects.requireNonNull(
                userId,
                "User ID is required"
        );
        this.email = requireText(email, "Email");
        this.fullName = requireText(fullName, "Full name");
        this.authVersion = authVersion;
        this.activeRole = requireRole(activeRole);
    }

    public AuthenticatedUserPrincipal(
            UUID userId,
            String email,
            String fullName,
            int authVersion,
            List<String> roles
    ) {
        this(
                userId,
                email,
                fullName,
                authVersion,
                requireSingleRole(roles)
        );
    }

    public UUID userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String fullName() {
        return fullName;
    }

    public int authVersion() {
        return authVersion;
    }

    public String activeRole() {
        return activeRole;
    }

    public List<String> roles() {
        return List.of(activeRole);
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public String toString() {
        return "AuthenticatedUserPrincipal[userId="
                + userId
                + ", authVersion="
                + authVersion
                + ", activeRole="
                + activeRole
                + "]";
    }

    private static String requireSingleRole(List<String> roles) {
        if (roles == null || roles.size() != 1) {
            throw new IllegalArgumentException(
                    "Exactly one active role is required"
            );
        }
        return roles.get(0);
    }

    private static String requireRole(String role) {
        String value = requireText(role, "Active role").strip();
        if (!value.startsWith("ROLE_")) {
            throw new IllegalArgumentException(
                    "Active role must start with ROLE_"
            );
        }
        return value;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }
        return value;
    }
}