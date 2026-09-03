package com.pureeats.app.report.dto;

import java.math.BigDecimal;

public record TopRiderReportRow(Long riderId, String name, Integer deliveries, BigDecimal earnings) {
}
