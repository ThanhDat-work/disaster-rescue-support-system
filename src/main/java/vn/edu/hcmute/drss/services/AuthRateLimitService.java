package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.AuthRateLimit;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.AuthRateLimitScope;
import vn.edu.hcmute.drss.repositories.AuthRateLimitRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthRateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN =
            Duration.ofSeconds(60);
    private static final Duration PENALTY_RESET_AFTER =
            Duration.ofHours(24);
    private static final Duration INITIAL_PENALTY =
            Duration.ofMinutes(15);
    private static final Duration MAXIMUM_PENALTY =
            Duration.ofHours(24);

    private static final int SUBJECT_REQUEST_LIMIT = 3;
    private static final int IP_REQUEST_LIMIT = 10;
    private static final int LOGIN_SUBJECT_FAILURE_LIMIT = 5;
    private static final int FAILURE_IP_LIMIT = 20;

    private final AuthRateLimitRepository repository;
    private final RateLimitSubjectKeyService subjectKeyService;
    private final Clock clock;

    public AuthRateLimitService(
            AuthRateLimitRepository repository,
            RateLimitSubjectKeyService subjectKeyService,
            Clock clock
    ) {
        this.repository = repository;
        this.subjectKeyService = subjectKeyService;
        this.clock = clock;
    }

    /**
     * Tiêu thụ một lượt cho các luồng public có fixed window:
     * REGISTER, RESEND_OTP và FORGOT_PASSWORD.
     */
    @Transactional
    public RateLimitDecision consumePublicRequest(
            AuthRateLimitAction action,
            String email,
            String ipAddress
    ) {
        requirePublicRequestAction(action);

        LocalDateTime now = currentDateTime();

        SubjectRecord emailSubject = lockEmailIpSubject(
                action,
                email,
                ipAddress
        );
        SubjectRecord ipSubject = lockIpSubject(action, ipAddress);

        LocalDateTime emailBlockedUntil = publicRequestBlockedUntil(
                emailSubject.record(),
                SUBJECT_REQUEST_LIMIT,
                action == AuthRateLimitAction.RESEND_OTP,
                now
        );

        LocalDateTime ipBlockedUntil = publicRequestBlockedUntil(
                ipSubject.record(),
                IP_REQUEST_LIMIT,
                false,
                now
        );

        LocalDateTime blockedUntil = laterOf(
                emailBlockedUntil,
                ipBlockedUntil
        );

        if (blockedUntil != null) {
            return RateLimitDecision.blockedUntil(blockedUntil);
        }

        AuthRateLimit emailRecord = preparePublicRequestRecord(
                emailSubject,
                now
        );
        AuthRateLimit ipRecord = preparePublicRequestRecord(
                ipSubject,
                now
        );

        repository.save(emailRecord);
        repository.save(ipRecord);

        return RateLimitDecision.allowedDecision();
    }

    /**
     * Kiểm tra trước khi thực hiện so khớp mật khẩu.
     * Phương thức này không tăng bộ đếm.
     */
    @Transactional(readOnly = true)
    public RateLimitDecision checkLoginAllowed(
            String email,
            String ipAddress
    ) {
        LocalDateTime now = currentDateTime();

        SubjectRecord emailSubject = findEmailIpSubject(
                AuthRateLimitAction.LOGIN,
                email,
                ipAddress
        );
        SubjectRecord ipSubject = findIpSubject(
                AuthRateLimitAction.LOGIN,
                ipAddress
        );

        return activeBlockDecision(
                emailSubject.record(),
                ipSubject.record(),
                now
        );
    }

    /**
     * Ghi nhận một lần đăng nhập sai.
     *
     * EMAIL_IP khóa ở lần sai thứ 5.
     * IP khóa ở lần sai thứ 20.
     */
    @Transactional
    public RateLimitDecision recordLoginFailure(
            String email,
            String ipAddress
    ) {
        LocalDateTime now = currentDateTime();

        SubjectRecord emailSubject = lockEmailIpSubject(
                AuthRateLimitAction.LOGIN,
                email,
                ipAddress
        );
        SubjectRecord ipSubject = lockIpSubject(
                AuthRateLimitAction.LOGIN,
                ipAddress
        );

        RateLimitDecision existingBlock = activeBlockDecision(
                emailSubject.record(),
                ipSubject.record(),
                now
        );

        if (!existingBlock.allowed()) {
            return existingBlock;
        }

        AuthRateLimit emailRecord = prepareFailureRecord(
                emailSubject,
                LOGIN_SUBJECT_FAILURE_LIMIT,
                now
        );
        AuthRateLimit ipRecord = prepareFailureRecord(
                ipSubject,
                FAILURE_IP_LIMIT,
                now
        );

        repository.save(emailRecord);
        repository.save(ipRecord);

        return decisionAfterFailure(
                emailRecord,
                ipRecord,
                now
        );
    }

    /**
     * Login thành công chỉ xóa record LOGIN + EMAIL_IP.
     * Record LOGIN + IP phải được giữ lại theo security contract.
     */
    @Transactional
    public void clearLoginSubjectAfterSuccess(
            String email,
            String ipAddress
    ) {
        String subjectKey = subjectKeyService.createEmailIpKey(
                AuthRateLimitAction.LOGIN,
                email,
                ipAddress
        );

        repository.deleteByActionAndScopeAndSubjectKey(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                subjectKey
        );
    }

    /**
     * Kiểm tra giới hạn IP trước khi xác minh OTP.
     * Số lần sai trên từng token do VerificationTokenService quản lý.
     */
    @Transactional(readOnly = true)
    public RateLimitDecision checkVerificationAllowed(
            AuthRateLimitAction action,
            String ipAddress
    ) {
        requireVerificationAction(action);

        SubjectRecord ipSubject = findIpSubject(action, ipAddress);

        return activeBlockDecision(
                ipSubject.record(),
                Optional.empty(),
                currentDateTime()
        );
    }

    /**
     * Ghi nhận OTP sai ở scope IP.
     *
     * Thành công không xóa record IP-wide.
     */
    @Transactional
    public RateLimitDecision recordVerificationFailure(
            AuthRateLimitAction action,
            String ipAddress
    ) {
        requireVerificationAction(action);

        LocalDateTime now = currentDateTime();
        SubjectRecord ipSubject = lockIpSubject(action, ipAddress);

        RateLimitDecision existingBlock = activeBlockDecision(
                ipSubject.record(),
                Optional.empty(),
                now
        );

        if (!existingBlock.allowed()) {
            return existingBlock;
        }

        AuthRateLimit ipRecord = prepareFailureRecord(
                ipSubject,
                FAILURE_IP_LIMIT,
                now
        );

        repository.save(ipRecord);

        return decisionAfterFailure(
                ipRecord,
                null,
                now
        );
    }

        @Transactional(readOnly = true)
    public RateLimitDecision checkRoleSwitchAllowed(
            String email,
            String ipAddress
    ) {
        LocalDateTime now = currentDateTime();

        SubjectRecord emailSubject = findEmailIpSubject(
                AuthRateLimitAction.ROLE_SWITCH,
                email,
                ipAddress
        );
        SubjectRecord ipSubject = findIpSubject(
                AuthRateLimitAction.ROLE_SWITCH,
                ipAddress
        );

        return activeBlockDecision(
                emailSubject.record(),
                ipSubject.record(),
                now
        );
    }

    @Transactional
    public RateLimitDecision recordRoleSwitchFailure(
            String email,
            String ipAddress
    ) {
        LocalDateTime now = currentDateTime();

        SubjectRecord emailSubject = lockEmailIpSubject(
                AuthRateLimitAction.ROLE_SWITCH,
                email,
                ipAddress
        );
        SubjectRecord ipSubject = lockIpSubject(
                AuthRateLimitAction.ROLE_SWITCH,
                ipAddress
        );

        RateLimitDecision existingBlock = activeBlockDecision(
                emailSubject.record(),
                ipSubject.record(),
                now
        );
        if (!existingBlock.allowed()) {
            return existingBlock;
        }

        AuthRateLimit emailRecord = prepareFailureRecord(
                emailSubject,
                LOGIN_SUBJECT_FAILURE_LIMIT,
                now
        );
        AuthRateLimit ipRecord = prepareFailureRecord(
                ipSubject,
                FAILURE_IP_LIMIT,
                now
        );

        repository.save(emailRecord);
        repository.save(ipRecord);

        return decisionAfterFailure(
                emailRecord,
                ipRecord,
                now
        );
    }

    @Transactional
    public void clearRoleSwitchSubjectAfterSuccess(
            String email,
            String ipAddress
    ) {
        String subjectKey = subjectKeyService.createEmailIpKey(
                AuthRateLimitAction.ROLE_SWITCH,
                email,
                ipAddress
        );

        repository.deleteByActionAndScopeAndSubjectKey(
                AuthRateLimitAction.ROLE_SWITCH,
                AuthRateLimitScope.EMAIL_IP,
                subjectKey
        );
    }

    private SubjectRecord lockEmailIpSubject(
            AuthRateLimitAction action,
            String email,
            String ipAddress
    ) {
        String subjectKey = subjectKeyService.createEmailIpKey(
                action,
                email,
                ipAddress
        );

        Optional<AuthRateLimit> record = repository.findForUpdate(
                action,
                AuthRateLimitScope.EMAIL_IP,
                subjectKey
        );

        return new SubjectRecord(
                action,
                AuthRateLimitScope.EMAIL_IP,
                subjectKey,
                record
        );
    }

    private SubjectRecord lockIpSubject(
            AuthRateLimitAction action,
            String ipAddress
    ) {
        String subjectKey = subjectKeyService.createIpKey(
                action,
                ipAddress
        );

        Optional<AuthRateLimit> record = repository.findForUpdate(
                action,
                AuthRateLimitScope.IP,
                subjectKey
        );

        return new SubjectRecord(
                action,
                AuthRateLimitScope.IP,
                subjectKey,
                record
        );
    }

    private SubjectRecord findEmailIpSubject(
            AuthRateLimitAction action,
            String email,
            String ipAddress
    ) {
        String subjectKey = subjectKeyService.createEmailIpKey(
                action,
                email,
                ipAddress
        );

        Optional<AuthRateLimit> record =
                repository.findByActionAndScopeAndSubjectKey(
                        action,
                        AuthRateLimitScope.EMAIL_IP,
                        subjectKey
                );

        return new SubjectRecord(
                action,
                AuthRateLimitScope.EMAIL_IP,
                subjectKey,
                record
        );
    }

    private SubjectRecord findIpSubject(
            AuthRateLimitAction action,
            String ipAddress
    ) {
        String subjectKey = subjectKeyService.createIpKey(
                action,
                ipAddress
        );

        Optional<AuthRateLimit> record =
                repository.findByActionAndScopeAndSubjectKey(
                        action,
                        AuthRateLimitScope.IP,
                        subjectKey
                );

        return new SubjectRecord(
                action,
                AuthRateLimitScope.IP,
                subjectKey,
                record
        );
    }

    private LocalDateTime publicRequestBlockedUntil(
            Optional<AuthRateLimit> optionalRecord,
            int limit,
            boolean enforceCooldown,
            LocalDateTime now
    ) {
        if (optionalRecord.isEmpty()) {
            return null;
        }

        AuthRateLimit record = optionalRecord.get();

        if (windowExpired(record, now)) {
            return null;
        }

        LocalDateTime blockedUntil = null;

        if (record.getAttemptCount() >= limit) {
            blockedUntil = record.getWindowStartedAt().plus(WINDOW);
        }

        if (enforceCooldown) {
            LocalDateTime cooldownUntil =
                    record.getLastAttemptAt().plus(RESEND_COOLDOWN);

            if (now.isBefore(cooldownUntil)) {
                blockedUntil = laterOf(
                        blockedUntil,
                        cooldownUntil
                );
            }
        }

        return blockedUntil;
    }

    private AuthRateLimit preparePublicRequestRecord(
            SubjectRecord subject,
            LocalDateTime now
    ) {
        AuthRateLimit record = subject.record()
                .orElseGet(() -> newRecord(subject, now));

        if (windowExpired(record, now)) {
            record.setAttemptCount(0);
            record.setWindowStartedAt(now);
            record.setBlockedUntil(null);
            record.setPenaltyLevel(0);
        }

        record.setAttemptCount(record.getAttemptCount() + 1);
        record.setLastAttemptAt(now);

        return record;
    }

    private AuthRateLimit prepareFailureRecord(
            SubjectRecord subject,
            int threshold,
            LocalDateTime now
    ) {
        AuthRateLimit record = subject.record()
                .orElseGet(() -> newRecord(subject, now));

        if (inactivityExpired(record, now)) {
            resetFailureState(record, now);
        }

        if (mustEscalateImmediately(record, now)) {
            int nextPenaltyLevel = record.getPenaltyLevel() + 1;

            record.setPenaltyLevel(nextPenaltyLevel);
            record.setAttemptCount(
                    Math.max(record.getAttemptCount(), threshold)
            );
            record.setWindowStartedAt(now);
            record.setLastAttemptAt(now);
            record.setBlockedUntil(
                    now.plus(penaltyDuration(nextPenaltyLevel))
            );

            return record;
        }

        if (windowExpired(record, now)) {
            record.setAttemptCount(0);
            record.setWindowStartedAt(now);
        }

        record.setAttemptCount(record.getAttemptCount() + 1);
        record.setLastAttemptAt(now);

        if (record.getAttemptCount() >= threshold) {
            int firstPenaltyLevel = Math.max(
                    1,
                    record.getPenaltyLevel()
            );

            record.setPenaltyLevel(firstPenaltyLevel);
            record.setBlockedUntil(
                    now.plus(penaltyDuration(firstPenaltyLevel))
            );
        }

        return record;
    }

    private AuthRateLimit newRecord(
            SubjectRecord subject,
            LocalDateTime now
    ) {
        AuthRateLimit record = new AuthRateLimit();

        record.setAction(subject.action());
        record.setScope(subject.scope());
        record.setSubjectKey(subject.subjectKey());
        record.setAttemptCount(0);
        record.setPenaltyLevel(0);
        record.setWindowStartedAt(now);
        record.setBlockedUntil(null);
        record.setLastAttemptAt(now);
        record.setVersion(0);

        return record;
    }

    private RateLimitDecision activeBlockDecision(
            Optional<AuthRateLimit> first,
            Optional<AuthRateLimit> second,
            LocalDateTime now
    ) {
        LocalDateTime firstBlockedUntil =
                activeBlockedUntil(first, now);
        LocalDateTime secondBlockedUntil =
                activeBlockedUntil(second, now);

        LocalDateTime blockedUntil = laterOf(
                firstBlockedUntil,
                secondBlockedUntil
        );

        if (blockedUntil == null) {
            return RateLimitDecision.allowedDecision();
        }

        return RateLimitDecision.blockedUntil(blockedUntil);
    }

    private LocalDateTime activeBlockedUntil(
            Optional<AuthRateLimit> optionalRecord,
            LocalDateTime now
    ) {
        if (optionalRecord == null || optionalRecord.isEmpty()) {
            return null;
        }

        AuthRateLimit record = optionalRecord.get();
        LocalDateTime blockedUntil = record.getBlockedUntil();

        if (blockedUntil != null && now.isBefore(blockedUntil)) {
            return blockedUntil;
        }

        return null;
    }

    private RateLimitDecision decisionAfterFailure(
            AuthRateLimit first,
            AuthRateLimit second,
            LocalDateTime now
    ) {
        LocalDateTime firstBlockedUntil =
                activeBlockedUntil(Optional.of(first), now);

        LocalDateTime secondBlockedUntil = second == null
                ? null
                : activeBlockedUntil(Optional.of(second), now);

        LocalDateTime blockedUntil = laterOf(
                firstBlockedUntil,
                secondBlockedUntil
        );

        if (blockedUntil == null) {
            return RateLimitDecision.allowedDecision();
        }

        return RateLimitDecision.blockedUntil(blockedUntil);
    }

    private boolean mustEscalateImmediately(
            AuthRateLimit record,
            LocalDateTime now
    ) {
        return record.getPenaltyLevel() > 0
                && record.getBlockedUntil() != null
                && !now.isBefore(record.getBlockedUntil());
    }

    private boolean inactivityExpired(
            AuthRateLimit record,
            LocalDateTime now
    ) {
        return record.getLastAttemptAt() != null
                && !now.isBefore(
                        record.getLastAttemptAt()
                                .plus(PENALTY_RESET_AFTER)
                );
    }

    private boolean windowExpired(
            AuthRateLimit record,
            LocalDateTime now
    ) {
        return record.getWindowStartedAt() == null
                || !now.isBefore(
                        record.getWindowStartedAt().plus(WINDOW)
                );
    }

    private void resetFailureState(
            AuthRateLimit record,
            LocalDateTime now
    ) {
        record.setAttemptCount(0);
        record.setPenaltyLevel(0);
        record.setWindowStartedAt(now);
        record.setBlockedUntil(null);
    }

    private Duration penaltyDuration(int penaltyLevel) {
        long minutes = INITIAL_PENALTY.toMinutes();

        for (int level = 1; level < penaltyLevel; level++) {
            minutes = Math.min(
                    minutes * 2,
                    MAXIMUM_PENALTY.toMinutes()
            );
        }

        return Duration.ofMinutes(minutes);
    }

    private LocalDateTime laterOf(
            LocalDateTime first,
            LocalDateTime second
    ) {
        if (first == null) {
            return second;
        }

        if (second == null) {
            return first;
        }

        return first.isAfter(second) ? first : second;
    }

    private void requirePublicRequestAction(
            AuthRateLimitAction action
    ) {
        if (action != AuthRateLimitAction.REGISTER
                && action != AuthRateLimitAction.RESEND_OTP
                && action != AuthRateLimitAction.FORGOT_PASSWORD) {
            throw new IllegalArgumentException(
                    "Action does not use public-request rate limiting"
            );
        }
    }

    private void requireVerificationAction(
            AuthRateLimitAction action
    ) {
        if (action != AuthRateLimitAction.VERIFY_ACTIVATION
                && action != AuthRateLimitAction.VERIFY_RESET) {
            throw new IllegalArgumentException(
                    "Action is not an OTP verification action"
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone()
        );
    }

    private record SubjectRecord(
            AuthRateLimitAction action,
            AuthRateLimitScope scope,
            String subjectKey,
            Optional<AuthRateLimit> record
    ) {
    }
}