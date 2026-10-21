package vn.edu.hcmute.drss.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        value = HttpStatus.NOT_FOUND,
        reason = "Notification not found"
)
public class NotificationNotFoundException
        extends RuntimeException {

    public NotificationNotFoundException() {
        super("Không tìm thấy thông báo.");
    }
}