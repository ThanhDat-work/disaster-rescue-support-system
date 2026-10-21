package vn.edu.hcmute.drss.repositories;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.StatusHistory;

@Repository
public interface StatusHistoryRepository
        extends JpaRepository<StatusHistory, UUID> {

    @EntityGraph(attributePaths = "changedByUser")
    List<StatusHistory> findByRequest_IdOrderByCreatedAtAscIdAsc(
            UUID requestId
    );
}
