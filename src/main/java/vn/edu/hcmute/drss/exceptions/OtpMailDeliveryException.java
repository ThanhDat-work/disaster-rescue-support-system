package vn.edu.hcmute.drss.exceptions;

public class OtpMailDeliveryException extends RuntimeException {

    private static final String SAFE_MESSAGE =
            "Unable to deliver OTP email";

    public OtpMailDeliveryException(Throwable cause) {
        super(SAFE_MESSAGE, cause);
    }
}