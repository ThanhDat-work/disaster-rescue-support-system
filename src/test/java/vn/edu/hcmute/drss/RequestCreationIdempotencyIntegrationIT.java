package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.services.CitizenCreateWorkflowService;
import vn.edu.hcmute.drss.services.RequestCreationDecision;
import vn.edu.hcmute.drss.services.RequestCreationGateService;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.media.UploadedMedia;

@SpringBootTest
@ActiveProfiles("it")
class RequestCreationIdempotencyIntegrationIT {
    private static final UUID CITIZEN = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final String HASH = "a".repeat(64);

    @Autowired private CitizenCreateWorkflowService workflow;
    @Autowired private RequestCreationGateService gate;
    @Autowired private JdbcTemplate sql;
    @MockitoBean private MediaStorage storage;

    @Test
    void simultaneousSameKeyCreatesOneRequestAndOneNotificationBatch()
            throws Exception {
        UUID key = UUID.randomUUID();
        String description = "CP02-concurrent-" + UUID.randomUUID();
        var form = form(description);
        var image = new MockMultipartFile("mediaFiles", "proof.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10});
        when(storage.upload(any())).thenReturn(new UploadedMedia(
                "drss/it/cp02-" + key, "https://example.invalid/proof.png",
                MediaResourceType.IMAGE, "image/png", "png", 1, 1, null, 8));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        UUID createdId = null;
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return workflow.create(CITIZEN, key, form, List.of(image));
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return workflow.create(CITIZEN, key, form, List.of(image));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var outcomes = List.of(first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));
            createdId = outcomes.stream()
                    .filter(result -> result.kind() == RequestCreationDecision.Kind.NEW)
                    .map(RequestCreationDecision::requestId)
                    .findFirst().orElseThrow();
            assertThat(outcomes.stream()
                    .filter(result -> result.kind() == RequestCreationDecision.Kind.NEW)
                    .count()).isEqualTo(1);
            assertThat(outcomes.stream().allMatch(result ->
                    result.kind() == RequestCreationDecision.Kind.NEW
                            || result.kind() == RequestCreationDecision.Kind.REPLAY
                            || result.kind() == RequestCreationDecision.Kind.PROCESSING)).isTrue();
            assertThat(countRequests(description)).isEqualTo(1);
            assertThat(countNotifications(createdId)).isGreaterThan(0);
            assertThat(workflow.create(CITIZEN, key, form, List.of(image)).requestId())
                    .isEqualTo(createdId);
            verify(storage, times(1)).upload(any());
            assertThat(sql.queryForObject(
                    "SELECT COUNT(*) FROM media_assets WHERE request_id = UNHEX(REPLACE(?, '-', ''))",
                    Long.class, createdId.toString())).isEqualTo(1L);
            assertThat(countRequests(description)).isEqualTo(1);
            assertThatThrownBy(() -> workflow.create(CITIZEN, key,
                    form(description + "-changed"), List.of(image)))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            start.countDown();
            executor.shutdownNow();
            cleanup(List.of(key), description, createdId);
        }
    }

    @Test
    void sixthNewKeyBlockedWhileSameKeyDoesNotConsumeAnotherSlot() {
        List<UUID> keys = new ArrayList<>();
        try {
            for (int index = 0; index < 5; index++) {
                UUID key = UUID.randomUUID();
                keys.add(key);
                assertThat(gate.claim(CITIZEN, key, HASH).kind())
                        .isEqualTo(RequestCreationDecision.Kind.NEW);
                assertThat(gate.claim(CITIZEN, key, HASH).kind())
                        .isEqualTo(RequestCreationDecision.Kind.PROCESSING);
            }
            UUID sixth = UUID.randomUUID();
            assertThat(gate.claim(CITIZEN, sixth, HASH).kind())
                    .isEqualTo(RequestCreationDecision.Kind.LIMITED);
        } finally {
            cleanup(keys, null, null);
        }
    }

    @Test
    void statusIsOwnerScopedAndFailedKeyCanRetryWithoutNewKey() {
        UUID key = UUID.randomUUID();
        try {
            assertThat(gate.status(CITIZEN, key).state()).isEqualTo("NOT_FOUND");
            assertThat(gate.claim(CITIZEN, key, HASH).kind())
                    .isEqualTo(RequestCreationDecision.Kind.NEW);
            assertThat(gate.status(CITIZEN, key).state()).isEqualTo("PROCESSING");
            UUID otherCitizen = UUID.fromString("11111111-1111-1111-1111-111111111111");
            assertThat(gate.status(otherCitizen, key).state()).isEqualTo("NOT_FOUND");
            gate.fail(CITIZEN, key);
            assertThat(gate.status(CITIZEN, key).retryAllowed()).isTrue();
            assertThat(gate.claim(CITIZEN, key, HASH).kind())
                    .isEqualTo(RequestCreationDecision.Kind.NEW);
            assertThat(gate.status(CITIZEN, key).state()).isEqualTo("PROCESSING");
            assertThatThrownBy(() -> gate.claim(CITIZEN, key, "b".repeat(64)))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            cleanup(List.of(key), null, null);
        }
    }

    private CreateCitizenRequest form(String description) {
        Long type = sql.queryForObject("SELECT MIN(id) FROM disaster_types WHERE is_active = 1",
                Long.class);
        CreateCitizenRequest form = new CreateCitizenRequest();
        form.setDisasterTypeId(type);
        form.setUrgency("HIGH");
        form.setDescription(description);
        return form;
    }

    private long countRequests(String description) {
        return sql.queryForObject(
                "SELECT COUNT(*) FROM rescue_requests WHERE description = ?",
                Long.class, description);
    }

    private long countNotifications(UUID requestId) {
        return sql.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE reference_id = UNHEX(REPLACE(?, '-', ''))",
                Long.class, requestId.toString());
    }

    private void cleanup(List<UUID> keys, String description, UUID createdId) {
        if (description != null) {
            sql.update("DELETE n FROM notifications n JOIN rescue_requests r ON n.reference_id = r.id WHERE r.description = ?",
                    description);
            sql.update("DELETE m FROM media_assets m JOIN rescue_requests r ON m.request_id = r.id WHERE r.description = ?",
                    description);
        }
        if (createdId != null) {
            sql.update("DELETE FROM notifications WHERE reference_id = UNHEX(REPLACE(?, '-', ''))",
                    createdId.toString());
        }
        for (UUID key : keys) {
            sql.update("DELETE FROM request_create_attempts WHERE user_id = UNHEX(REPLACE(?, '-', '')) AND operation_key = UNHEX(REPLACE(?, '-', ''))",
                    CITIZEN.toString(), key.toString());
        }
        if (description != null) {
            sql.update("DELETE FROM rescue_requests WHERE description = ?", description);
        }
    }
}
