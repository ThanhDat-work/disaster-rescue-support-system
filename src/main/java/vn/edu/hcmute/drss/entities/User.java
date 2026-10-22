package vn.edu.hcmute.drss.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "display_code", nullable = false, unique = true, length = 20)
    private String displayCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 15)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    @Column(name = "avatar_public_id", length = 255)
    private String avatarPublicId;

    @Column(name = "avatar_secure_url", length = 500)
    private String avatarSecureUrl;
}