package com.pureeats.user.service;

import com.pureeats.domain.common.PiiMaskUtil;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.user.dto.AuditLogResponse;
import com.pureeats.user.dto.LoginHistoryResponse;
import com.pureeats.user.dto.OtpChallengeResponse;
import com.pureeats.user.dto.RateLimitBucketResponse;
import com.pureeats.user.dto.SecurityBlockEntryResponse;
import com.pureeats.user.dto.UserDeviceResponse;
import com.pureeats.user.dto.UserSessionResponse;
import com.pureeats.user.entity.AuditLog;
import com.pureeats.user.entity.LoginHistory;
import com.pureeats.user.entity.OtpChallenge;
import com.pureeats.user.entity.RateLimitBucket;
import com.pureeats.user.entity.SecurityBlockEntry;
import com.pureeats.user.entity.UserDevice;
import com.pureeats.user.entity.UserSession;
import com.pureeats.user.enums.BlockType;
import com.pureeats.user.repository.AuditLogRepository;
import com.pureeats.user.repository.LoginHistoryRepository;
import com.pureeats.user.repository.OtpChallengeRepository;
import com.pureeats.user.repository.RateLimitBucketRepository;
import com.pureeats.user.repository.SecurityBlockEntryRepository;
import com.pureeats.user.repository.UserDeviceRepository;
import com.pureeats.user.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only admin/audit views over the seven security-observability tables. Every list is
 * paginated (these tables are append-only and can grow large) and every mapping is deliberately
 * hand-written rather than exposing an entity - see each {@code *Response} DTO's Javadoc for what
 * is intentionally left out (OTP hashes, refresh-token hashes).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditService {

    private final AuditLogRepository auditLogRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final RateLimitBucketRepository rateLimitBucketRepository;
    private final SecurityBlockEntryRepository securityBlockEntryRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSessionRepository userSessionRepository;

    public PageResponse<AuditLogResponse> listAuditLogs(Long userId, Pageable pageable) {
        Page<AuditLog> page = userId != null
                ? auditLogRepository.findByUserId(userId, pageable)
                : auditLogRepository.findAll(pageable);
        return toPageResponse(page, AdminAuditService::toResponse);
    }

    public PageResponse<LoginHistoryResponse> listLoginHistory(Long userId, Pageable pageable) {
        Page<LoginHistory> page = userId != null
                ? loginHistoryRepository.findByUserId(userId, pageable)
                : loginHistoryRepository.findAll(pageable);
        return toPageResponse(page, AdminAuditService::toResponse);
    }

    public PageResponse<OtpChallengeResponse> listOtpChallenges(Long userId, Pageable pageable) {
        Page<OtpChallenge> page = userId != null
                ? otpChallengeRepository.findByUserId(userId, pageable)
                : otpChallengeRepository.findAll(pageable);
        return toPageResponse(page, AdminAuditService::toResponse);
    }

    public PageResponse<RateLimitBucketResponse> listRateLimitBuckets(Pageable pageable) {
        return toPageResponse(rateLimitBucketRepository.findAll(pageable), AdminAuditService::toResponse);
    }

    public PageResponse<SecurityBlockEntryResponse> listSecurityBlockEntries(BlockType blockType, Pageable pageable) {
        Page<SecurityBlockEntry> page = blockType != null
                ? securityBlockEntryRepository.findByBlockType(blockType, pageable)
                : securityBlockEntryRepository.findAll(pageable);
        return toPageResponse(page, AdminAuditService::toResponse);
    }

    public PageResponse<UserDeviceResponse> listUserDevices(Long userId, Pageable pageable) {
        Page<UserDevice> page = userId != null
                ? userDeviceRepository.findByUserId(userId, pageable)
                : userDeviceRepository.findAll(pageable);
        return toPageResponse(page, AdminAuditService::toResponse);
    }

    public PageResponse<UserSessionResponse> listUserSessions(Long userId, Pageable pageable) {
        Page<UserSession> page = userId != null
                ? userSessionRepository.findByUserId(userId, pageable)
                : userSessionRepository.findAll(pageable);
        return toPageResponse(page, AdminAuditService::toResponse);
    }

    private static AuditLogResponse toResponse(AuditLog e) {
        return new AuditLogResponse(e.getId(), e.getEventType(), e.getUserId(), e.getRequestId(),
                e.getIpAddress(), e.getDeviceId(), e.getEndpoint(), e.getHttpMethod(), e.getResult(),
                e.getFailureReason(), e.getMetadata(), e.getCreatedAt());
    }

    private static LoginHistoryResponse toResponse(LoginHistory e) {
        return new LoginHistoryResponse(e.getId(), e.getUserId(), e.getLoginMethod(), e.getStatus(),
                e.getIpAddress(), e.getDeviceId(), e.getUserAgent(), e.getCountry(), e.getRegion(), e.getCity(),
                e.getLatitude(), e.getLongitude(), e.getOccurredAt(), e.getFailureReason());
    }

    private static OtpChallengeResponse toResponse(OtpChallenge e) {
        return new OtpChallengeResponse(e.getId(), e.getChallengeId(), e.getUserId(), e.getAuthenticationMethod(),
                PiiMaskUtil.maskDestination(e.getDestination()), e.getPurpose(), e.getStatus(), e.getExpiresAt(),
                e.getAttemptCount(), e.getMaxAttempts(), e.getResendCount(), e.getMaxResendCount(),
                e.getLastSentAt(), e.getCreatedAt(), e.getUpdatedAt(), e.getVerifiedAt(),
                e.getIpAddress(), e.getDeviceId(), e.getRequestId());
    }

    private static RateLimitBucketResponse toResponse(RateLimitBucket e) {
        return new RateLimitBucketResponse(e.getId(), e.getBucketKey(), e.getWindowStart(), e.getHitCount(), e.getUpdatedAt());
    }

    private static SecurityBlockEntryResponse toResponse(SecurityBlockEntry e) {
        return new SecurityBlockEntryResponse(e.getId(), e.getBlockType(), e.getValue(), e.getReason(),
                e.getStatus(), e.getCreatedAt(), e.getExpiresAt(), e.getCreatedBy());
    }

    private static UserDeviceResponse toResponse(UserDevice e) {
        return new UserDeviceResponse(e.getId(), e.getUserId(), e.getDeviceId(), e.getDeviceType(), e.getBrowser(),
                e.getBrowserVersion(), e.getOperatingSystem(), e.getOsVersion(), e.getIpAddress(),
                e.getFirstSeenAt(), e.getLastSeenAt(), e.getLastLoginAt());
    }

    private static UserSessionResponse toResponse(UserSession e) {
        return new UserSessionResponse(e.getId(), e.getSessionId(), e.getUserId(), e.getDeviceId(),
                e.getCreatedAt(), e.getExpiresAt(), e.getLastUsedAt(), e.getRevokedAt(), e.getIpAddress(), e.getUserAgent());
    }

    private <E, R> PageResponse<R> toPageResponse(Page<E> page, java.util.function.Function<E, R> mapper) {
        return PageResponse.of(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
