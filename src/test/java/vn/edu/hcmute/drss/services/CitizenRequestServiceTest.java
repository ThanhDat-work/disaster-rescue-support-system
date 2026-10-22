package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class CitizenRequestServiceTest {

    @Mock
    private RescueRequestRepository requests;
    @Mock
    private DisasterTypeRepository disasterTypes;
    @Mock
    private StatusHistoryRepository histories;
    @Mock
    private MediaAssetRepository media;
    @Mock
    private UserRepository users;
    @Mock
    private BusinessNotificationService notifications;

    private CitizenRequestService service;
    private UUID ownerId;
    private User owner;
    private DisasterType activeType;

    @BeforeEach
    void setUp() {
        service = new CitizenRequestService(
                requests, disasterTypes, histories, media, users, notifications
        );
        ownerId = UUID.randomUUID();
        owner = new User();
        owner.setId(ownerId);
        activeType = new DisasterType();
        activeType.setId(7L);
        activeType.setIsActive(true);
    }

    @Test
    void createUsesServerOwnerPendingStatusAndNormalizedFields() {
        allowCreate();
        CreateCitizenRequest form = validForm();
        form.setAddress("  123 Demo Street  ");
        form.setDescription("  Need assistance  ");

        RescueRequest saved = service.create(ownerId, form);

        assertThat(saved.getUser()).isSameAs(owner);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getUrgency()).isEqualTo("HIGH");
        assertThat(saved.getAddress()).isEqualTo("123 Demo Street");
        assertThat(saved.getDescription()).isEqualTo("Need assistance");
        assertThat(saved.getDisplayCode()).matches("REQ-[A-F0-9]{16}");
        verify(histories, never()).save(any());
    }

    @Test
    void createRetriesCollisionAndStopsAtBoundedFailure() {
        allowCreate();
        when(requests.existsByDisplayCode(any())).thenReturn(true, false);
        service.create(ownerId, validForm());
        verify(requests, times(2)).existsByDisplayCode(any());

        org.mockito.Mockito.reset(requests);
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(disasterTypes.findById(7L)).thenReturn(Optional.of(activeType));
        when(requests.existsByDisplayCode(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(ownerId, validForm()))
                .isInstanceOf(IllegalStateException.class);
        verify(requests, times(CitizenRequestService.DISPLAY_CODE_ATTEMPTS))
                .existsByDisplayCode(any());
        verify(requests, never()).save(any());
    }

    @Test
    void createRejectsMissingUserAndInvalidTypeOrUrgency() {
        when(users.findById(ownerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(ownerId, validForm()))
                .isInstanceOf(IllegalArgumentException.class);

        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(disasterTypes.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(ownerId, validForm()))
                .isInstanceOf(IllegalArgumentException.class);

        activeType.setIsActive(false);
        when(disasterTypes.findById(7L)).thenReturn(Optional.of(activeType));
        assertThatThrownBy(() -> service.create(ownerId, validForm()))
                .isInstanceOf(IllegalArgumentException.class);

        activeType.setIsActive(true);
        CreateCitizenRequest forged = validForm();
        forged.setUrgency("FORGED");
        assertThatThrownBy(() -> service.create(ownerId, forged))
                .isInstanceOf(IllegalArgumentException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void listClampsNegativePageAndOverflowPage() {
        Page<RescueRequest> empty = Page.empty();
        when(requests.findByUser_IdOrderByCreatedAtDescIdDesc(
                eq(ownerId), any(Pageable.class)
        )).thenReturn(empty);
        service.list(ownerId, -99);

        ArgumentCaptor<Pageable> firstCapture =
                ArgumentCaptor.forClass(Pageable.class);
        verify(requests).findByUser_IdOrderByCreatedAtDescIdDesc(
                eq(ownerId), firstCapture.capture()
        );
        assertThat(firstCapture.getValue().getPageNumber()).isZero();
        assertThat(firstCapture.getValue().getPageSize())
                .isEqualTo(CitizenRequestService.PAGE_SIZE);

        org.mockito.Mockito.reset(requests);
        Page<RescueRequest> overflow = new PageImpl<>(
                List.of(), PageRequest.of(99, 10), 21
        );
        Page<RescueRequest> last = new PageImpl<>(
                List.of(new RescueRequest()), PageRequest.of(2, 10), 21
        );
        when(requests.findByUser_IdOrderByCreatedAtDescIdDesc(
                eq(ownerId), any(Pageable.class)
        )).thenReturn(overflow, last);

        assertThat(service.list(ownerId, 99)).isSameAs(last);
        ArgumentCaptor<Pageable> overflowCapture =
                ArgumentCaptor.forClass(Pageable.class);
        verify(requests, times(2))
                .findByUser_IdOrderByCreatedAtDescIdDesc(
                        eq(ownerId), overflowCapture.capture()
                );
        assertThat(overflowCapture.getAllValues().get(1).getPageNumber())
                .isEqualTo(2);
    }

    @Test
    void detailHistoryAndMediaRequireSameOwner() {
        UUID requestId = UUID.randomUUID();
        RescueRequest request = ownedRequest(requestId, "PENDING");
        StatusHistory event = new StatusHistory();
        MediaAsset asset = new MediaAsset();
        when(requests.findWithDisasterTypeById(requestId))
                .thenReturn(Optional.of(request));
        when(histories.findByRequest_IdOrderByCreatedAtAscIdAsc(requestId))
                .thenReturn(List.of(event));
        when(media.findByRequest_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                requestId
        )).thenReturn(List.of(asset));

        assertThat(service.detail(requestId, ownerId)).isSameAs(request);
        assertThat(service.history(requestId, ownerId)).containsExactly(event);
        assertThat(service.media(requestId, ownerId)).containsExactly(asset);
    }

    @Test
    void missingAndOtherOwnerUseSameGenericMessage() {
        UUID requestId = UUID.randomUUID();
        when(requests.findWithDisasterTypeById(requestId))
                .thenReturn(Optional.empty());
        Throwable missing = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.detail(requestId, ownerId)
        );

        User other = new User();
        other.setId(UUID.randomUUID());
        RescueRequest foreign = new RescueRequest();
        foreign.setUser(other);
        when(requests.findWithDisasterTypeById(requestId))
                .thenReturn(Optional.of(foreign));
        Throwable forbidden = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.detail(requestId, ownerId)
        );

        assertThat(missing)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kh\u00f4ng t\u00ecm th\u1ea5y y\u00eau c\u1ea7u.");
        assertThat(forbidden).hasMessage(missing.getMessage());
    }

    @ParameterizedTest
    @MethodSource("cancellableStatuses")
    void cancelAllowedStateCreatesOneHistory(String status) {
        UUID requestId = UUID.randomUUID();
        RescueRequest request = ownedRequest(requestId, status);
        when(requests.findOwnedByIdForUpdate(requestId, ownerId))
                .thenReturn(Optional.of(request));

        service.cancel(requestId, ownerId, "  Situation resolved  ");

        assertThat(request.getStatus()).isEqualTo("CANCELLED");
        verify(requests).findOwnedByIdForUpdate(requestId, ownerId);
        verify(histories).save(org.mockito.ArgumentMatchers.argThat(event ->
                status.equals(event.getOldStatus())
                        && "CANCELLED".equals(event.getNewStatus())
                        && event.getChangedByUser() == owner
                        && "ROLE_CITIZEN".equals(event.getActorRole())
                        && "Situation resolved".equals(event.getNotes())
        ));
        verify(requests).flush();
        verify(notifications).notifyRequestStatusChanged(
                request,
                status,
                "CANCELLED"
        );
    }

    @ParameterizedTest
    @MethodSource("nonCancellableStatuses")
    void cancelRejectsEveryNonCancellableState(String status) {
        UUID requestId = UUID.randomUUID();
        RescueRequest request = ownedRequest(requestId, status);
        when(requests.findOwnedByIdForUpdate(requestId, ownerId))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(
                requestId, ownerId, "Reason"
        )).isInstanceOf(IllegalStateException.class);
        verify(histories, never()).save(any());
        verify(requests, never()).flush();
    }

    @Test
    void cancelRejectsBlankAndOversizedReason() {
        UUID requestId = UUID.randomUUID();
        RescueRequest request = ownedRequest(requestId, "PENDING");
        when(requests.findOwnedByIdForUpdate(requestId, ownerId))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(requestId, ownerId, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cancel(
                requestId,
                ownerId,
                "x".repeat(CitizenRequestService.MAX_CANCEL_REASON_LENGTH + 1)
        )).isInstanceOf(IllegalArgumentException.class);
        verify(histories, never()).save(any());
        verify(requests, never()).flush();
    }

    @Test
    void repeatedCancelCannotCreateDuplicateHistory() {
        UUID requestId = UUID.randomUUID();
        RescueRequest request = ownedRequest(requestId, "PENDING");
        when(requests.findOwnedByIdForUpdate(requestId, ownerId))
                .thenReturn(Optional.of(request));

        service.cancel(requestId, ownerId, "First cancellation");
        assertThatThrownBy(() -> service.cancel(
                requestId, ownerId, "Second cancellation"
        )).isInstanceOf(IllegalStateException.class);
        verify(histories, times(1)).save(any());
        verify(requests, times(1)).flush();
    }

    private void allowCreate() {
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(disasterTypes.findById(7L)).thenReturn(Optional.of(activeType));
        when(requests.existsByDisplayCode(any())).thenReturn(false);
        when(requests.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RescueRequest ownedRequest(UUID requestId, String status) {
        RescueRequest request = new RescueRequest();
        request.setId(requestId);
        request.setUser(owner);
        request.setStatus(status);
        return request;
    }

    private CreateCitizenRequest validForm() {
        CreateCitizenRequest form = new CreateCitizenRequest();
        form.setDisasterTypeId(7L);
        form.setUrgency(" high ");
        form.setAddress("123 Demo Street");
        form.setLatitude(new BigDecimal("10.762622"));
        form.setLongitude(new BigDecimal("106.660172"));
        form.setDescription("Need assistance");
        return form;
    }

    private static Stream<String> cancellableStatuses() {
        return Stream.of("PENDING", "VERIFIED");
    }

    private static Stream<String> nonCancellableStatuses() {
        return Stream.of(
                "ASSIGNED", "IN_PROGRESS", "RESCUED",
                "CLOSED", "REJECTED", "CANCELLED"
        );
    }
}