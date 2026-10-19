package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class UiShowcaseProfileTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void showcaseControllerIsNotRegisteredWithoutDevProfile() {
        assertThat(applicationContext.getBeansOfType(vn.edu.hcmute.drss.controllers.UiShowcaseController.class)).isEmpty();
    }
}
