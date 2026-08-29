package com.pureeats.domain.enums;

public enum Role {
    SUPER_ADMIN,
    ADMIN,
    STORE_OWNER,
    DELIVERY,
    CUSTOMER,
    EMPLOYEE;

    public String authority() {
        return "ROLE_" + name();
    }

    /** {@code true} for the two roles that are provisioned out-of-band (bootstrap seeder / admin panel), never via self-registration. */
    public boolean isPrivileged() {
        return this == SUPER_ADMIN || this == ADMIN;
    }

    /** Maps the legacy Spatie role name (as stored in the `roles` table) to this enum. */
    public static Role fromLegacyName(String legacyName) {
        if (legacyName == null) {
            return CUSTOMER;
        }
        return switch (legacyName.trim().toLowerCase()) {
            case "super admin" -> SUPER_ADMIN;
            case "admin" -> ADMIN;
            case "store owner" -> STORE_OWNER;
            case "delivery guy" -> DELIVERY;
            case "employee" -> EMPLOYEE;
            default -> CUSTOMER;
        };
    }

    /** The legacy Spatie role name this enum corresponds to, for writing back to `roles`/`model_has_roles`. */
    public String legacyName() {
        return switch (this) {
            case SUPER_ADMIN -> "Super Admin";
            case ADMIN -> "Admin";
            case STORE_OWNER -> "Store Owner";
            case DELIVERY -> "Delivery Guy";
            case EMPLOYEE -> "Employee";
            case CUSTOMER -> "Customer";
        };
    }
}
