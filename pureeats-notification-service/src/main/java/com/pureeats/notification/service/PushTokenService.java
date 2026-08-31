package com.pureeats.notification.service;

import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;

    @Transactional
    public void save(Long userId, String token) {
        Optional<PushToken> existing = pushTokenRepository.findByToken(token);
        boolean isNew = existing.isEmpty();
        PushToken pushToken = existing.orElseGet(PushToken::new);
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
        log.info("{} push token for user {}", isNew ? "Registered new" : "Refreshed existing", userId);
    }
}
