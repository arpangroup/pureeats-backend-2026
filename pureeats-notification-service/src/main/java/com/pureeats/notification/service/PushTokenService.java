package com.pureeats.notification.service;

import com.pureeats.domain.entity.PushToken;
import com.pureeats.notification.enums.PushAudience;
import com.pureeats.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final FcmSender fcmSender;

    @Transactional
    public void save(Long userId, String token) {
        save(userId, token, null);
    }

    /** {@code audience}, if a recognized {@link PushAudience} name, auto-subscribes this token to that audience's standing broadcast topic (see {@link PushTopics}) - so "every customer" reach doesn't need a per-user loop later. Unrecognized/blank/null is silently a no-op, matching this field being optional on the request. */
    @Transactional
    public void save(Long userId, String token, String audience) {
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

        if (audience != null && !audience.isBlank()) {
            try {
                fcmSender.subscribeToTopic(List.of(token), PushAudience.valueOf(audience.trim().toUpperCase()).topic());
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring unrecognized push audience '{}' for user {}", audience, userId);
            }
        }
    }
}
