package vn.edu.hcmute.drss;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles({"test", "dev"})
class UiShowcaseMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersFirstMiddleAndLastPaginationPagesAndShowcaseComponents() throws Exception {
        assertPage(0, "REQ-0001", "?filter=showcase&amp;page=1", "page-item disabled");
        assertPage(1, "REQ-0002", "?filter=showcase&amp;page=0", "?filter=showcase&amp;page=2");
        assertPage(2, "REQ-0003", "?filter=showcase&amp;page=1", "page-item disabled");
    }

    private void assertPage(int page, String requestCode, String firstHref, String secondHref) throws Exception {
        mockMvc.perform(get("/dev/ui-showcase").param("page", String.valueOf(page)))
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
