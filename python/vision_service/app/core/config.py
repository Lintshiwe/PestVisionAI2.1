from __future__ import annotations

import os
from functools import lru_cache
from typing import Any, Dict, Union

from pydantic import BaseModel, Field, field_validator


class Settings(BaseModel):
    """Runtime configuration for the vision service."""

    camera_source: Union[int, str] = Field(default=0, description="Camera index or stream URL")
    backend_base_url: str = Field(default="http://localhost:8080", description="Java backend origin")
    backend_detection_endpoint: str = Field(
        default="/api/detections",
        description="Endpoint receiving detection payloads",
    )
    frame_width: int = Field(default=1280, ge=320, le=3840)
    frame_height: int = Field(default=720, ge=240, le=2160)
    detection_interval_frames: int = Field(default=5, ge=1, le=30)
    confidence_threshold: float = Field(default=0.6, ge=0.0, le=1.0)
    enable_telemetry_snapshots: bool = Field(default=True)
    snapshot_dir: str = Field(default="storage/snapshots")
    enable_dataset_collection: bool = Field(default=True)
    dataset_dir: str = Field(default="storage/dataset")
    excluded_labels: list[str] = Field(default_factory=lambda: ["human", "person"])
    max_track_history: int = Field(default=30, ge=0, le=120)
    service_name: str = Field(default="vision-service")
    skip_backend_push: bool = Field(default=False, description="Disable push for offline testing")

    @field_validator("backend_base_url", mode="before")
    @classmethod
    def _trim_slash(cls, value: str) -> str:
        return value.rstrip("/")


@lru_cache
def get_settings() -> Settings:
    """Load settings from environment variables once."""

    env_map: Dict[str, Any] = {
        "camera_source": os.getenv("PV_CAMERA_SOURCE", "0"),
        "backend_base_url": os.getenv("PV_BACKEND_BASE_URL", "http://localhost:8080"),
        "backend_detection_endpoint": os.getenv("PV_BACKEND_DETECTION_ENDPOINT", "/api/detections"),
        "frame_width": int(os.getenv("PV_FRAME_WIDTH", "1280")),
        "frame_height": int(os.getenv("PV_FRAME_HEIGHT", "720")),
        "detection_interval_frames": int(os.getenv("PV_DETECTION_INTERVAL", "5")),
        "confidence_threshold": float(os.getenv("PV_CONFIDENCE_THRESHOLD", "0.6")),
        "enable_telemetry_snapshots": os.getenv("PV_ENABLE_SNAPSHOTS", "true").lower() == "true",
        "snapshot_dir": os.getenv("PV_SNAPSHOT_DIR", "storage/snapshots"),
        "enable_dataset_collection": os.getenv("PV_ENABLE_DATASET", "true").lower() == "true",
        "dataset_dir": os.getenv("PV_DATASET_DIR", "storage/dataset"),
        "excluded_labels": [label.strip().lower() for label in os.getenv("PV_EXCLUDED_LABELS", "human,person").split(",") if label.strip()],
        "max_track_history": int(os.getenv("PV_MAX_TRACK_HISTORY", "30")),
        "service_name": os.getenv("PV_SERVICE_NAME", "vision-service"),
        "skip_backend_push": os.getenv("PV_SKIP_BACKEND", "false").lower() == "true",
    }

    camera_source = env_map["camera_source"]
    if isinstance(camera_source, str) and camera_source.isdigit():
        env_map["camera_source"] = int(camera_source)

    return Settings(**env_map)
