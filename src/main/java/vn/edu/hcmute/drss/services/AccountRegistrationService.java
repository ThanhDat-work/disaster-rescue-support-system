package vn.edu.hcmute.drss.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.dtos.RegistrationRequest;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;

import java.util.Optional;

@Service
public class AccountRegistrationService {

    private final AuthRateLimitService rateLimitService;
    private final RegistrationPersistenceService persistenceService;
    private final GmailOtpEmailService emailService;

    public AccountRegistrationService(
            AuthRateLimitService rateLimitService,
            RegistrationPersistenceService persistenceService,
            GmailOtpEmailService emailService
    ) {
        this.rateLimitService = rateLimitService;
        this.persistenceService = persistenceService;
        this.emailService = emailService;
    }

    public RegistrationResult register(
            RegistrationRequest request,
            String ipAddress
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Registration request is required"
            );
        }

        RateLimitDecision decision =
                rateLimitService.consumePublicRequest(
                        AuthRateLimitAction.REGISTER,
                        request.email(),
                        ipAddress
                );

        if (!decision.allowed()) {
            return RegistrationResult.rateLimited(
                    decision.blockedUntil()
            );
        }

        Optional<PendingOtpDelivery> pendingDelivery;

        try {
            pendingDelivery =
                    persistenceService.prepareRegistration(request);
        } catch (DataIntegrityViolationException exception) {
            /*
             * Một request đồng thời có thể vừa tạo cùng email.
             * Không trả chi tiết constraint để tránh account enumeration.
             */
            return RegistrationResult.accepted();
        }

        if (pendingDelivery.isEmpty()) {
            return RegistrationResult.accepted();
        }

        PendingOtpDelivery delivery = pendingDelivery.get();

        try {
            emailService.sendOtp(
                    delivery.recipient(),
                    VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                    delivery.otp()
            );

            return RegistrationResult.accepted();
        } catch (OtpMailDeliveryException exception) {
            /*
             * Transaction lưu user/role/token đã commit trước khi
             * đi vào khối gửi Gmail này.
             */
            return RegistrationResult.emailDeliveryFailed();
        }
    }
}