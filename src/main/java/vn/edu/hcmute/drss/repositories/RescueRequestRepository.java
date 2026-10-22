package vn.edu.hcmute.drss.repositories;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueRequest;

@Repository
public interface RescueRequestRepository
        extends JpaRepository<RescueRequest, UUID> {

    @EntityGraph(attributePaths = "disasterType")
    Page<RescueRequest> findByUser_IdOrderByCreatedAtDescIdDesc(
            UUID userId,
            Pageable pageable
    );

    boolean existsByDisplayCode(String displayCode);

    @EntityGraph(attributePaths = "disasterType")
    Optional<RescueRequest> findWithDisasterTypeById(UUID id);

    @Query(
            value = """
                    SELECT request
                    FROM RescueRequest request
                    JOIN FETCH request.user requester
                    JOIN FETCH request.disasterType disasterType
                    WHERE request.status = 'PENDING'
                      AND requester.id <> :excludedOwnerId
                      AND (
                          :keyword IS NULL
                          OR LOWER(request.displayCode)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.fullName)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.phoneNumber)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(request.address)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                      AND (
                          :urgency IS NULL
                          OR request.urgency = :urgency
                      )
                      AND (
                          :disasterTypeId IS NULL
                          OR disasterType.id = :disasterTypeId
                      )
                      AND (
                          :fromInclusive IS NULL
                          OR request.createdAt >= :fromInclusive
                      )
                      AND (
                          :toExclusive IS NULL
                          OR request.createdAt < :toExclusive
                      )
                    ORDER BY request.createdAt ASC, request.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(request)
                    FROM RescueRequest request
                    JOIN request.user requester
                    JOIN request.disasterType disasterType
                    WHERE request.status = 'PENDING'
                      AND requester.id <> :excludedOwnerId
                      AND (
                          :keyword IS NULL
                          OR LOWER(request.displayCode)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.fullName)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.phoneNumber)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(request.address)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                      AND (
                          :urgency IS NULL
                          OR request.urgency = :urgency
                      )
                      AND (
                          :disasterTypeId IS NULL
                          OR disasterType.id = :disasterTypeId
                      )
                      AND (
                          :fromInclusive IS NULL
                          OR request.createdAt >= :fromInclusive
                      )
                      AND (
                          :toExclusive IS NULL
                          OR request.createdAt < :toExclusive
                      )
                    """
    )
    Page<RescueRequest> searchPendingForCoordinator(
            @Param("keyword") String keyword,
            @Param("urgency") String urgency,
            @Param("disasterTypeId") Long disasterTypeId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("excludedOwnerId") UUID excludedOwnerId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "disasterType"})
    @Query("""
            SELECT request
            FROM RescueRequest request
            WHERE request.id = :id
              AND request.user.id <> :excludedOwnerId
            """)
    Optional<RescueRequest> findCoordinatorDetailByIdAndOwnerExcluded(
            @Param("id") UUID id,
            @Param("excludedOwnerId") UUID excludedOwnerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "disasterType"})
    @Query("""
            SELECT request
            FROM RescueRequest request
            WHERE request.id = :requestId
              AND request.user.id = :userId
            """)
    Optional<RescueRequest> findOwnedByIdForUpdate(
            @Param("requestId") UUID requestId,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "disasterType"})
    @Query("""
            SELECT request
            FROM RescueRequest request
            WHERE request.id = :id
              AND request.user.id <> :excludedOwnerId
            """)
    Optional<RescueRequest> findByIdForCoordinatorAction(
            @Param("id") UUID id,
            @Param("excludedOwnerId") UUID excludedOwnerId
    );

    @Query(
            value = """
                    SELECT DISTINCT request
                    FROM RescueRequest request
                    JOIN FETCH request.user requester
                    JOIN FETCH request.disasterType disasterType
                    LEFT JOIN RescueAssignment assignment
                           ON assignment.request = request
                    WHERE requester.id <> :excludedOwnerId
                      AND (
                          :keyword IS NULL
                          OR LOWER(request.displayCode)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.fullName)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.phoneNumber)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(request.address)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                      AND (
                          :status IS NULL
                          OR request.status = :status
                      )
                      AND (
                          :urgency IS NULL
                          OR request.urgency = :urgency
                      )
                      AND (
                          :disasterTypeId IS NULL
                          OR disasterType.id = :disasterTypeId
                      )
                      AND (
                          :teamId IS NULL
                          OR assignment.team.id = :teamId
                      )
                      AND (
                          :fromInclusive IS NULL
                          OR request.createdAt >= :fromInclusive
                      )
                      AND (
                          :toExclusive IS NULL
                          OR request.createdAt < :toExclusive
                      )
                    ORDER BY request.createdAt DESC, request.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT request)
                    FROM RescueRequest request
                    JOIN request.user requester
                    JOIN request.disasterType disasterType
                    LEFT JOIN RescueAssignment assignment
                           ON assignment.request = request
                    WHERE requester.id <> :excludedOwnerId
                      AND (
                          :keyword IS NULL
                          OR LOWER(request.displayCode)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.fullName)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(requester.phoneNumber)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(request.address)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                      AND (
                          :status IS NULL
                          OR request.status = :status
                      )
                      AND (
                          :urgency IS NULL
                          OR request.urgency = :urgency
                      )
                      AND (
                          :disasterTypeId IS NULL
                          OR disasterType.id = :disasterTypeId
                      )
                      AND (
                          :teamId IS NULL
                          OR assignment.team.id = :teamId
                      )
                      AND (
                          :fromInclusive IS NULL
                          OR request.createdAt >= :fromInclusive
                      )
                      AND (
                          :toExclusive IS NULL
                          OR request.createdAt < :toExclusive
                      )
                    """
    )
    Page<RescueRequest> searchForCoordinatorTracking(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("urgency") String urgency,
            @Param("disasterTypeId") Long disasterTypeId,
            @Param("teamId") UUID teamId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("excludedOwnerId") UUID excludedOwnerId,
            Pageable pageable
    );

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