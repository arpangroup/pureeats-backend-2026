package com.pureeats.catalog.dto;

/** Public, active-only listing — {@code code} ("COD"/"WALLET"/"UPI") is what the customer app maps to an actual checkout option; a row with no code (legacy/decorative) is display-only and never selectable. Also reused (unfiltered) for the admin listing, where {@code isActive} matters — always {@code true} on the public listing since inactive rows are already excluded there. */
public record PaymentGatewayResponse(Long id, String name, String description, String logo, String code, boolean isActive) {
}
