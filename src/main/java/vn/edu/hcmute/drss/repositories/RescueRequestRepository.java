package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RescueRequestRepository
        extends JpaRepository<RescueRequest, UUID> {

    @Query("""
            SELECT request.status AS category,
                   COUNT(request) AS total
            FROM RescueRequest request
            WHERE request.createdAt >= :fromInclusive
              AND request.createdAt < :toExclusive
            GROUP BY request.status
            """)
    List<RequestCountProjection> countByStatusBetween(
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query("""
            SELECT request.urgency AS category,
                   COUNT(request) AS total
            FROM RescueRequest request
            WHERE request.createdAt >= :fromInclusive
              AND request.createdAt < :toExclusive
            GROUP BY request.urgency
            """)
    List<RequestCountProjection> countByUrgencyBetween(
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );
}