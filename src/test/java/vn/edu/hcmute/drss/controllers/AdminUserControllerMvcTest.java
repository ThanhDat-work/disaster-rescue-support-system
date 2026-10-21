package vn.edu.hcmute.drss.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vn.edu.hcmute.drss.dtos.AdminUserDetail;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.AdminUserService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerMvcTest {

    @Mock
    private AdminUserService service;

    private MockMvc mockMvc;
    private UUID actorId;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                actorId,
                "admin@example.test",
                "Admin Test",
                0,
                List.of("ROLE_ADMIN"));
        authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(service.allowedRoles()).thenReturn(List.of(
                "ROLE_ADMIN",
                "ROLE_COORDINATOR",
                "ROLE_RESCUER",
                "ROLE_CITIZEN"));

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminUserController(service))
                .setValidator(validator)
                .build();
    }

    @Test
    void listAndDetailRenderSafeModels() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(service.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        when(service.getDetail(targetId)).thenReturn(detail(targetId));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/user-list"))
                .andExpect(model().attributeExists("result"));

        mockMvc.perform(get("/admin/users/{id}", targetId))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/user-detail"))
                .andExpect(model().attributeExists("user", "rolesForm"));
    }

    @Test
    void validStatusChangeUsesAuthenticatedActorAndRedirects()
            throws Exception {
        UUID targetId = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{id}/status", targetId)
                .principal(authentication)
                .param("status", "BANNED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + targetId))
                .andExpect(flash().attributeExists("successMsg"));

        verify(service).changeStatus(targetId, "BANNED", actorId);
    }

    @Test
    void emptyRoleSelectionReturnsValidationError() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(service.getDetail(targetId)).thenReturn(detail(targetId));

        mockMvc.perform(post("/admin/users/{id}/roles", targetId)
                .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/user-detail"))
                .andExpect(model().attributeHasFieldErrors(
                        "rolesForm",
                        "roles"));

        verify(service, never()).updateRoles(any(), any(), any());
    }

    @Test
    void validRoleUpdateRedirectsWithSuccessAlert() throws Exception {
        UUID targetId = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{id}/roles", targetId)
                .principal(authentication)
                .param("roles", "ROLE_CITIZEN")
                .param("roles", "ROLE_RESCUER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/" + targetId))
                .andExpect(flash().attributeExists("successMsg"));

        verify(service).updateRoles(
                targetId,
                java.util.Set.of("ROLE_CITIZEN", "ROLE_RESCUER"),
                actorId);
    }

    @Test
    void domainRoleErrorReturnsDetailWithoutLosingSelection()
            throws Exception {
        UUID targetId = UUID.randomUUID();
        when(service.getDetail(targetId)).thenReturn(detail(targetId));
        org.mockito.Mockito.doThrow(
                new AdminDomainException(
                        "roles",
                        "Không thể thu hồi vai trò cuối cùng"))
                .when(service).updateRoles(any(), any(), any());

        mockMvc.perform(post("/admin/users/{id}/roles", targetId)
                .principal(authentication)
                .param("roles", "ROLE_CITIZEN"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/user-detail"))
                .andExpect(model().attributeHasFieldErrors(
                        "rolesForm",
                        "roles"));
    }

    @Test
    void missingUserRedirectsWithSafeFlashError() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(service.getDetail(targetId)).thenThrow(
                new AdminDomainException(null, "Không tìm thấy người dùng"));

        mockMvc.perform(get("/admin/users/{id}", targetId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("errorMsg"));
    }

    private AdminUserDetail detail(UUID id) {
        return new AdminUserDetail(
                id,
                "USR-TEST",
                "Người dùng kiểm thử",
                "user@example.test",
                "0900000000",
                "ACTIVE",
                List.of("ROLE_CITIZEN"));
    }
}