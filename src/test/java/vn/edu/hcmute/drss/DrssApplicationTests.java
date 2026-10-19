package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class DrssApplicationTests {
    @Test
    void contextLoads() {
    }
}