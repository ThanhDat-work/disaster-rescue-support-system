package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class OtpCodeService {

    private static final int OTP_BOUND = 1_000_000;
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final Pattern OTP_PATTERN = Pattern.compile("[0-9]{6}");
    private static final Pattern DIGEST_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final SecureRandom secureRandom;
    private final SecretKey otpSecretKey;

    public OtpCodeService(
            SecureRandom secureRandom,
            AuthSecurityProperties properties
    ) {
        this.secureRandom = secureRandom;
        this.otpSecretKey = new SecretKeySpec(
                properties.otpHmacSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_SHA_256
        );
    }

    public String generate() {
        int value = secureRandom.nextInt(OTP_BOUND);
        return String.format(Locale.ROOT, "%06d", value);
    }

    public String digest(
            UUID userId,
            VerificationTokenPurpose purpose,
            String otp
    ) {
        validateContext(userId, purpose);

        if (!isValidOtp(otp)) {
            throw new IllegalArgumentException("OTP must contain exactly 6 digits");
        }

        byte[] digestBytes = calculateDigest(userId, purpose, otp);
        return HexFormat.of().formatHex(digestBytes);
    }

    public boolean matches(
            UUID userId,
            VerificationTokenPurpose purpose,
            String candidateOtp,
            String expectedDigest
    ) {
        if (userId == null
                || purpose == null
                || !isValidOtp(candidateOtp)
                || expectedDigest == null
                || !DIGEST_PATTERN.matcher(expectedDigest).matches()) {
            return false;
        }

        byte[] candidateDigest = calculateDigest(userId, purpose, candidateOtp);
        byte[] storedDigest = HexFormat.of().parseHex(expectedDigest);

        return MessageDigest.isEqual(candidateDigest, storedDigest);
    }

    private byte[] calculateDigest(
            UUID userId,
            VerificationTokenPurpose purpose,
            String otp
    ) {
        String payload = userId + ":" + purpose.name() + ":" + otp;

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(otpSecretKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to calculate OTP digest",
                    exception
            );
        }
    }

    private void validateContext(
            UUID userId,
            VerificationTokenPurpose purpose
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (purpose == null) {
            throw new IllegalArgumentException("OTP purpose is required");
        }
    }

    private boolean isValidOtp(String otp) {
        return otp != null && OTP_PATTERN.matcher(otp).matches();
    }
}