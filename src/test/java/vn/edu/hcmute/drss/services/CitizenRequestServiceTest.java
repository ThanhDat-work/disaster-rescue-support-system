package vn.edu.hcmute.drss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class CitizenRequestServiceTest {
    @Mock RescueRequestRepository requests;
    @Mock DisasterTypeRepository types;
    @Mock StatusHistoryRepository histories;
    @Mock MediaAssetRepository media;
    @Mock UserRepository users;
    CitizenRequestService service;
    UUID ownerId;
    DisasterType activeType;

    @BeforeEach void setUp() {
        service = new CitizenRequestService(requests, types, histories, media, users);
        ownerId = UUID.randomUUID();
        activeType = new DisasterType(); activeType.setId(7L); activeType.setIsActive(true);
    }

    @Test void createUsesServerOwnerPendingAndUniqueGeneratedCode() {
        User owner = new User(); owner.setId(ownerId);
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(types.findById(7L)).thenReturn(Optional.of(activeType));
        when(requests.existsByDisplayCode(any())).thenReturn(false);
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RescueRequest saved = service.create(ownerId, validForm());
        assertSame(owner, saved.getUser());
        assertEquals("PENDING", saved.getStatus());
        assertTrue(saved.getDisplayCode().matches("REQ-[A-F0-9]{16}"));
        assertEquals("HIGH", saved.getUrgency());
        verify(histories, never()).save(any());
    }

    @Test void createRejectsInactiveDisasterType() {
        activeType.setIsActive(false);
        when(users.findById(ownerId)).thenReturn(Optional.of(new User()));
        when(types.findById(7L)).thenReturn(Optional.of(activeType));
        assertThrows(IllegalArgumentException.class, () -> service.create(ownerId, validForm()));
        verify(requests, never()).save(any());
    }

    @Test void cancelCreatesSingleHistoryWithOwnerAndReason() {
        UUID requestId = UUID.randomUUID();
        User owner = new User(); owner.setId(ownerId);
        RescueRequest request = new RescueRequest(); request.setId(requestId); request.setUser(owner); request.setStatus("PENDING");
        when(requests.findWithDisasterTypeById(requestId)).thenReturn(Optional.of(request));
        service.cancel(requestId, ownerId, "Duplicate request");
        assertEquals("CANCELLED", request.getStatus());
        verify(histories).save(argThat(history -> "PENDING".equals(history.getOldStatus())
                && "CANCELLED".equals(history.getNewStatus())
                && history.getChangedByUser() == owner
                && "Duplicate request".equals(history.getNotes())));
        verify(requests).flush();
    }

    @Test void ownerCanCancelVerifiedRequestAndHistoryUsesActualOldState() {
        UUID requestId = UUID.randomUUID();
        User owner = new User(); owner.setId(ownerId);
        RescueRequest request = new RescueRequest(); request.setId(requestId); request.setUser(owner); request.setStatus("VERIFIED");
        when(requests.findWithDisasterTypeById(requestId)).thenReturn(Optional.of(request));
        service.cancel(requestId, ownerId, "Situation resolved");
        assertEquals("CANCELLED", request.getStatus());
        verify(histories).save(argThat(history -> "VERIFIED".equals(history.getOldStatus())
                && "CANCELLED".equals(history.getNewStatus())
                && history.getChangedByUser() == owner));
    }

    @Test void cancelRejectsOtherOwnersAndNonPendingStates() {
        UUID requestId = UUID.randomUUID();
        User other = new User(); other.setId(UUID.randomUUID());
        RescueRequest request = new RescueRequest(); request.setId(requestId); request.setUser(other); request.setStatus("PENDING");
        when(requests.findWithDisasterTypeById(requestId)).thenReturn(Optional.of(request));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(requestId, ownerId, "Reason"));
        request.getUser().setId(ownerId); request.setStatus("ASSIGNED");
        assertThrows(IllegalStateException.class, () -> service.cancel(requestId, ownerId, "Reason"));
        verify(histories, never()).save(any());
    }

    private CreateCitizenRequest validForm() {
        CreateCitizenRequest form = new CreateCitizenRequest();
        form.setDisasterTypeId(7L); form.setUrgency(" high "); form.setAddress("123 Demo Street");
        form.setLatitude(new BigDecimal("10.762622")); form.setLongitude(new BigDecimal("106.660172"));
        form.setDescription("Need assistance");
        return form;
    }
}
