package vn.edu.hcmute.drss.configs;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;

@Configuration
public class AuthenticationCryptoConfig {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean("jwtSecretKey")
    public SecretKey jwtSecretKey(
            AuthSecurityProperties properties
    ) {
        byte[] secretBytes = properties.jwtSecret()
                .getBytes(StandardCharsets.UTF_8);

        return new SecretKeySpec(secretBytes, HMAC_SHA_256);
    }

    @Bean
    public JwtEncoder jwtEncoder(
            @Qualifier("jwtSecretKey") SecretKey secretKey
    ) {
        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Qualifier("jwtSecretKey") SecretKey secretKey,
            AuthSecurityProperties properties
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(
                        properties.issuer()
                )
        );

        return decoder;
    }
}