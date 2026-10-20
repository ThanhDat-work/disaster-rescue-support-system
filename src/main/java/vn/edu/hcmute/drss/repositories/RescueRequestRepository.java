package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import vn.edu.hcmute.drss.entities.RescueRequest;
import java.util.UUID;

@Repository
public interface RescueRequestRepository extends JpaRepository<RescueRequest, UUID> {
    @EntityGraph(attributePaths = "disasterType")
    Page<RescueRequest> findByUser_IdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);
    boolean existsByDisplayCode(String displayCode);

    @EntityGraph(attributePaths = "disasterType")
    java.util.Optional<RescueRequest> findWithDisasterTypeById(UUID id);
    // Giao tiếp với bảng rescue_requests
}
