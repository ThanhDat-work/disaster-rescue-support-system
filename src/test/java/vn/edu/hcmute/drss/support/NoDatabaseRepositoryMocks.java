package vn.edu.hcmute.drss.support;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.hcmute.drss.repositories.AuthRateLimitRepository;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;

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
        RescueTeamMemberRepository.class,
        StatusHistoryRepository.class
})
public @interface NoDatabaseRepositoryMocks {
}