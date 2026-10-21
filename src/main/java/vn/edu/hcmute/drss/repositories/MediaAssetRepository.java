package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.MediaAsset;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    java.util.List<MediaAsset> findByRequest_IdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID requestId);
}
