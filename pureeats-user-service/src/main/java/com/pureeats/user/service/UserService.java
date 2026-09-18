package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.user.dto.UpdateUserRequest;
import com.pureeats.user.dto.UserResponse;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String OWNER_TYPE_USER = "USER";

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final MediaAssetService mediaAssetService;
    private final MediaUrlResolver mediaUrlResolver;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = findUserOrThrow(userId);
        return UserMapper.toResponse(user, roleService.resolveRole(userId), mediaUrlResolver.resolve(user.getPhoto()));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateUserRequest request) {
        User user = findUserOrThrow(userId);
        user.setName(request.name());
        if (request.photo() != null) {
            user.setPhoto(request.photo());
        }
        if (request.dob() != null) {
            user.setDob(request.dob());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Profile updated for user {}", userId);
        return UserMapper.toResponse(user, roleService.resolveRole(userId), mediaUrlResolver.resolve(user.getPhoto()));
    }

    @Transactional
    public UserResponse updatePhoto(Long userId, MultipartFile file) {
        User user = findUserOrThrow(userId);
        String storageKey = mediaAssetService.upload(file, OWNER_TYPE_USER, userId, userId).storageKey();
        user.setPhoto(storageKey);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Profile photo updated for user {}", userId);
        return UserMapper.toResponse(user, roleService.resolveRole(userId), mediaUrlResolver.resolve(storageKey));
    }

    /**
     * Self-service soft delete - "owner validation" is implicit and total: {@code userId} always
     * comes from the caller's own JWT ({@code @AuthenticationPrincipal}), never a path parameter,
     * so there is no way to delete anyone else's account through this endpoint. Data is
     * deliberately NOT erased (per product decision - "treated as deleted", not removed); this
     * only flips {@code accountStatus}, which {@link AuthenticationService#assertAccountUsable}
     * already checks on every login-challenge/verify/refresh call, so the account is immediately
     * unable to log in again. The frontend is responsible for clearing its own local session right
     * after this call succeeds - any access token already issued stays technically valid until it
     * naturally expires or is refreshed, same as the existing BLOCKED/DISABLED accounts today.
     */
    @Transactional
    public void deleteOwnAccount(Long userId) {
        User user = findUserOrThrow(userId);
        user.setAccountStatus(AccountStatus.DELETED);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("User {} deleted their own account", userId);
    }

    User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User {} not found", userId);
                    return new ResourceNotFoundException("User not found: " + userId);
                });
    }
}
