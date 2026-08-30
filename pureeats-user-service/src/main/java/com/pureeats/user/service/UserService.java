package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.User;
import com.pureeats.media.service.MediaAssetService;
import com.pureeats.media.storage.MediaUrlResolver;
import com.pureeats.user.dto.UpdateUserRequest;
import com.pureeats.user.dto.UserResponse;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

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
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return UserMapper.toResponse(user, roleService.resolveRole(userId), mediaUrlResolver.resolve(user.getPhoto()));
    }

    @Transactional
    public UserResponse updatePhoto(Long userId, MultipartFile file) {
        User user = findUserOrThrow(userId);
        String storageKey = mediaAssetService.upload(file, OWNER_TYPE_USER, userId, userId).storageKey();
        user.setPhoto(storageKey);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return UserMapper.toResponse(user, roleService.resolveRole(userId), mediaUrlResolver.resolve(storageKey));
    }

    User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
