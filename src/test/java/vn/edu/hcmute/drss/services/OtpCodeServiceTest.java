package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class OtpCodeServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID OTHER_USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private OtpCodeService otpCodeService;

    @Test
    void generatedOtpAlwaysContainsExactlySixDigits() {
        for (int index = 0; index < 100; index++) {
            assertThat(otpCodeService.generate())
                    .matches("[0-9]{6}");
        }
    }

    @Test
    void digestIsDeterministicAndDoesNotExposeRawOtp() {
        String otp = "012345";

        String firstDigest = otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                otp
        );

        String secondDigest = otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                otp
        );

        assertThat(firstDigest)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(secondDigest)
                .doesNotContain(otp);
    }

    @Test
    void digestIsBoundToUserPurposeAndOtp() {
        String originalDigest = otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "123456"
        );

        assertThat(otpCodeService.digest(
                OTHER_USER_ID,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "123456"
        )).isNotEqualTo(originalDigest);

        assertThat(otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        )).isNotEqualTo(originalDigest);

        assertThat(otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "654321"
        )).isNotEqualTo(originalDigest);
    }

    @Test
    void matchesUsesExactOtpAndRejectsInvalidInput() {
        String digest = otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        );

        assertThat(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456",
                digest
        )).isTrue();

        assertThat(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123457",
                digest
        )).isFalse();

        assertThat(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                null,
                digest
        )).isFalse();

        assertThat(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "12345",
                digest
        )).isFalse();

        assertThat(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456",
                "invalid-digest"
        )).isFalse();
    }

    @Test
    void digestRejectsMalformedOtp() {
        assertThatThrownBy(() -> otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "12345"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OTP must contain exactly 6 digits");
    }
}