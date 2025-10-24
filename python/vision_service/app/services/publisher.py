from __future__ import annotations

import asyncio
import logging
from typing import Optional

import httpx

from app.core.config import get_settings
from app.schemas.detections import DetectionEnvelope

logger = logging.getLogger(__name__)


class DetectionPublisher:
    def __init__(self) -> None:
        settings = get_settings()
        self._skip_push = settings.skip_backend_push
        self._url = f"{settings.backend_base_url}{settings.backend_detection_endpoint}"
        self._client: Optional[httpx.AsyncClient] = None
        self._lock = asyncio.Lock()

    async def _get_client(self) -> httpx.AsyncClient:
        async with self._lock:
            if self._client is None:
                self._client = httpx.AsyncClient(timeout=10.0)
            return self._client

    async def publish(self, envelope: DetectionEnvelope) -> None:
        if self._skip_push:
            logger.info("Skipping backend push (PV_SKIP_BACKEND=true)")
            return
        client = await self._get_client()
        payload = envelope.model_dump(mode="json")
        try:
            response = await client.post(self._url, json=payload)
            response.raise_for_status()
        except httpx.HTTPError as exc:
            logger.error("Failed to push detection to backend: %s", exc)

    async def close(self) -> None:
        if self._client:
            await self._client.aclose()
            self._client = None
