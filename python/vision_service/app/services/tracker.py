from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from typing import Deque, Dict, List, Tuple

import cv2
import numpy as np
from numpy.typing import NDArray

from app.core.config import get_settings
from app.schemas.detections import BoundingBox


@dataclass
class Track:
    track_id: int
    history: Deque[Tuple[int, int]] = field(default_factory=lambda: deque(maxlen=get_settings().max_track_history))


class TrackManager:
    def __init__(self) -> None:
        self._tracks: Dict[int, Track] = {}
        self._next_id = 1
        settings = get_settings()
        self._max_history = settings.max_track_history
        self._distance_threshold = 120

    def update_tracks(self, boxes: List[BoundingBox]) -> None:
        centers = [(box.x + box.width // 2, box.y + box.height // 2) for box in boxes]

        for idx, center in enumerate(centers):
            track_id = self._match_track(center)
            if track_id is None:
                track_id = self._create_track(center)
            track = self._tracks[track_id]
            track.history.append(center)
            boxes[idx].track_id = track_id

        stale_ids = [track_id for track_id, track in self._tracks.items() if not track.history]
        for track_id in stale_ids:
            self._tracks.pop(track_id, None)

    def _match_track(self, center: Tuple[int, int]) -> int | None:
        min_distance = float("inf")
        closest_track_id = None
        for track_id, track in self._tracks.items():
            if not track.history:
                continue
            last_point = track.history[-1]
            distance = np.linalg.norm(np.array(center) - np.array(last_point))
            if distance < min_distance and distance <= self._distance_threshold:
                min_distance = distance
                closest_track_id = track_id
        return closest_track_id

    def _create_track(self, center: Tuple[int, int]) -> int:
        track_id = self._next_id
        self._next_id += 1
        history: Deque[Tuple[int, int]] = deque(maxlen=self._max_history)
        history.append(center)
        self._tracks[track_id] = Track(track_id=track_id, history=history)
        return track_id

    def draw_annotations(self, frame: NDArray[np.uint8], boxes: List[BoundingBox]) -> NDArray[np.uint8]:
        annotated = frame.copy()
        for box in boxes:
            color = (0, 255, 0)
            cv2.rectangle(annotated, (box.x, box.y), (box.x + box.width, box.y + box.height), color, 2)
            label = f"{box.label} {box.confidence:.2f}"
            cv2.putText(annotated, label, (box.x, box.y - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
            if box.track_id is not None and box.track_id in self._tracks:
                history = list(self._tracks[box.track_id].history)
                for i in range(1, len(history)):
                    cv2.line(annotated, history[i - 1], history[i], (0, 128, 255), 2)
                cv2.putText(
                    annotated,
                    f"ID {box.track_id}",
                    (box.x, box.y + box.height + 15),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.5,
                    (255, 255, 0),
                    1,
                )
        return annotated
