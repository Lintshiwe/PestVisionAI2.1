from __future__ import annotations

import itertools
from dataclasses import dataclass
from typing import List, Tuple

import cv2
import numpy as np
from numpy.typing import NDArray

from app.core.config import get_settings
from app.schemas.detections import BoundingBox


@dataclass
class DetectorMetrics:
    frame_id: int
    pest_count: int
    max_confidence: float


class PestDetector:
    """Simple motion and color-based detector placeholder for a CNN."""

    def __init__(self) -> None:
        settings = get_settings()
        self._confidence_threshold = settings.confidence_threshold
        self._bg_subtractor = cv2.createBackgroundSubtractorMOG2(detectShadows=True)
        self._min_area = 600  # pixels
        self._frame_id = itertools.count()

    def detect(self, frame: NDArray[np.uint8]) -> Tuple[int, List[BoundingBox], DetectorMetrics]:
        frame_id = next(self._frame_id)
        fg_mask = self._bg_subtractor.apply(frame)
        fg_mask = cv2.GaussianBlur(fg_mask, (9, 9), 0)
        _, binary = cv2.threshold(fg_mask, 200, 255, cv2.THRESH_BINARY)
        contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        boxes: List[BoundingBox] = []
        max_confidence = 0.0
        for contour in contours:
            area = cv2.contourArea(contour)
            if area < self._min_area:
                continue

            x, y, w, h = cv2.boundingRect(contour)
            confidence = min(1.0, max(0.2, area / (self._min_area * 4)))
            max_confidence = max(max_confidence, confidence)
            if confidence < self._confidence_threshold:
                continue

            boxes.append(
                BoundingBox(
                    x=int(x),
                    y=int(y),
                    width=int(w),
                    height=int(h),
                    confidence=float(confidence),
                    label="pest",
                )
            )

        metrics = DetectorMetrics(frame_id=frame_id, pest_count=len(boxes), max_confidence=max_confidence)
        return frame_id, boxes, metrics
