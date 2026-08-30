package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.domain.common.response.PageResponse;
import com.pureeats.order.dto.AdminDeliveryCollectionResponse;
import com.pureeats.order.dto.DeliveryCollectionLogResponse;
import com.pureeats.order.service.DeliveryCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin view of cash-on-delivery held by each delivery partner - ADMIN or SUPER_ADMIN only. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Delivery Collections", description = "Cash-in-hand directory - ADMIN or SUPER_ADMIN only")
public class AdminDeliveryCollectionController {

    private final DeliveryCollectionService deliveryCollectionService;

    @GetMapping("/api/v1/admin/delivery-collections")
    public ApiResponse<PageResponse<AdminDeliveryCollectionResponse>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(deliveryCollectionService.listPaged(pageable));
    }

    @GetMapping("/api/v1/admin/delivery-collections/{id}/logs")
    public ApiResponse<List<DeliveryCollectionLogResponse>> logs(@PathVariable Long id) {
        return ApiResponse.success(deliveryCollectionService.logs(id));
    }
}
