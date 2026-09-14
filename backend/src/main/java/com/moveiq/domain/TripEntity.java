package com.moveiq.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "trip", schema = "moveiq")
public class TripEntity {
    @EmbeddedId
    private TripKey id;

    @Column(name = "trip_date", nullable = false)
    private LocalDate tripDate;
    private String office;
    @Column(name = "shift_type") private String shiftType;
    @Column(name = "trip_direction") private String tripDirection;
    @Column(name = "vendor_id") private String vendorId;
    @Column(name = "planned_start_epoch") private Long plannedStartEpoch;
    @Column(name = "planned_end_epoch") private Long plannedEndEpoch;
    @Column(name = "actual_start_epoch") private Long actualStartEpoch;
    @Column(name = "actual_end_epoch") private Long actualEndEpoch;
    @Column(name = "reported_delay_minutes") private Integer reportedDelayMinutes;
    @Column(name = "planned_employee_cnt") private Integer plannedEmployeeCount;
    @Column(name = "actual_employee_cnt") private Integer actualEmployeeCount;
    @Column(name = "noshow_cnt") private Integer noShowCount;
    @Version private Long version;

    protected TripEntity() {}

    public TripEntity(TripKey id, LocalDate tripDate) { this.id = id; this.tripDate = tripDate; }

    public TripKey getId() { return id; }
    public LocalDate getTripDate() { return tripDate; }
    public String getOffice() { return office; }
    public String getShiftType() { return shiftType; }
    public String getTripDirection() { return tripDirection; }
    public String getVendorId() { return vendorId; }
    public Long getPlannedEndEpoch() { return plannedEndEpoch; }
    public Long getActualEndEpoch() { return actualEndEpoch; }
    public Integer getReportedDelayMinutes() { return reportedDelayMinutes; }

    public Long timestampDelayMinutes() {
        if (plannedEndEpoch == null || actualEndEpoch == null) return null;
        return Math.max(0L, (actualEndEpoch - plannedEndEpoch) / 60L);
    }
}
