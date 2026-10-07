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
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
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

    @Test
    void deletedAccount_isDenied_withADeletedMessage() {
        user.setAccountStatus(AccountStatus.DELETED);
        assertEquals("This account has been deleted.", guard.denialFor(7L).orElseThrow());
    }

    @Test
    void deleting_revokesEverySession() {
        user.setAccountStatus(AccountStatus.DELETED);
        guard.onAccountChanged(7L);
        verify(sessionService).revokeAllForUser(7L);
    }

    @Test
    void signOutEverywhere_revokesSessionsEvenForAnActiveAccount() {
        guard.signOutEverywhere(7L);
        verify(sessionService).revokeAllForUser(7L);
    }

    @Test
    void logOutOfAllDevices_rejectsTokensIssuedBefore_butNotNewOnes() {
        java.time.Instant before = java.time.Instant.now().minusSeconds(60);
        assertFalse(guard.isRevoked(7L, before));

        guard.signOutEverywhere(7L);

        assertTrue(guard.isRevoked(7L, before), "a token from before the sign-out is rejected");
        assertTrue(guard.isRevoked(7L, java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)), "same second counts as before");
        assertFalse(guard.isRevoked(7L, java.time.Instant.now().plusSeconds(5)), "a fresh sign-in works");
        assertFalse(guard.isRevoked(8L, before), "other users are unaffected");
    }
}
