package vn.edu.hcmute.drss.exceptions;

public class InvalidMediaUploadException extends IllegalArgumentException {

    public InvalidMediaUploadException(String message) {
        super(message);
    }

    public InvalidMediaUploadException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}