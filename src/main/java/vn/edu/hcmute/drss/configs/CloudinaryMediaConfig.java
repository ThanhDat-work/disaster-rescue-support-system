package vn.edu.hcmute.drss.configs;

import com.cloudinary.Cloudinary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.hcmute.drss.media.CloudinaryGateway;
import vn.edu.hcmute.drss.media.CloudinaryMediaStorage;
import vn.edu.hcmute.drss.media.CloudinarySdkGateway;
import vn.edu.hcmute.drss.media.MediaStorage;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class CloudinaryMediaConfig {

    @Bean
    Cloudinary cloudinary(MediaStorageProperties properties) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("cloud_name", properties.cloudName());
        configuration.put("api_key", properties.apiKey());
        configuration.put("api_secret", properties.apiSecret());
        configuration.put("secure", true);
        return new Cloudinary(configuration);
    }

    @Bean
    CloudinaryGateway cloudinaryGateway(Cloudinary cloudinary) {
        return new CloudinarySdkGateway(cloudinary);
    }

    @Bean
    MediaStorage mediaStorage(
            CloudinaryGateway gateway,
            MediaStorageProperties properties
    ) {
        return new CloudinaryMediaStorage(gateway, properties);
    }
}