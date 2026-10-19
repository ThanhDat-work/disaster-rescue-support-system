package vn.edu.hcmute.drss.support;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.hcmute.drss.repositories.AuthRateLimitRepository;
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
        AuthRateLimitRepository.class
})
public @interface NoDatabaseRepositoryMocks {
}