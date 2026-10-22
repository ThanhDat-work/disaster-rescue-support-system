package vn.edu.hcmute.drss.dtos;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class CoordinatorTrackingFilter {

    private String keyword;
    private String status;
    private String urgency;
    private Long disasterTypeId;
    private UUID teamId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}