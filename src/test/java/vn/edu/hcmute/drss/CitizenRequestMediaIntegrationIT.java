package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class CitizenRequestMediaIntegrationIT {

    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final String COOKIE_NAME = "DRSS_ACCESS_TOKEN";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private MediaStorage storage;

    @Test
    void multipartCreatePersistsCloudinaryMetadataWithRequestOwner()
            throws Exception {
        UploadedMedia uploaded = uploaded("drss/it/create-success");
        when(storage.upload(any())).thenReturn(uploaded);

        mockMvc.perform(multipart("/requests")
                        .file(validPng("evidence.png"))
                        .cookie(cookie())
                        .with(csrf())
                        .param(
                                "disasterTypeId",
                                activeDisasterTypeId().toString()
                        )
                        .param("urgency", "HIGH")
                        .param("address", "T2-05 media integration success"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/requests/*"))
                .andExpect(flash().attributeExists("successMsg"));

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM media_assets media
                JOIN rescue_requests request ON request.id = media.request_id
                WHERE request.address = ?
                  AND media.public_id = ?
                  AND media.resource_type = 'IMAGE'
                  AND media.mime_type = 'image/png'
                  AND media.uploader_user_id = UNHEX(REPLACE(?, '-', ''))
                """,
                Long.class,
                "T2-05 media integration success",
                uploaded.publicId(),
                CITIZEN_ID.toString()
        );
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void secondUploadFailureDeletesFirstAndDoesNotCreateRequest()
            throws Exception {
        UploadedMedia first = uploaded("drss/it/first-upload");
        when(storage.upload(any()))
                .thenReturn(first)
                .thenThrow(new MediaStorageException("second upload failed"));

        mockMvc.perform(multipart("/requests")
                        .file(validPng("first.png"))
                        .file(validPng("second.png"))
                        .cookie(cookie())
                        .with(csrf())
                        .param(
                                "disasterTypeId",
                                activeDisasterTypeId().toString()
                        )
                        .param("urgency", "HIGH")
                        .param("address", "T2-05 partial upload failure"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/new"))
                .andExpect(flash().attributeExists("errorMsg"));

        verify(storage).delete(first.publicId(), first.resourceType());
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rescue_requests WHERE address = ?",
                Long.class,
                "T2-05 partial upload failure"
        );
        assertThat(count).isZero();
    }

        @Test
    void oversizedMediaCreatesNoRequestAndNeverUploads()
            throws Exception {
        String address = "T2-05 oversized upload rejected";

        mockMvc.perform(multipart("/requests")
                        .file(oversizedPng("too-large.png"))
                        .cookie(cookie())
                        .with(csrf())
                        .param(
                                "disasterTypeId",
                                activeDisasterTypeId().toString()
                        )
                        .param("urgency", "HIGH")
                        .param("address", address))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests/new"))
                .andExpect(flash().attribute(
                        "errorMsg",
                        org.hamcrest.Matchers.containsString("10 MiB")
                ));

        verify(storage, never()).upload(any());

        Long requestCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rescue_requests WHERE address = ?",
                Long.class,
                address
        );

        assertThat(requestCount).isZero();
    }

    private Cookie cookie() {
        User user = users.findById(CITIZEN_ID).orElseThrow();
        IssuedAccessToken token = jwtTokenService.issue(
                user,
                List.of("ROLE_CITIZEN")
        );
        return new Cookie(COOKIE_NAME, token.tokenValue());
    }

    private Long activeDisasterTypeId() {
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM disaster_types
                WHERE is_active = TRUE
                ORDER BY id
                LIMIT 1
                """,
                Long.class
        );
    }

    private MockMultipartFile validPng(String name) {
        return new MockMultipartFile(
                "mediaFiles",
                name,
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A}
        );
    }

        private MockMultipartFile oversizedPng(String name) {
        byte[] content = new byte[
                Math.toIntExact(
                        org.springframework.util.unit.DataSize
                                .ofMegabytes(10)
                                .toBytes()
                ) + 1
        ];
        content[0] = (byte) 0x89;
        content[1] = 0x50;
        content[2] = 0x4E;
        content[3] = 0x47;
        content[4] = 0x0D;
        content[5] = 0x0A;
        content[6] = 0x1A;
        content[7] = 0x0A;

        return new MockMultipartFile(
                "mediaFiles",
                name,
                "image/png",
                content
        );
    }

    private UploadedMedia uploaded(String publicId) {
        return new UploadedMedia(
                publicId,
                "https://example.invalid/" + publicId + ".png",
                MediaResourceType.IMAGE,
                "image/png",
                "png",
                320,
                240,
                null,
                1024
        );
    }
}