package com.pureeats.rating.dto;

public enum RateableType {
    RESTAURANT("App\\Restaurant"),
    DRIVER("App\\DeliveryGuyDetail");

    private final String legacyMorphClass;

    RateableType(String legacyMorphClass) {
        this.legacyMorphClass = legacyMorphClass;
    }

    public String legacyMorphClass() {
        return legacyMorphClass;
    }
}
