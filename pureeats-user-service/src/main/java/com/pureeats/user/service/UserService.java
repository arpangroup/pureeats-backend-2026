package com.pureeats.user.service;

import com.pureeats.domain.common.exception.ResourceNotFoundException;
import com.pureeats.domain.entity.User;
import com.pureeats.user.dto.UpdateUserRequest;
import com.pureeats.user.dto.UserResponse;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = findUserOrThrow(userId);
        return UserMapper.toResponse(user, roleService.resolveRole(userId));
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
        return UserMapper.toResponse(user, roleService.resolveRole(userId));
    }

    User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
