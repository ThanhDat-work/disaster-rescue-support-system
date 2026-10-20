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
    private final List<String> roles;

    public AuthenticatedUserPrincipal(
            UUID userId,
            String email,
            String fullName,
            int authVersion,
            List<String> roles
    ) {
        this.userId = Objects.requireNonNull(
                userId,
                "User ID is required"
        );
        this.email = requireText(email, "Email");
        this.fullName = requireText(fullName, "Full name");
        this.authVersion = authVersion;
        this.roles = List.copyOf(
                Objects.requireNonNull(roles, "Roles are required")
        );

        if (this.roles.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one active role is required"
            );
        }
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

    public List<String> roles() {
        return roles;
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
                + ", roles="
                + roles
                + "]";
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