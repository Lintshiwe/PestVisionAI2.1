from __future__ import annotations

import asyncio
import logging
import threading
import time
from pathlib import Path
from typing import Generator, Optional, cast

import cv2
import numpy as np
from numpy.typing import NDArray

from app.core.config import get_settings
from app.schemas.detections import BoundingBox, DetectionEnvelope, DetectionEvent
from app.services.dataset_collector import DatasetCollector
from app.services.detector import PestDetector
from app.services.publisher import DetectionPublisher
from app.services.tracker import TrackManager

logger = logging.getLogger(__name__)


class VisionPipeline:
    def __init__(self, loop: asyncio.AbstractEventLoop) -> None:
        self._settings = get_settings()
        self._loop = loop
        self._publisher = DetectionPublisher()
        self._detector = PestDetector()
        self._tracker = TrackManager()
        self._dataset_collector = DatasetCollector()
        self._excluded_labels = {label.lower() for label in self._settings.excluded_labels}
        self._capture = cv2.VideoCapture(self._settings.camera_source)
        self._capture.set(cv2.CAP_PROP_FRAME_WIDTH, self._settings.frame_width)
        self._capture.set(cv2.CAP_PROP_FRAME_HEIGHT, self._settings.frame_height)
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._latest_frame_bytes: Optional[bytes] = None
        self._latest_boxes: list[BoundingBox] = []
        self._frame_lock = threading.Lock()
        snapshot_dir = Path(self._settings.snapshot_dir)
        snapshot_dir.mkdir(parents=True, exist_ok=True)
        self._snapshot_dir = snapshot_dir

    def start(self) -> None:
        if self._running:
            return
        if not self._capture.isOpened():
            logger.warning("Unable to open camera source %s", self._settings.camera_source)
        self._running = True
        self._thread = threading.Thread(target=self._loop_frames, name="vision-pipeline", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2.0)
        self._capture.release()

    def _loop_frames(self) -> None:
        interval = max(1, self._settings.detection_interval_frames)
        frame_counter = 0
        while self._running:
            ret, frame = self._capture.read()
            if not ret:
                time.sleep(0.1)
                continue

            frame_counter += 1
            frame_matrix = cast(NDArray[np.uint8], frame)
            frame_id, boxes, metrics = self._detector.detect(frame_matrix)
            filtered_boxes = [box for box in boxes if box.label.lower() not in self._excluded_labels]
            if filtered_boxes:
                self._tracker.update_tracks(filtered_boxes)
                annotated_frame = self._tracker.draw_annotations(frame_matrix, filtered_boxes)
            else:
                annotated_frame = frame_matrix

            ok, buffer = cv2.imencode(".jpg", annotated_frame)
            if ok:
                with self._frame_lock:
                    self._latest_frame_bytes = buffer.tobytes()
                    self._latest_boxes = [box.model_copy(deep=True) for box in filtered_boxes] if filtered_boxes else []

            if filtered_boxes:
                self._dataset_collector.record(frame_matrix, frame_id, filtered_boxes)

            if filtered_boxes and frame_counter % interval == 0:
                snapshot_path = self._store_snapshot(frame_id, annotated_frame)
                envelope = DetectionEnvelope(
                    service_name=self._settings.service_name,
                    payload=DetectionEvent(
                        frame_id=frame_id,
                        stream_id=str(self._settings.camera_source),
                        pest_type="general",
                        pest_count=len(filtered_boxes),
                        boxes=filtered_boxes,
                        max_confidence=metrics.max_confidence,
                        snapshot_path=snapshot_path,
                    ),
                )
                asyncio.run_coroutine_threadsafe(self._publisher.publish(envelope), self._loop)

        logger.info("Vision pipeline stopped")

    def _store_snapshot(self, frame_id: int, frame: NDArray[np.uint8]) -> Optional[str]:
        if not self._settings.enable_telemetry_snapshots:
            return None
        filename = self._snapshot_dir / f"frame_{frame_id:07d}.jpg"
        cv2.imwrite(str(filename), frame)
        return str(filename)

    def mjpeg_stream(self) -> Generator[bytes, None, None]:
        boundary = b"--frame"
        while True:
            with self._frame_lock:
                frame_bytes = self._latest_frame_bytes
            if frame_bytes is None:
                time.sleep(0.05)
                continue
            yield boundary + b"\r\nContent-Type: image/jpeg\r\n\r\n" + frame_bytes + b"\r\n"

    async def latest_boxes(self) -> list[BoundingBox]:
        with self._frame_lock:
            return list(self._latest_boxes)

    async def shutdown(self) -> None:
        self.stop()
        await self._publisher.close()
