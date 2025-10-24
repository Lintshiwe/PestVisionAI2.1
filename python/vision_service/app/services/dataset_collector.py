from __future__ import annotations

import csv
import logging
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

import cv2
import numpy as np
from numpy.typing import NDArray

from app.core.config import get_settings
from app.schemas.detections import BoundingBox

logger = logging.getLogger(__name__)


class DatasetCollector:
    """Persists detection crops for future training."""

    def __init__(self) -> None:
        settings = get_settings()
        self._enabled = settings.enable_dataset_collection
        self._dataset_dir = Path(settings.dataset_dir)
        self._excluded_labels = {label.lower() for label in settings.excluded_labels}
        self._metadata_path = self._dataset_dir / "metadata.csv"
        self._lock = threading.Lock()

        if not self._enabled:
            logger.info("Dataset collection disabled via configuration")
            return

        self._dataset_dir.mkdir(parents=True, exist_ok=True)
        if not self._metadata_path.exists():
            with self._metadata_path.open("w", newline="", encoding="utf-8") as handle:
                writer = csv.writer(handle)
                writer.writerow(
                    [
                        "timestamp",
                        "frame_id",
                        "label",
                        "confidence",
                        "track_id",
                        "image_path",
                    ]
                )

    def record(self, frame: NDArray[np.uint8], frame_id: int, boxes: Iterable[BoundingBox]) -> None:
        if not self._enabled:
            return

        height, width = frame.shape[:2]
        timestamp = datetime.now(timezone.utc)

        rows: list[list[str | int | float | None]] = []
        for box in boxes:
            label = box.label.lower()
            if label in self._excluded_labels:
                continue

            x1 = max(0, int(box.x))
            y1 = max(0, int(box.y))
            x2 = min(width, x1 + int(box.width))
            y2 = min(height, y1 + int(box.height))

            if x2 <= x1 or y2 <= y1:
                logger.debug("Skipping invalid crop for frame %s", frame_id)
                continue

            crop = frame[y1:y2, x1:x2]
            if crop.size == 0:
                logger.debug("Empty crop encountered for frame %s", frame_id)
                continue

            filename = f"{timestamp.strftime('%Y%m%dT%H%M%S%f')}_{uuid.uuid4().hex[:8]}_{label}.jpg"
            output_path = self._dataset_dir / filename
            cv2.imwrite(str(output_path), crop)

            rows.append(
                [
                    timestamp.isoformat(),
                    frame_id,
                    label,
                    round(float(box.confidence), 3),
                    box.track_id,
                    str(output_path),
                ]
            )

        if rows:
            with self._lock:
                with self._metadata_path.open("a", newline="", encoding="utf-8") as handle:
                    writer = csv.writer(handle)
                    writer.writerows(rows)

            logger.debug("Persisted %d dataset crops for frame %s", len(rows), frame_id)
