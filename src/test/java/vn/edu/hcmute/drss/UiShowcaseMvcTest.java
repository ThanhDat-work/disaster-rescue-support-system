package vn.edu.hcmute.drss;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@NoDatabaseRepositoryMocks
class UiShowcaseMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersFirstMiddleLastAndOutOfRangePaginationPages() throws Exception {
        assertPage(null, "REQ-0001", "?filter=showcase&amp;page=1", "page-item active");
        assertPage("2", "REQ-0003", "?filter=showcase&amp;page=1", "?filter=showcase&amp;page=3");
        assertPage("4", "REQ-0005", "?filter=showcase&amp;page=3", "page-item disabled");
        assertPage("99", "REQ-0005", "?filter=showcase&amp;page=3", "page-item disabled");
        assertPage("-1", "REQ-0001", "page-item disabled", "?filter=showcase&amp;page=1");
    }

    @Test
    void statusBadgeStaysInsideTableCellAndShowcaseFormsUseDemoEndpoints() throws Exception {
        mockMvc.perform(get("/dev/ui-showcase"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<td><span class=\"badge status-badge status-verified\">VERIFIED</span></td>")))
                .andExpect(content().string(containsString("action=\"/dev/ui-showcase/demo/request\"")))
                .andExpect(content().string(containsString("action=\"/dev/ui-showcase/demo/reject\"")))
                .andExpect(content().string(not(containsString("/requests/reject/1"))))
                .andExpect(content().string(containsString("aria-label=\"Đóng\"")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    @Test
    void devSecurityChainDoesNotPermitBusinessRoutes() throws Exception {
        mockMvc.perform(get("/requests/reject/1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void demoRequestRedirectsAndShowsSuccessAlert() throws Exception {
        MvcResult postResult = mockMvc.perform(post("/dev/ui-showcase/demo/request"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dev/ui-showcase"))
                .andExpect(flash().attributeExists("successMsg"))
                .andReturn();

        mockMvc.perform(get("/dev/ui-showcase").flashAttrs(postResult.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Yêu cầu demo đã được gửi thành công.")))
                .andExpect(content().string(containsString("alert-success")));
    }

    @Test
    void modalDemoRedirectsAndErrorDemoShowsErrorAlert() throws Exception {
        mockMvc.perform(post("/dev/ui-showcase/demo/reject"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dev/ui-showcase"))
                .andExpect(flash().attributeExists("successMsg"));

        MvcResult errorResult = mockMvc.perform(post("/dev/ui-showcase/demo/error"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dev/ui-showcase"))
                .andExpect(flash().attributeExists("errorMsg"))
                .andReturn();
        mockMvc.perform(get("/dev/ui-showcase").flashAttrs(errorResult.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert-danger")))
                .andExpect(content().string(containsString("Đây là thông báo lỗi minh họa")));
    }

    private void assertPage(String page, String requestCode, String firstHref, String secondHref) throws Exception {
        MockHttpServletRequestBuilder request = get("/dev/ui-showcase");
        if (page != null) request.param("page", page);
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DRSS")))
                .andExpect(content().string(containsString(requestCode)))
                .andExpect(content().string(containsString(firstHref)))
                .andExpect(content().string(containsString(secondHref)))
                .andExpect(content().string(containsString("modal fade")))
                .andExpect(content().string(not(containsString("alert-dismissible"))))
                .andExpect(content().string(containsString("VERIFIED")));
    }
}
