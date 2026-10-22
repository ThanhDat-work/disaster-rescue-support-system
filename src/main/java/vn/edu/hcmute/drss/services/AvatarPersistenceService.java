package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.repositories.UserRepository;

import java.util.UUID;

@Service
public class AvatarPersistenceService {
    private final UserRepository users;

    public AvatarPersistenceService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public String replace(UUID userId, UploadedMedia avatar) {
        User user = lockedUser(userId);
        String previous = user.getAvatarPublicId();
        user.setAvatarPublicId(avatar.publicId());
        user.setAvatarSecureUrl(avatar.secureUrl());
        users.saveAndFlush(user);
        return previous;
    }

    @Transactional
    public String remove(UUID userId) {
        User user = lockedUser(userId);
        String previous = user.getAvatarPublicId();
        user.setAvatarPublicId(null);
        user.setAvatarSecureUrl(null);
        users.saveAndFlush(user);
        return previous;
    }

    private User lockedUser(UUID userId) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("Tài khoản không thể thay đổi avatar.");
        }
        return user;
    }
}