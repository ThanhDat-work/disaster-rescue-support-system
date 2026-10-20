package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository
        extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken>
            findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                    User user,
                    VerificationTokenPurpose purpose
            );

    List<VerificationToken> findAllByUserAndPurposeAndConsumedAtIsNull(
            User user,
            VerificationTokenPurpose purpose
    );
}