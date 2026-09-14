package com.moveiq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TripKey implements Serializable {
    @Column(name = "business_unit", nullable = false, length = 64)
    private String businessUnit;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    protected TripKey() {}

    public TripKey(String businessUnit, Long tripId) {
        this.businessUnit = Objects.requireNonNull(businessUnit);
        this.tripId = Objects.requireNonNull(tripId);
    }

    public String getBusinessUnit() { return businessUnit; }
    public Long getTripId() { return tripId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TripKey other)) return false;
        return Objects.equals(businessUnit, other.businessUnit) && Objects.equals(tripId, other.tripId);
    }

    @Override public int hashCode() { return Objects.hash(businessUnit, tripId); }
}
