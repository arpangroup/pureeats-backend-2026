package com.pureeats.notification.service;

import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;

    @Transactional
    public void save(Long userId, String token) {
        PushToken pushToken = pushTokenRepository.findByToken(token).orElseGet(PushToken::new);
        pushToken.setToken(token);
        pushToken.setUserId(userId.intValue());
        pushToken.setStatus(true);
        pushToken.setIsSent(false);
        pushToken.setIsActive(true);
        pushToken.setUpdatedAt(LocalDateTime.now());
        if (pushToken.getCreatedAt() == null) {
            pushToken.setCreatedAt(LocalDateTime.now());
        }
        pushTokenRepository.save(pushToken);
    }
}
