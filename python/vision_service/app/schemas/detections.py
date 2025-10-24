from __future__ import annotations

from datetime import datetime, timezone
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


def _to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(fragment.capitalize() for fragment in tail)


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, alias_generator=_to_camel)


class BoundingBox(CamelModel):
    x: int = Field(..., ge=0)
    y: int = Field(..., ge=0)
    width: int = Field(..., ge=1)
    height: int = Field(..., ge=1)
    confidence: float = Field(..., ge=0.0, le=1.0)
    label: str = Field(default="pest")
    track_id: Optional[int] = Field(default=None, description="Identifier for tracking the same pest across frames")


class DetectionEvent(CamelModel):
    frame_id: int = Field(..., ge=0)
    stream_id: str = Field(..., min_length=1)
    detected_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    pest_type: str = Field(default="unknown")
    pest_count: int = Field(..., ge=1)
    boxes: List[BoundingBox]
    max_confidence: float = Field(..., ge=0.0, le=1.0)
    snapshot_path: Optional[str] = Field(default=None)


class DetectionEnvelope(CamelModel):
    service_name: str
    payload: DetectionEvent
