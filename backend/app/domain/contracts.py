from datetime import datetime
from enum import Enum
from pydantic import BaseModel, Field

class TrustStatus(str, Enum):
    TRUSTED="TRUSTED"; PARTIAL="PARTIAL"; LOW_COVERAGE="LOW_COVERAGE"; QUARANTINED="QUARANTINED"; UNKNOWN="UNKNOWN"

class TripKey(BaseModel):
    business_unit: str
    trip_id: int
    @property
    def canonical(self) -> str:
        return f"{self.business_unit}:{self.trip_id}"

class MetricScope(BaseModel):
    business_unit: str
    office: str | None = None
    shift: str | None = None
    direction: str | None = None
    vendor: str | None = None

class ContextMetric(BaseModel):
    metric_id: str
    metric_name: str
    value: float | None = None
    unit: str
    scope: MetricScope
    comparison_value: float | None = None
    delta: float | None = None
    sample_size: int = 0
    coverage_pct: float | None = Field(default=None, ge=0, le=100)
    source_tables: list[str] = Field(default_factory=list)
    source_fields: list[str] = Field(default_factory=list)
    methodology_version: str
    trust_status: TrustStatus = TrustStatus.UNKNOWN
    confidence: float | None = Field(default=None, ge=0, le=1)
    computed_at: datetime = Field(default_factory=datetime.utcnow)
