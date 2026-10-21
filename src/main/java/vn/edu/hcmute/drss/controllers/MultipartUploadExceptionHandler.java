package vn.edu.hcmute.drss.controllers;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class MultipartUploadExceptionHandler {

    public static final String SAFE_UPLOAD_LIMIT_MESSAGE =
            "T\u1ed5ng dung l\u01b0\u1ee3ng t\u1ec7p t\u1ea3i l\u00ean "
                    + "v\u01b0\u1ee3t gi\u1edbi h\u1ea1n cho ph\u00e9p. "
                    + "Vui l\u00f2ng gi\u1ea3m dung l\u01b0\u1ee3ng "
                    + "ho\u1eb7c s\u1ed1 l\u01b0\u1ee3ng t\u1ec7p.";

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(
            RedirectAttributes flash
    ) {
        flash.addFlashAttribute(
                "errorMsg",
                SAFE_UPLOAD_LIMIT_MESSAGE
        );
        return "redirect:/requests/new";
    }
}