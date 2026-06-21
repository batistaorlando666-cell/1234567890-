from pathlib import Path
from datetime import datetime

from flask import Flask, jsonify, request

app = Flask(__name__)
UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

@app.post("/upload")
def upload():
    image = request.files.get("image")
    if image is None:
        return jsonify(ok=False, error="missing image"), 400

    timestamp = request.form.get("timestamp", datetime.utcnow().isoformat())
    battery = request.form.get("battery", "-")
    width = request.form.get("width", "-")
    height = request.form.get("height", "-")
    jpeg_quality = request.form.get("jpegQuality", "-")

    filename = datetime.utcnow().strftime("%Y%m%d_%H%M%S_%f") + ".jpg"
    path = UPLOAD_DIR / filename
    image.save(path)

    print("received:", filename, "ts=", timestamp, "battery=", battery,
          "size=", width, "x", height, "q=", jpeg_quality)
    return jsonify(ok=True, file=str(path), timestamp=timestamp)

@app.get("/")
def index():
    latest = sorted(UPLOAD_DIR.glob("*.jpg"))[-1].name if list(UPLOAD_DIR.glob("*.jpg")) else None
    return jsonify(ok=True, latest=latest, total=len(list(UPLOAD_DIR.glob("*.jpg"))))

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, debug=True)
