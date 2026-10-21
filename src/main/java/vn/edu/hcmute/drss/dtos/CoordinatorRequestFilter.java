package vn.edu.hcmute.drss.dtos;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class CoordinatorRequestFilter {

    private String keyword;
    private String urgency;
    private Long disasterTypeId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public Long getDisasterTypeId() { return disasterTypeId; }
    public void setDisasterTypeId(Long disasterTypeId) { this.disasterTypeId = disasterTypeId; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
}
