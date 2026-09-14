package com.moveiq.api.dto;

import java.time.Instant;
import java.util.List;

public record ContextMetric(
        String metricId,
        String name,
        double value,
        String unit,
        Scope scope,
        Double baseline,
        Double delta,
        long sampleSize,
        double coveragePct,
        List<String> sourceFields,
        String methodologyVersion,
        String trustStatus,
        Instant computedAt) {
    public record Scope(String businessUnit, String office, String shift, String direction, String vendor) {}
}
