package vn.edu.hcmute.drss.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.services.AdminDisasterTypeService;
import vn.edu.hcmute.drss.services.AdminRescueTeamService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
class AdminCatalogControllerMvcTest {

    @Mock
    private AdminDisasterTypeService disasterService;

    @Mock
    private AdminRescueTeamService teamService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminDisasterTypeController(disasterService),
                        new AdminRescueTeamController(teamService)
                )
                .setValidator(validator)
                .build();
    }

    @Test
    void listsDisastersAndTeams() throws Exception {
        when(disasterService.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        when(teamService.search(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/disasters"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/disaster-list"))
                .andExpect(model().attributeExists("result"));

        mockMvc.perform(get("/admin/teams"))
                .andExpect(status().isOk())
.andExpect(view().name("pages/admin/team-list"))
                .andExpect(model().attributeExists("result"));
    }

    @Test
    void invalidDisasterFormDoesNotCallService() throws Exception {
        mockMvc.perform(post("/admin/disasters")
                        .param("code", "")
                        .param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/disaster-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "form",
                        "code",
                        "name"
                ));

        verify(disasterService, never()).create(any());
    }

    @Test
    void validDisasterCreateRedirects() throws Exception {
        mockMvc.perform(post("/admin/disasters")
                        .param("code", "DIS-010")
                        .param("name", "Lốc xoáy")
                        .param("description", "Gió xoáy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/disasters"))
                .andExpect(flash().attributeExists("successMsg"));

        verify(disasterService).create(any());
    }

    @Test
    void domainFieldErrorReturnsDisasterForm() throws Exception {
        when(disasterService.create(any())).thenThrow(
                new AdminDomainException("code", "Mã đã tồn tại")
        );

        mockMvc.perform(post("/admin/disasters")
                        .param("code", "DIS-010")
                        .param("name", "Lốc xoáy"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/disaster-form"))
                .andExpect(model().attributeHasFieldErrors("form", "code"));
    }

    @Test
    void invalidTeamFormDoesNotCallService() throws Exception {
        mockMvc.perform(post("/admin/teams")
                        .param("code", "TEAM-010")
                        .param("name", "Đội 10")
                        .param("leaderName", "Đội trưởng")
                        .param("contactNumber", "invalid")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/admin/team-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "form",
                        "contactNumber",
                        "status"
                ));

        verify(teamService, never()).create(any());
    }

    @Test
    void validTeamCreateAndStatusChangeRedirect() throws Exception {
        mockMvc.perform(post("/admin/teams")
                        .param("code", "TEAM-010")
                        .param("name", "Đội 10")
                        .param("leaderName", "Đội trưởng")
                        .param("contactNumber", "0912345678")
                        .param("status", "AVAILABLE"))
                .andExpect(status().is3xxRedirection())
.andExpect(redirectedUrl("/admin/teams"));

        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/admin/teams/{id}/status", id)
                        .param("status", "OFFLINE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/teams"));

        verify(teamService).create(any());
        verify(teamService).changeStatus(id, "OFFLINE");
    }

    @Test
    void missingEntitiesRedirectWithSafeError() throws Exception {
        when(disasterService.get(anyLong())).thenThrow(
                new AdminDomainException(null, "Không tìm thấy")
        );
        when(teamService.get(any())).thenThrow(
                new AdminDomainException(null, "Không tìm thấy")
        );

        mockMvc.perform(get("/admin/disasters/999/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/disasters"))
                .andExpect(flash().attributeExists("errorMsg"));

        mockMvc.perform(get(
                        "/admin/teams/{id}/edit",
                        UUID.randomUUID()
                ))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/teams"))
                .andExpect(flash().attributeExists("errorMsg"));
    }
}
