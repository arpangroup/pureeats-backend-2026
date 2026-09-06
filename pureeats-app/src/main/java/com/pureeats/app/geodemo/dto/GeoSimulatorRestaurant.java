package com.pureeats.app.geodemo.dto;

/** One entry in {@code GeoSimulatorController}'s fixed dummy restaurant list. */
public record GeoSimulatorRestaurant(Long id, String name, String lat, String lng) {
}
