package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.drss.entities.AuthRateLimit;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.AuthRateLimitScope;
import vn.edu.hcmute.drss.repositories.AuthRateLimitRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRateLimitServiceTest {

    private static final String EMAIL =
            "citizen@example.com";
    private static final String IP_ADDRESS =
            "203.0.113.10";

    private static final String EMAIL_IP_KEY =
            "a".repeat(64);
    private static final String IP_KEY =
            "b".repeat(64);

    private final Map<RecordKey, AuthRateLimit> records =
            new HashMap<>();

    private AuthRateLimitRepository repository;
    private RateLimitSubjectKeyService subjectKeyService;
    private MutableClock clock;
    private AuthRateLimitService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuthRateLimitRepository.class);
        subjectKeyService = mock(
                RateLimitSubjectKeyService.class
        );
        clock = new MutableClock(
                Instant.parse("2026-10-19T06:00:00Z"),
                ZoneOffset.UTC
        );

        when(subjectKeyService.createEmailIpKey(
                any(),
                any(),
                any()
        )).thenReturn(EMAIL_IP_KEY);

        when(subjectKeyService.createIpKey(
                any(),
                any()
        )).thenReturn(IP_KEY);

        when(repository.findForUpdate(
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            RecordKey key = new RecordKey(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)
            );

            return Optional.ofNullable(records.get(key));
        });

        when(repository.findByActionAndScopeAndSubjectKey(
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            RecordKey key = new RecordKey(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)
            );

            return Optional.ofNullable(records.get(key));
        });

        when(repository.save(any(AuthRateLimit.class)))
                .thenAnswer(invocation -> {
                    AuthRateLimit record =
                            invocation.getArgument(0);

                    records.put(
                            keyOf(record),
                            record
                    );

                    return record;
                });

        when(repository.deleteByActionAndScopeAndSubjectKey(
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            RecordKey key = new RecordKey(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)
            );

            return records.remove(key) == null ? 0L : 1L;
        });

        service = new AuthRateLimitService(
                repository,
                subjectKeyService,
                clock
        );
    }

    @Test
    void publicRequestAllowsThreeSubjectAttemptsThenBlocksFourth() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            RateLimitDecision decision =
                    service.consumePublicRequest(
                            AuthRateLimitAction.REGISTER,
                            EMAIL,
                            IP_ADDRESS
                    );

            assertThat(decision.allowed()).isTrue();
        }

        RateLimitDecision fourth =
                service.consumePublicRequest(
                        AuthRateLimitAction.REGISTER,
                        EMAIL,
                        IP_ADDRESS
                );

        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.blockedUntil()).isEqualTo(
                LocalDateTime.of(
                        2026,
                        10,
                        19,
                        6,
                        15
                )
        );

        AuthRateLimit emailRecord = record(
                AuthRateLimitAction.REGISTER,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        AuthRateLimit ipRecord = record(
                AuthRateLimitAction.REGISTER,
                AuthRateLimitScope.IP,
                IP_KEY
        );

        assertThat(emailRecord.getAttemptCount()).isEqualTo(3);
        assertThat(ipRecord.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void resendOtpEnforcesSixtySecondCooldown() {
        RateLimitDecision first =
                service.consumePublicRequest(
                        AuthRateLimitAction.RESEND_OTP,
                        EMAIL,
                        IP_ADDRESS
                );

        clock.advance(Duration.ofSeconds(59));

        RateLimitDecision tooSoon =
                service.consumePublicRequest(
                        AuthRateLimitAction.RESEND_OTP,
                        EMAIL,
                        IP_ADDRESS
                );

        clock.advance(Duration.ofSeconds(1));

        RateLimitDecision afterCooldown =
                service.consumePublicRequest(
                        AuthRateLimitAction.RESEND_OTP,
                        EMAIL,
                        IP_ADDRESS
                );

        assertThat(first.allowed()).isTrue();
        assertThat(tooSoon.allowed()).isFalse();
        assertThat(tooSoon.blockedUntil()).isEqualTo(
                LocalDateTime.of(
                        2026,
                        10,
                        19,
                        6,
                        1
                )
        );
        assertThat(afterCooldown.allowed()).isTrue();
    }

    @Test
    void fifthLoginFailureStartsFifteenMinutePenalty() {
        for (int attempt = 1; attempt < 5; attempt++) {
            RateLimitDecision decision =
                    service.recordLoginFailure(
                            EMAIL,
                            IP_ADDRESS
                    );

            assertThat(decision.allowed()).isTrue();
        }

        RateLimitDecision fifth =
                service.recordLoginFailure(
                        EMAIL,
                        IP_ADDRESS
                );

        assertThat(fifth.allowed()).isFalse();
        assertThat(fifth.blockedUntil()).isEqualTo(
                LocalDateTime.of(
                        2026,
                        10,
                        19,
                        6,
                        15
                )
        );

        AuthRateLimit emailRecord = record(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        assertThat(emailRecord.getAttemptCount()).isEqualTo(5);
        assertThat(emailRecord.getPenaltyLevel()).isEqualTo(1);
    }

    @Test
    void requestDuringPenaltyDoesNotExtendOrMutatePenalty() {
        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordLoginFailure(EMAIL, IP_ADDRESS);
        }

        AuthRateLimit before = record(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        LocalDateTime originalBlockedUntil =
                before.getBlockedUntil();
        int originalAttemptCount = before.getAttemptCount();
        LocalDateTime originalLastAttempt =
                before.getLastAttemptAt();

        clock.advance(Duration.ofMinutes(5));

        RateLimitDecision check =
                service.checkLoginAllowed(
                        EMAIL,
                        IP_ADDRESS
                );

        RateLimitDecision rejectedFailure =
                service.recordLoginFailure(
                        EMAIL,
                        IP_ADDRESS
                );

        AuthRateLimit after = record(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        assertThat(check.allowed()).isFalse();
        assertThat(rejectedFailure.allowed()).isFalse();
        assertThat(after.getBlockedUntil())
                .isEqualTo(originalBlockedUntil);
        assertThat(after.getAttemptCount())
                .isEqualTo(originalAttemptCount);
        assertThat(after.getLastAttemptAt())
                .isEqualTo(originalLastAttempt);
    }

    @Test
    void firstFailureAfterPenaltyExpiresStartsThirtyMinutePenalty() {
        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordLoginFailure(EMAIL, IP_ADDRESS);
        }

        clock.advance(Duration.ofMinutes(15));

        assertThat(
                service.checkLoginAllowed(
                        EMAIL,
                        IP_ADDRESS
                ).allowed()
        ).isTrue();

        RateLimitDecision nextFailure =
                service.recordLoginFailure(
                        EMAIL,
                        IP_ADDRESS
                );

        AuthRateLimit emailRecord = record(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        assertThat(nextFailure.allowed()).isFalse();
        assertThat(emailRecord.getPenaltyLevel()).isEqualTo(2);
        assertThat(nextFailure.blockedUntil()).isEqualTo(
                LocalDateTime.of(
                        2026,
                        10,
                        19,
                        6,
                        45
                )
        );
    }

    @Test
    void penaltyDurationNeverExceedsTwentyFourHours() {
        AuthRateLimit emailRecord = existingRecord(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        emailRecord.setAttemptCount(5);
        emailRecord.setPenaltyLevel(8);
        emailRecord.setBlockedUntil(
                now().minusSeconds(1)
        );
        emailRecord.setLastAttemptAt(
                now().minusMinutes(1)
        );

        RateLimitDecision decision =
                service.recordLoginFailure(
                        EMAIL,
                        IP_ADDRESS
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(emailRecord.getPenaltyLevel()).isEqualTo(9);
        assertThat(emailRecord.getBlockedUntil())
                .isEqualTo(now().plusHours(24));
    }

    @Test
    void twentyFourHoursWithoutFailureResetsPenalty() {
        AuthRateLimit emailRecord = existingRecord(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        emailRecord.setAttemptCount(5);
        emailRecord.setPenaltyLevel(3);
        emailRecord.setBlockedUntil(
                now().minusHours(23)
        );
        emailRecord.setLastAttemptAt(
                now().minusHours(24)
        );

        RateLimitDecision decision =
                service.recordLoginFailure(
                        EMAIL,
                        IP_ADDRESS
                );

        assertThat(decision.allowed()).isTrue();
        assertThat(emailRecord.getAttemptCount()).isEqualTo(1);
        assertThat(emailRecord.getPenaltyLevel()).isZero();
        assertThat(emailRecord.getBlockedUntil()).isNull();
    }

    @Test
    void successfulLoginClearsOnlyEmailIpRecord() {
        existingRecord(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.EMAIL_IP,
                EMAIL_IP_KEY
        );

        existingRecord(
                AuthRateLimitAction.LOGIN,
                AuthRateLimitScope.IP,
                IP_KEY
        );

        service.clearLoginSubjectAfterSuccess(
                EMAIL,
                IP_ADDRESS
        );

        assertThat(records).doesNotContainKey(
                new RecordKey(
                        AuthRateLimitAction.LOGIN,
                        AuthRateLimitScope.EMAIL_IP,
                        EMAIL_IP_KEY
                )
        );

        assertThat(records).containsKey(
                new RecordKey(
                        AuthRateLimitAction.LOGIN,
                        AuthRateLimitScope.IP,
                        IP_KEY
                )
        );
    }

    @Test
    void verificationFailureUsesOnlyIpWideRecord() {
        for (int attempt = 1; attempt < 20; attempt++) {
            RateLimitDecision decision =
                    service.recordVerificationFailure(
                            AuthRateLimitAction.VERIFY_RESET,
                            IP_ADDRESS
                    );

            assertThat(decision.allowed()).isTrue();
        }

        RateLimitDecision twentieth =
                service.recordVerificationFailure(
                        AuthRateLimitAction.VERIFY_RESET,
                        IP_ADDRESS
                );

        assertThat(twentieth.allowed()).isFalse();

        assertThat(records).containsOnlyKeys(
                new RecordKey(
                        AuthRateLimitAction.VERIFY_RESET,
                        AuthRateLimitScope.IP,
                        IP_KEY
                )
        );
    }

    @Test
    void rejectsActionThatDoesNotBelongToRequestedPolicy() {
        assertThatThrownBy(() ->
                service.consumePublicRequest(
                        AuthRateLimitAction.LOGIN,
                        EMAIL,
                        IP_ADDRESS
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "public-request rate limiting"
                );

        assertThatThrownBy(() ->
                service.recordVerificationFailure(
                        AuthRateLimitAction.REGISTER,
                        IP_ADDRESS
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "OTP verification"
                );
    }

    private AuthRateLimit existingRecord(
            AuthRateLimitAction action,
            AuthRateLimitScope scope,
            String subjectKey
    ) {
        AuthRateLimit record = new AuthRateLimit();

        record.setAction(action);
        record.setScope(scope);
        record.setSubjectKey(subjectKey);
        record.setAttemptCount(0);
        record.setPenaltyLevel(0);
        record.setWindowStartedAt(now());
        record.setLastAttemptAt(now());
        record.setVersion(0);

        records.put(keyOf(record), record);

        return record;
    }

    private AuthRateLimit record(
            AuthRateLimitAction action,
            AuthRateLimitScope scope,
            String subjectKey
    ) {
        return records.get(
                new RecordKey(action, scope, subjectKey)
        );
    }

    private RecordKey keyOf(AuthRateLimit record) {
        return new RecordKey(
                record.getAction(),
                record.getScope(),
                record.getSubjectKey()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone()
        );
    }

    private record RecordKey(
            AuthRateLimitAction action,
            AuthRateLimitScope scope,
            String subjectKey
    ) {
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(
                Instant instant,
                ZoneId zone
        ) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}