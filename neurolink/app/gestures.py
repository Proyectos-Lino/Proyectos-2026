from collections import deque
from dataclasses import dataclass
from math import dist, isfinite
from time import monotonic


POINT = (False, True, False, False, False)
CLICK = (False, True, True, True, True)
BACK = (False, True, True, False, False)
OPEN = (True, True, True, True, True)


@dataclass(frozen=True)
class Calibration:
    left: float = 0.14
    top: float = 0.13
    right: float = 0.86
    bottom: float = 0.87

    def valid(self):
        return (
            all(isfinite(value) and 0 <= value <= 1
                for value in (self.left, self.top, self.right, self.bottom))
            and self.right - self.left >= 0.22 and self.bottom - self.top >= 0.22
        )

    def map(self, x, y):
        return (
            max(0.0, min(1.0, (x - self.left) / (self.right - self.left))),
            max(0.0, min(1.0, (y - self.top) / (self.bottom - self.top))),
        )


class GestureDetector:
    def __init__(self, click_frames=5, back_frames=10, swipe_distance=0.22):
        self.click_frames = max(2, click_frames)
        self.back_frames = max(2, back_frames)
        self.swipe_distance = max(0.08, min(0.85, swipe_distance))
        self.palms = deque(maxlen=10)
        self.pose = None
        self.frames = 0
        self.last_action = ""
        self.last_swipe = float("-inf")
        self.reset()

    def reset(self):
        self.pose = None
        self.frames = 0
        self.last_action = ""
        self.palms.clear()

    def update(self, fingers, point, palm, now=None):
        if fingers is None:
            self.reset()
            return "lost", None
        now = monotonic() if now is None else now
        # The thumb can be relaxed while pointing or making a V. Click and swipe
        # still use its state to distinguish four fingers from an open hand.
        if fingers[1:] in (POINT[1:], BACK[1:]):
            fingers = (False, *fingers[1:])
        if fingers == self.pose:
            self.frames += 1
        else:
            self.pose = fingers
            self.frames = 1

        if fingers != OPEN:
            self.palms.clear()
        if fingers not in (CLICK, BACK) and self.frames >= 3:
            self.last_action = ""

        if fingers == POINT and self.frames >= 2:
            return "point", point
        if fingers == CLICK and self.frames >= self.click_frames and self.last_action != "click":
            self.last_action = "click"
            return "click", None
        if fingers == BACK and self.frames >= self.back_frames and self.last_action != "back":
            self.last_action = "back"
            return "back", None
        if fingers == OPEN:
            self.palms.append(palm)
            direction = self.swipe(now)
            if direction:
                return "swipe", direction
        if fingers in (OPEN, CLICK):
            return "hold", None
        return "hand", None

    def swipe(self, now):
        if len(self.palms) < 7 or now - self.last_swipe < 0.65:
            return None
        x0, y0 = self.palms[0]
        x1, y1 = self.palms[-1]
        dx, dy = x1 - x0, y1 - y0
        if abs(dx) < self.swipe_distance or abs(dy) > abs(dx) * 0.55:
            return None
        sign = 1 if dx > 0 else -1
        moves = zip(self.palms, list(self.palms)[1:])
        forward = sum(sign * (b[0] - a[0]) > 0 for a, b in moves)
        if forward < (len(self.palms) - 1) * 0.70:
            return None
        self.last_swipe = now
        self.palms.clear()
        return "right" if sign == 1 else "left"


def extended_fingers(points):
    wrist = points[0]
    thumb = dist(points[4], points[5]) > 1.10 * dist(points[3], points[5])
    fingers = [thumb]
    for tip in (8, 12, 16, 20):
        base, joint, end = points[tip - 3], points[tip - 2], points[tip]
        length = dist(base, joint) + dist(joint, end)
        straight = length > 0 and dist(base, end) > length * 0.83
        farther = dist(end, wrist) > dist(joint, wrist) * 1.04
        fingers.append(straight and farther)
    return tuple(fingers)
