package com.pureeats.catalog.dto;

/** One quick-pick delivery instruction chip (Cart page) - {@code icon} is a lucide-react icon name the client maps client-side, with a hardcoded fallback list if the admin hasn't configured any. */
public record DeliveryInstructionOptionDto(String key, String label, String icon) {
}
