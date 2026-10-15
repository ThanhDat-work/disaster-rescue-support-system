package vn.edu.hcmute.drss.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private RescueRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_user_id", nullable = false)
    private User uploaderUser;

    @Column(name = "public_id", nullable = false, unique = true, length = 255)
    private String publicId;

    @Column(name = "secure_url", nullable = false, length = 500)
    private String secureUrl;

    @Column(nullable = false, length = 20)
    private String format;

    private Integer width;
    private Integer height;
    private Integer bytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}