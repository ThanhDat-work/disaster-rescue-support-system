package vn.edu.hcmute.drss.repositories;

import java.util.UUID;

public interface TeamMemberCountProjection {
    UUID getTeamId();
    long getTotal();
}