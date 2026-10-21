package vn.edu.hcmute.drss.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

import vn.edu.hcmute.drss.entities.DisasterType;

@Repository
public interface DisasterTypeRepository
        extends JpaRepository<DisasterType, Long> {

    List<DisasterType> findByIsActiveTrueOrderByDisasterNameAsc();

    @Query("""
            SELECT disaster
            FROM DisasterType disaster
            WHERE (
                :keyword IS NULL
                OR LOWER(disaster.disasterCode)
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(disaster.disasterName)
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            AND (
                :active IS NULL
                OR disaster.isActive = :active
            )
            """)
    Page<DisasterType> searchAdminDisasterTypes(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            Pageable pageable
    );

    boolean existsByDisasterCodeIgnoreCase(String disasterCode);

    boolean existsByDisasterCodeIgnoreCaseAndIdNot(
            String disasterCode,
            Long id
    );

    boolean existsByDisasterNameIgnoreCase(String disasterName);

    boolean existsByDisasterNameIgnoreCaseAndIdNot(
            String disasterName,
            Long id
    );
}