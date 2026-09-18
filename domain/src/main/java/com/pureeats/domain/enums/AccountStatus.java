package com.pureeats.domain.enums;

public enum AccountStatus {
    ACTIVE,
    TEMPORARILY_LOCKED,
    BLOCKED,
    DISABLED,
    /** Self-service deletion (see UserService#deleteOwnAccount) - distinct from admin-imposed BLOCKED/DISABLED so the login error and any future admin-facing reporting can tell "the owner deleted this" apart from "an admin blocked this". Data is retained as-is; only login is denied. */
    DELETED
}
