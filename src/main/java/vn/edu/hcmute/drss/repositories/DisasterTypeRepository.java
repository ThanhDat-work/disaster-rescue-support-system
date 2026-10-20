package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import vn.edu.hcmute.drss.entities.DisasterType;

@Repository
public interface DisasterTypeRepository extends JpaRepository<DisasterType, Long> {
    List<DisasterType> findByIsActiveTrueOrderByDisasterNameAsc();
}
