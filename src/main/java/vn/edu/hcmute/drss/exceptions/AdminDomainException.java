package vn.edu.hcmute.drss.exceptions;

public class AdminDomainException extends RuntimeException {

    private final String field;

    public AdminDomainException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}