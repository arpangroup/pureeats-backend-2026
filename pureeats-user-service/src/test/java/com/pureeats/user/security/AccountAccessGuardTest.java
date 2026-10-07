package com.pureeats.user.security;

import com.pureeats.domain.entity.DeliveryGuyDetail;
import com.pureeats.domain.entity.User;
import com.pureeats.domain.enums.AccountStatus;
import com.pureeats.user.repository.DeliveryGuyDetailRepository;
import com.pureeats.user.repository.UserRepository;
import com.pureeats.user.security.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountAccessGuardTest {

    @Mock private UserRepository userRepository;
    @Mock private DeliveryGuyDetailRepository deliveryGuyDetailRepository;
    @Mock private SessionService sessionService;
    @InjectMocks private AccountAccessGuard guard;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setIsActive(User.STATUS_ACTIVE);
        user.setAccountStatus(AccountStatus.ACTIVE);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void activeUser_isAllowed() {
        assertTrue(guard.denialFor(7L).isEmpty());
    }

    @Test
    void userBlockedFromTheAdminPanel_isDenied() {
        user.setIsActive(User.STATUS_INACTIVE);
        assertTrue(guard.denialFor(7L).isPresent());
    }

    @Test
    void blockedAccountStatus_isDenied() {
        user.setAccountStatus(AccountStatus.BLOCKED);
        assertTrue(guard.denialFor(7L).isPresent());
    }

    @Test
    void deactivatedDeliveryPartner_isDenied() {
        user.setDeliveryGuyDetailId(4);
        DeliveryGuyDetail detail = new DeliveryGuyDetail();
        detail.setIsActive(false);
        when(deliveryGuyDetailRepository.findById(4L)).thenReturn(Optional.of(detail));

        assertTrue(guard.denialFor(7L).orElseThrow().contains("deactivated"));
    }

    @Test
    void blocking_takesEffectImmediately_andRevokesEverySession() {
        assertTrue(guard.denialFor(7L).isEmpty(), "cached as allowed");
        user.setIsActive(User.STATUS_INACTIVE);

        guard.onAccountChanged(7L);

        assertTrue(guard.denialFor(7L).isPresent(), "cache dropped on change");
        verify(sessionService).revokeAllForUser(7L);
    }

    @Test
    void unblocking_doesNotRevokeSessions() {
        guard.onAccountChanged(7L);
        verify(sessionService, never()).revokeAllForUser(any());
    }
}
