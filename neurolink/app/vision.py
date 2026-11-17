import cv2
import mediapipe as mp

from .gestures import GestureDetector, extended_fingers


class HandDetector:
    def __init__(self, config):
        camera = "GestureDetection"
        gestures = "Gestures"
        self.hands = mp.solutions.hands.Hands(
            max_num_hands=1,
            min_detection_confidence=config.getfloat(camera, "MinDetectionConfidence", fallback=0.70),
            min_tracking_confidence=config.getfloat(camera, "MinTrackingConfidence", fallback=0.70),
        )
        self.gestures = GestureDetector(
            click_frames=config.getint(gestures, "ClickFrames", fallback=5),
            back_frames=config.getint(gestures, "BackFrames", fallback=10),
            swipe_distance=config.getfloat(gestures, "SwipeDistance", fallback=0.22),
        )

    def process(self, frame):
        frame = cv2.flip(frame, 1)
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        rgb.flags.writeable = False
        result = self.hands.process(rgb)
        if not result.multi_hand_landmarks:
            return frame, self.gestures.update(None, None, None)
        landmarks = result.multi_hand_landmarks[0]
        mp.solutions.drawing_utils.draw_landmarks(frame, landmarks, mp.solutions.hands.HAND_CONNECTIONS)
        height, width = frame.shape[:2]
        points = [(point.x, point.y * height / width, point.z) for point in landmarks.landmark]
        index = landmarks.landmark[8]
        palm = landmarks.landmark[9]
        return frame, self.gestures.update(extended_fingers(points), (index.x, index.y), (palm.x, palm.y))

    def close(self):
        self.hands.close()
