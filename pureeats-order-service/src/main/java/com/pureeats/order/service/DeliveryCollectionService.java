package com.pureeats.order.service;

import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.domain.entity.DeliveryCollection;
import com.pureeats.domain.entity.DeliveryCollectionLog;
import com.pureeats.domain.entity.User;
import com.pureeats.order.dto.AdminDeliveryCollectionResponse;
import com.pureeats.order.dto.DeliveryCollectionLogResponse;
import com.pureeats.order.repository.DeliveryCollectionLogRepository;
import com.pureeats.order.repository.DeliveryCollectionRepository;
import com.pureeats.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Cash-in-hand held by each delivery partner, and the credit/debit log behind each balance. */
@Service
@RequiredArgsConstructor
public class DeliveryCollectionService {

    private final DeliveryCollectionRepository deliveryCollectionRepository;
    private final DeliveryCollectionLogRepository deliveryCollectionLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminDeliveryCollectionResponse> listPaged(Pageable pageable) {
        Page<DeliveryCollection> page = deliveryCollectionRepository.findAll(pageable);
        List<AdminDeliveryCollectionResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<DeliveryCollectionLogResponse> logs(Long collectionId) {
        return deliveryCollectionLogRepository.findByDeliveryCollectionIdOrderByCreatedAtDesc(collectionId.intValue()).stream()
                .map(this::toLogResponse).toList();
    }

    private AdminDeliveryCollectionResponse toResponse(DeliveryCollection c) {
        String riderName = userRepository.findById(c.getUserId().longValue()).map(User::getName).orElse("Unknown");
        return new AdminDeliveryCollectionResponse(c.getId(), c.getUserId().longValue(), riderName, c.getAmount(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private DeliveryCollectionLogResponse toLogResponse(DeliveryCollectionLog l) {
        return new DeliveryCollectionLogResponse(l.getId(), l.getDeliveryCollectionId().longValue(), l.getAmount(),
                l.getType(), l.getMessage(), l.getCreatedAt(), l.getUpdatedAt());
    }
}
