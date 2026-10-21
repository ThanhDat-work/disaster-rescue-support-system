package vn.edu.hcmute.drss.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CoordinatorRequestConflictException
        extends RuntimeException {

    public CoordinatorRequestConflictException(String message) {
        super(message);
    }
}