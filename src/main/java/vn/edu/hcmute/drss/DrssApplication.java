package vn.edu.hcmute.drss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DrssApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrssApplication.class, args);
    }
}