# PestVisionAI 2.1

An integrated pest detection and mitigation platform that combines real-time computer vision, automated pesticide spraying, and data analytics for farmers.

## System Overview

The platform is composed of three main subsystems:

1. **Vision Service (Python)**

   - Captures live video from farm cameras.
   - Runs a convolutional neural network (CNN) to classify and locate pests.
   - Annotates the camera feed with bounding boxes and trajectory traces.
   - Streams annotated frames over HTTP for the web frontend and posts detection events to the backend.
   - Triggers spray commands on high-confidence detections.

2. **Operations Backend (Java Spring Boot)**

   - Exposes REST APIs for receiving detection events and managing spray actions.
   - Persists telemetry, detections, and spray logs in a relational database (H2 for development, PostgreSQL recommended for production).
   - Broadcasts live detections and spray events via Server-Sent Events (SSE) to the frontend.
   - Provides farmer-facing dashboards (Thymeleaf views) to review historical activity, live detections, and system health.

3. **Hardware Integration (Simulation Layer)**
   - Abstracts spray system control. In development it logs actions; in production this layer interfaces with physical actuators via Modbus, GPIO, or vendor SDKs.
   - Receives commands from the backend and acknowledges completion.

## High-Level Data Flow

```
Camera -> Python Vision Service -> (detections, frames) -> Java Backend -> Farmers' Web UI
                                              |                                   |
                                              +----> Spray Controller (actuate) <-+
```

1. Video frames are ingested by the Python vision service.
2. The CNN produces pest classifications and bounding boxes.
3. Annotated frames are streamed as MJPEG to the frontend.
4. Detection metadata is posted to the backend, persisted, and pushed to subscribed clients (UI + downstream systems).
5. The backend evaluates spray policies and triggers the spray controller. Events are logged for auditing.

## Tech Stack

- **Python 3.10+** with FastAPI, OpenCV, Torch/TensorFlow (model dependent), NumPy.
- **Java 21** with Spring Boot 3, Spring WebFlux, Spring Data JPA, H2/PostgreSQL.
- **Frontend** rendered by Spring MVC + Thymeleaf, enhanced with vanilla JS for SSE, and HTML `<img>` elements for MJPEG stream.
- **Messaging** via HTTP POST (vision -> backend) and SSE (backend -> UI).

## Repository Structure

```
PestVisionAI2.1/
├── README.md
├── python/
│   └── vision_service/
├── java/
│   └── pest-backend/
└── infra/
    └── docker/
```

## Getting Started

### Prerequisites

- Python 3.10+
- Java 21 (Temurin or OpenJDK)
- Maven 3.9+
- Node.js 18+ (optional, for asset bundling if the UI is extended)
- FFmpeg / GStreamer (optional, for advanced streaming setups)

### Setup Steps

1. **Clone models and assets**

   - Place the trained pest detection model under `python/vision_service/models/pest_model.pt` (PyTorch) or adjust the loader in `detector.py`.

2. **Install Python dependencies**

   ```bash
   cd python/vision_service
   python -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   ```

3. **Run the vision service**

   ```bash
   uvicorn app.main:app --reload
   ```

4. **Run the Java backend**

   ```bash
   cd java/pest-backend
   mvn spring-boot:run
   ```

5. **Access the farmer dashboard**

   Open `http://localhost:8080/dashboard` in a browser. The live MJPEG feed is proxied from `http://localhost:8000/video/feed`. Detection and spray events stream into the dashboard via SSE.

### Data Persistence

- Development mode uses an in-memory H2 database. Data resets on each restart.
- Production deployments should configure PostgreSQL credentials via environment variables in `application-prod.yml`.

### Spray Controller Integration

- `SprayControllerClient` currently logs actions to the backend console.
- Replace the `simulateSpray` implementation with actual actuator control (GPIO, PLC, or drone interface) as needed.

## Future Enhancements

- Replace HTTP polling between services with a message bus (e.g., Kafka) for scalability.
- Add user authentication/authorization for farmer dashboards.
- Capture and store annotated frame snapshots for audit trails.
- Expand analytics to include pest density heatmaps and seasonal trends.
- Introduce configurable spray policies (avoid over-spraying, wind compensation, safety interlocks).

## Safety and Compliance Notice

Always validate local regulations and safety protocols before automating pesticide spraying. Ensure human override mechanisms and safety interlocks are in place.
