# PestVisionAI Model Training Guide

This guide walks you through updating the vision model so that it can reliably identify specific insect species from your fields.

## 1. Collect and Label Examples

1. **Capture frames**: the vision service already saves cropped detections in `python/vision_service/storage/dataset`. Let it run during different times of day and weather conditions so that lighting, crop health, and camera angles vary.
2. **Curate the crops**: remove any false positives, blurry frames, or duplicated images that would confuse the model.
3. **Create class list**: make a `classes.txt` file listing each insect you want the model to recognize (e.g. `armyworm`, `aphid`, `beetle`). The order matters—keep it consistent.
4. **Label bounding boxes**: use an annotation tool such as [Roboflow](https://roboflow.com/), [Label Studio](https://labelstud.io/), or [CVAT](https://www.cvat.ai/). Export in YOLO format (`.txt` matching each image). The model expects normalized coordinates.

> Tip: Aim for at least 200 labelled examples per class for a first pass. More data yields a more robust model.

## 2. Prepare Training Dataset

1. Split annotated images into train/val/test sets. A common ratio is 70% / 20% / 10%.
2. Mirror the structure expected by YOLOv8 (recommended):
   ```
   dataset/
     train/
       images/
       labels/
     val/
       images/
       labels/
     test/
       images/
       labels/
   dataset.yaml
   ```
3. Create `dataset.yaml` referencing the folders and the class list:
   ```yaml
   path: ./dataset
   train: train/images
   val: val/images
   test: test/images
   names: ['armyworm', 'aphid', 'beetle']
   ```

## 3. Train a Detector

You can reuse the existing Ultralytics YOLOv8 workflow:

```bash
# Inside python/vision_service (after activating your virtual environment)
pip install ultralytics
ultralytics detect train model=yolov8n.pt data=./dataset.yaml epochs=80 imgsz=1280 batch=16
```

Adjust `epochs`, `imgsz`, and `batch` to suit GPU memory. After training finishes, note the best weights path, typically `runs/detect/train/weights/best.pt`.

### Validation Checklist

- mAP50 should be >0.75 for reliable deployment.
- Review per-class precision/recall to ensure no class is under-performing.
- Visually spot check predictions on `test/` images using:
  ```bash
  ultralytics detect val model=path/to/best.pt data=./dataset.yaml save_txt=True save_conf=True
  ```

## 4. Deploy the Updated Weights

1. Copy `best.pt` into `python/vision_service/app/services/models/` (create the folder if needed).
2. Update the detector configuration in `python/vision_service/app/services/detector.py` so it loads your new weights (for example, change `model_path` to the location of `best.pt`).
3. Restart the vision service:
   ```bash
   cd python/vision_service
   uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```
4. Monitor logs for confidence values and ensure detections align with expectations.

## 5. Continual Improvement

- **Active learning**: capture frames where confidence is low or spray events did not coincide with true pests. Label and add them back into the dataset for the next training round.
- **Version control**: store `dataset.yaml`, class list, and training command parameters so future runs are reproducible.
- **Benchmark**: each time you retrain, record validation metrics in a spreadsheet to track progress.

Following this workflow will keep the PestVisionAI detector tuned to the specific insects in your fields while maintaining compatibility with the automated spray pipeline.
