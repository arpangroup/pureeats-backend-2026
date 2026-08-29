package com.pureeats.domain.enums;

/**
 * Canonical application roles, mirroring the legacy Spatie role names
 * (Admin, Store Owner, Delivery Guy, Customer, Employee) so existing seeded
 * role rows keep meaning. Carried as a claim inside the JWT and mapped to a
 * Spring Security {@code ROLE_*} authority via {@link #authority()}.
 */
public enum Role {
    ADMIN,
    RESTAURANT_OWNER,
    DELIVERY,
    CUSTOMER,
    EMPLOYEE;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Maps the legacy Spatie role name (as stored in the `roles` table) to this enum. */
    public static Role fromLegacyName(String legacyName) {
        if (legacyName == null) {
            return CUSTOMER;
        }
        return switch (legacyName.trim().toLowerCase()) {
            case "admin" -> ADMIN;
            case "store owner" -> RESTAURANT_OWNER;
            case "delivery guy" -> DELIVERY;
            case "employee" -> EMPLOYEE;
            default -> CUSTOMER;
        };
    }

    /** The legacy Spatie role name this enum corresponds to, for writing back to `roles`/`model_has_roles`. */
    public String legacyName() {
        return switch (this) {
            case ADMIN -> "Admin";
            case RESTAURANT_OWNER -> "Store Owner";
            case DELIVERY -> "Delivery Guy";
            case EMPLOYEE -> "Employee";
            case CUSTOMER -> "Customer";
        };
    }
}
