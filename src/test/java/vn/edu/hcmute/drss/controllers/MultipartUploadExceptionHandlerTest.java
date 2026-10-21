package vn.edu.hcmute.drss.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class MultipartUploadExceptionHandlerTest {

    @Test
    void maxUploadFailureRedirectsWithSafeMessage() throws Exception {
        MockMvc mockMvc = standaloneSetup(new ThrowingUploadController())
                .setControllerAdvice(
                        new MultipartUploadExceptionHandler()
                )
                .build();

        mockMvc.perform(post("/test-upload-limit"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/new"))
                .andExpect(flash().attribute(
                        "errorMsg",
                        MultipartUploadExceptionHandler
                                .SAFE_UPLOAD_LIMIT_MESSAGE
                ));
    }

    @Controller
    static class ThrowingUploadController {

        @PostMapping("/test-upload-limit")
        String upload() {
            throw new MaxUploadSizeExceededException(1L);
        }
    }
}