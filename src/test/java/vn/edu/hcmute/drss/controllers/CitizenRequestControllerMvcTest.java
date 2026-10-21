package vn.edu.hcmute.drss.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.services.CitizenRequestMediaService;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CitizenRequestService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class CitizenRequestControllerMvcTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CitizenRequestService service;

    @MockitoBean
    private CitizenRequestMediaService mediaService;

    @Test
    void citizenRoutesRequireAuthenticationAndCitizenRole() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));
        mockMvc.perform(get("/requests").with(authenticatedAs("ROLE_RESCUER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsesAuthenticatedOwnerAndRequestedPage() throws Exception {
        when(service.list(USER_ID, 3))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/requests")
                        .param("page", "3")
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/citizen/request-list"))
                .andExpect(model().attributeExists("pageData"));
        verify(service).list(USER_ID, 3);
    }

    @Test
    void createFormContainsActiveTypesFromService() throws Exception {
        DisasterType type = disasterType();
        when(service.getActiveDisasterTypes()).thenReturn(List.of(type));
        mockMvc.perform(get("/requests/new")
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/citizen/request-form"))
                .andExpect(model().attribute("disasterTypes", List.of(type)))
                .andExpect(model().attributeExists("createCitizenRequest"));
    }

    @Test
    void invalidCreateRendersFormAndDoesNotCallService() throws Exception {
        when(service.getActiveDisasterTypes()).thenReturn(List.of(disasterType()));
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("urgency", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/citizen/request-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "createCitizenRequest", "disasterTypeId", "urgency"
                ));
        verify(mediaService, never()).create(eq(USER_ID), any(), any());
    }

    @Test
    void createPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .param("disasterTypeId", "7")
                        .param("urgency", "HIGH"))
                .andExpect(status().isForbidden());
        verify(mediaService, never()).create(eq(USER_ID), any(), any());
    }

    @Test
    void validCreateUsesPrincipalAndIgnoresForgedServerFields()
            throws Exception {
        RescueRequest created = request(USER_ID, "PENDING");
        when(mediaService.create(eq(USER_ID), any(), any())).thenReturn(created);
        mockMvc.perform(post("/requests")
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("disasterTypeId", "7")
                        .param("urgency", "HIGH")
                        .param("address", "123 Demo")
                        .param("userId", UUID.randomUUID().toString())
                        .param("status", "CLOSED")
                        .param("displayCode", "FORGED"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/" + created.getId()))
                .andExpect(flash().attributeExists("successMsg"));
        verify(mediaService).create(eq(USER_ID), any(), eq(List.of()));
    }

    @Test
    void detailLoadsRequestHistoryAndMediaForOwner() throws Exception {
        UUID requestId = UUID.randomUUID();
        RescueRequest request = request(USER_ID, "PENDING");
        request.setId(requestId);
        StatusHistory event = new StatusHistory();
        event.setOldStatus("PENDING");
        event.setNewStatus("VERIFIED");
        event.setCreatedAt(LocalDateTime.now());
        MediaAsset asset = new MediaAsset();
        asset.setSecureUrl("https://example.invalid/evidence.jpg");
        asset.setFormat("jpg");
        asset.setResourceType(MediaResourceType.IMAGE);
        asset.setMimeType("image/jpeg");
        when(service.detail(requestId, USER_ID)).thenReturn(request);
        when(service.history(requestId, USER_ID)).thenReturn(List.of(event));
        when(service.media(requestId, USER_ID)).thenReturn(List.of(asset));

        mockMvc.perform(get("/requests/{id}", requestId)
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/citizen/request-detail"))
                .andExpect(model().attribute("request", request))
                .andExpect(model().attribute("history", List.of(event)))
                .andExpect(model().attribute("media", List.of(asset)))
                .andExpect(model().attributeExists("cancelCitizenRequest"));
    }

    @Test
    void inaccessibleDetailUsesGenericRedirect() throws Exception {
        UUID requestId = UUID.randomUUID();
        String message = "Kh\u00f4ng t\u00ecm th\u1ea5y y\u00eau c\u1ea7u.";
        when(service.detail(requestId, USER_ID))
                .thenThrow(new IllegalArgumentException(message));
        mockMvc.perform(get("/requests/{id}", requestId)
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests"))
                .andExpect(flash().attribute("errorMsg", message));
    }

    @Test
    void cancelRequiresCsrfAndValidReason() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(post("/requests/{id}/cancel", requestId)
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .param("reason", "Resolved"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/requests/{id}/cancel", requestId)
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("reason", " "))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/" + requestId))
                .andExpect(flash().attributeExists("errorMsg"));
        verify(service, never()).cancel(any(), any(), any());
    }

    @Test
    void validCancelUsesPrincipalAndReason() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(post("/requests/{id}/cancel", requestId)
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("reason", "Situation resolved"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/" + requestId))
                .andExpect(flash().attributeExists("successMsg"));
        verify(service).cancel(requestId, USER_ID, "Situation resolved");
    }

    @Test
        void multipartCreateForwardsSelectedMedia() throws Exception {
        RescueRequest created = request(USER_ID, "PENDING");
        MockMultipartFile image = new MockMultipartFile(
                "mediaFiles",
                "evidence.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A}
        );
        when(mediaService.create(eq(USER_ID), any(), any()))
                .thenReturn(created);

        mockMvc.perform(multipart("/requests")
                        .file(image)
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("disasterTypeId", "7")
                        .param("urgency", "HIGH"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/" + created.getId()))
                .andExpect(flash().attributeExists("successMsg"));

        verify(mediaService).create(
                eq(USER_ID),
                any(),
                org.mockito.ArgumentMatchers.argThat(files ->
                        files.size() == 1
                                && "evidence.png".equals(
                                files.get(0).getOriginalFilename()
                        )
                )
        );
        }

        @Test
        void mediaStorageFailureUsesGenericClientMessage() throws Exception {
        when(mediaService.create(eq(USER_ID), any(), any()))
                .thenThrow(new MediaStorageException(
                        "internal cloud failure with implementation detail"
                ));

        mockMvc.perform(post("/requests")
                        .with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("disasterTypeId", "7")
                        .param("urgency", "HIGH"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/new"))
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Kh\u00f4ng th\u1ec3 l\u01b0u media l\u00fac n\u00e0y. "
                                + "Vui l\u00f2ng th\u1eed l\u1ea1i."
                ));
        }

    private RequestPostProcessor authenticatedAs(String role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                USER_ID, "citizen@demo.com", "Citizen Demo", 0, List.of(role)
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role))
        ));
    }

    private DisasterType disasterType() {
        DisasterType type = new DisasterType();
        type.setId(7L);
        type.setDisasterName("Flood");
        type.setIsActive(true);
        return type;
    }

    private RescueRequest request(UUID ownerId, String status) {
        User owner = new User();
        owner.setId(ownerId);
        RescueRequest request = new RescueRequest();
        request.setId(UUID.randomUUID());
        request.setDisplayCode("REQ-0123456789ABCDEF");
        request.setUser(owner);
        request.setDisasterType(disasterType());
        request.setUrgency("HIGH");
        request.setStatus(status);
        request.setAddress("123 Demo");
        request.setCreatedAt(LocalDateTime.now());
        return request;
    }
}
