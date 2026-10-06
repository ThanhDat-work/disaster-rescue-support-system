package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueRequest;
import java.util.UUID;

@Repository
public interface RescueRequestRepository extends JpaRepository<RescueRequest, UUID> {
    // Giao tiếp với bảng rescue_requests
}