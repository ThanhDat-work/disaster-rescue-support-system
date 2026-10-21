package vn.edu.hcmute.drss.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CoordinatorRequestNotFoundException extends RuntimeException {

    public CoordinatorRequestNotFoundException() {
        super("Kh\u00F4ng t\u00ECm th\u1EA5y y\u00EAu c\u1EA7u c\u1EE9u n\u1EA1n.");
    }
}
