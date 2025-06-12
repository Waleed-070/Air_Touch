from fastapi import FastAPI, UploadFile, File
import cv2
import numpy as np
import uvicorn
from ultralytics import YOLO
import torch
import os
import socket


app = FastAPI()

# Print current working directory for debugging
current_dir = os.getcwd()
print(f"Current working directory: {current_dir}")

# Print all available network interfaces
print("Available network interfaces:")
try:
    hostname = socket.gethostname()
    host_ip = socket.gethostbyname(hostname)
    print(f"Hostname: {hostname}")
    print(f"Host IP: {host_ip}")
    
    # Try to get all network interfaces
    for interface in socket.getaddrinfo(hostname, None):
        print(f"Interface: {interface[4][0]}")
        
except Exception as e:
    print(f"Error getting network interfaces: {e}")

# Initialize YOLO model
 
model_path = r"D:\G_working 2 - Copy\gesture control\yolo - 3\gesture_detection\exp172\weights\best.pt"

# Check if the model file exists
if os.path.exists(model_path):
    print(f"Model file found at: {model_path}")
else:
    print(f"Model file NOT found at: {model_path}")
    
model = YOLO(model_path)
print(f"Model loaded: {model}")

# Define class names
class_names = {
    0: "open_app",
    1: "scroll_up",
    2: "thumbs_down",
    3: "thumbs_up",
    4: "scroll_down",
    5: "move",
    6: "click",
    7: "fist",
    8: "forward",
    9: "play_pause",
    10:"backward",
    11:"swipe_right",
    12:"swipe_left"
}

# Lower confidence threshold for better detection
CONFIDENCE_THRESHOLD = 0.7

# Add health endpoint for client connectivity checks
@app.get("/health")
async def health_check():
    """Health check endpoint that returns 200 OK if server is running."""
    return {"status": "ok", "model_loaded": True, "server_ip": socket.gethostbyname(socket.gethostname())}

@app.post("/upload_frame")
async def upload_frame(file: UploadFile = File(...)):
    try:
        # Read and convert image
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        # Perform gesture detection using YOLO
        results = model(frame)
        
        # Initialize response variables
        gesture_detected = False
        detected_gesture = None
        confidence = 0.0
        finger_position = None
        
        # Check if any gestures were detected
        if len(results[0].boxes) > 0:
            # Get the prediction with highest confidence
            boxes = results[0].boxes
            if len(boxes) > 0:
                # Get the highest confidence prediction
                conf = float(boxes[0].conf)
                class_id = int(boxes[0].cls)
                # Debug print
                print(f"Detection: Class {class_id} ({class_names.get(class_id, 'unknown')}) with confidence {conf:.2f}")
                
                if conf > CONFIDENCE_THRESHOLD:  # Lower confidence threshold
                    gesture_detected = True
                    detected_gesture = class_names.get(class_id, "unknown")
                    confidence = conf
                    
                    # Get bounding box coordinates (YOLO format: xywh)
                    x, y, w, h = boxes[0].xywh[0].tolist()
                    
                    # Calculate center point of the hand
                    # Convert to normalized coordinates (0-1)
                    finger_position = {
                        "x": float(x / frame.shape[1]),  # Center X normalized by image width
                        "y": float(y / frame.shape[0])   # Center Y normalized by image height
                    }
                    print(f"Finger position: x={finger_position['x']:.2f}, y={finger_position['y']:.2f}")
        
        # Save test frame with detection
        if gesture_detected:
            # Draw the prediction on the frame
            annotated_frame = results[0].plot()
            cv2.imwrite("received_frame.jpg", annotated_frame)
        else:
            cv2.imwrite("received_frame.jpg", frame)

        return {
            "status": "success", 
            "gesture_detected": gesture_detected,
            "detected_gesture": detected_gesture,
            "confidence": confidence if confidence else None,
            "position": finger_position if gesture_detected else None  # Add position data to response
        }
        
    except Exception as e:
        print(f"Error: {e}")
        return {"status": "error", "message": str(e)}

if __name__ == "__main__":
    print(f"Starting server on 0.0.0.0:8080...")
    print(f"Local access URL: http://localhost:8080")
    print(f"Network access URL: http://{socket.gethostbyname(socket.gethostname())}:8080")
    uvicorn.run(app, host="0.0.0.0", port=8080)
    # hello check