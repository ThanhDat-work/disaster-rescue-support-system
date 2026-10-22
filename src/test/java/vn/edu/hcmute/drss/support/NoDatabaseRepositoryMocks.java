package vn.edu.hcmute.drss.support;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.hcmute.drss.repositories.AuthRateLimitRepository;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;
import vn.edu.hcmute.drss.repositories.MediaCleanupTaskRepository;
import vn.edu.hcmute.drss.repositories.NotificationRepository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@MockitoBean(types = {
        VerificationTokenRepository.class,
        AuthRateLimitRepository.class,
        UserRepository.class,
        RoleRepository.class,
        UserRoleRepository.class,
        RescueAssignmentRepository.class,
        RescueRequestRepository.class,
        RescueTeamMemberRepository.class,
        StatusHistoryRepository.class,
        DisasterTypeRepository.class,
        MediaAssetRepository.class,
        RescueTeamRepository.class,
        MediaCleanupTaskRepository.class,
        NotificationRepository.class
})
public @interface NoDatabaseRepositoryMocks {
}