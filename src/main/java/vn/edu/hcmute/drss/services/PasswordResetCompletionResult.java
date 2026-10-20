package vn.edu.hcmute.drss.services;

public enum PasswordResetCompletionResult {
    COMPLETED,
    INVALID_GRANT,
    PASSWORD_POLICY_VIOLATION,
    PASSWORD_CONFIRMATION_MISMATCH
}