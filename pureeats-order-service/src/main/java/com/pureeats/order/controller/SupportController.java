package com.pureeats.order.controller;

import com.pureeats.domain.common.response.ApiResponse;
import com.pureeats.order.dto.SupportRequest;
import com.pureeats.order.dto.SupportResponse;
import com.pureeats.order.service.SupportService;
import com.pureeats.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "Support", description = "Customer support tickets")
@SecurityRequirement(name = "bearerAuth")
public class SupportController {

    private final SupportService supportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a support ticket")
    public ApiResponse<SupportResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody SupportRequest request) {
        return ApiResponse.success("Support ticket created", supportService.create(principal.userId(), request));
    }

    @GetMapping
    @Operation(summary = "List the signed-in user's support tickets")
    public ApiResponse<List<SupportResponse>> myTickets(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(supportService.myTickets(principal.userId()));
    }
}
