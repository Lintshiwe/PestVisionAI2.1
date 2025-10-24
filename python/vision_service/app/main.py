from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.responses import JSONResponse, StreamingResponse

from app.core.config import Settings, get_settings
from app.services.pipeline import VisionPipeline

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    logger.info("Starting vision service with camera source %s", settings.camera_source)
    loop = asyncio.get_running_loop()
    pipeline = VisionPipeline(loop)
    pipeline.start()
    app.state.pipeline = pipeline
    try:
        yield
    finally:
        await pipeline.shutdown()


app = FastAPI(title="PestVisionAI Vision Service", version="2.1.0", lifespan=lifespan)


def get_pipeline() -> VisionPipeline:
    pipeline: VisionPipeline = app.state.pipeline
    return pipeline


@app.get("/")
async def root() -> dict[str, str]:
    return {
        "message": "PestVisionAI vision service running",
        "stream": "/video/feed",
        "health": "/health",
    }


@app.get("/health")
async def health(settings: Settings = Depends(get_settings)) -> dict[str, str]:
    return {"status": "ok", "service": settings.service_name}


@app.get("/video/feed")
async def video_feed(pipeline: VisionPipeline = Depends(get_pipeline)) -> StreamingResponse:
    headers = {"Cache-Control": "no-cache"}
    return StreamingResponse(
        pipeline.mjpeg_stream(),
        media_type="multipart/x-mixed-replace; boundary=frame",
        headers=headers,
    )


@app.get("/detections/latest")
async def latest_detections(pipeline: VisionPipeline = Depends(get_pipeline)) -> JSONResponse:
    boxes = await pipeline.latest_boxes()
    data = [box.model_dump() for box in boxes]
    return JSONResponse(content={"count": len(data), "boxes": data})
