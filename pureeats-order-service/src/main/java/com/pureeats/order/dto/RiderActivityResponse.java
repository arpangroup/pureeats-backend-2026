package com.pureeats.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/** The delivery partner's own online/offline changes and sign-ins, newest first. */
public record RiderActivityResponse(List<StatusChange> statusHistory, List<Login> loginHistory) {

    /** reason: SELF, INACTIVITY (the auto-offline scheduler) or ADMIN; null when going online. */
    public record StatusChange(boolean online, String reason, String message, LocalDateTime at) {
    }

    public record Login(LocalDateTime at, String method, String status, String device, String location) {
    }
}
