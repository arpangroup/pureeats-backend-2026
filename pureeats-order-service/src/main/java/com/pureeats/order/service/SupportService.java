package com.pureeats.order.service;

import com.pureeats.domain.entity.Support;
import com.pureeats.order.dto.SupportRequest;
import com.pureeats.order.dto.SupportResponse;
import com.pureeats.order.repository.SupportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportRepository supportRepository;

    @Transactional
    public SupportResponse create(Long userId, SupportRequest request) {
        Support support = new Support();
        support.setUserId(userId.intValue());
        support.setOrderId(request.orderId() != null ? request.orderId().intValue() : null);
        support.setResturantId(request.restaurantId() != null ? request.restaurantId().intValue() : null);
        support.setIssue(request.issue());
        support.setMessage(request.message());
        support.setResolved(0);
        support.setCreatedAt(LocalDateTime.now());
        support.setUpdatedAt(LocalDateTime.now());
        return toResponse(supportRepository.save(support));
    }

    @Transactional(readOnly = true)
    public List<SupportResponse> myTickets(Long userId) {
        return supportRepository.findByUserIdOrderByCreatedAtDesc(userId.intValue()).stream()
                .map(this::toResponse).toList();
    }

    private SupportResponse toResponse(Support s) {
        return new SupportResponse(s.getId(), s.getOrderId() != null ? s.getOrderId().longValue() : null,
                s.getIssue(), s.getMessage(), s.getResolved() != null && s.getResolved() == 1, s.getCreatedAt());
    }
}
