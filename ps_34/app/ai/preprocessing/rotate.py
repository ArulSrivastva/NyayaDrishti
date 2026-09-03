import cv2
import numpy as np


def _angle_from_lines(gray):
    edges = cv2.Canny(gray, 50, 150, apertureSize=3)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 180, threshold=120, minLineLength=80, maxLineGap=12)
    if lines is None or len(lines) == 0:
        return 0.0
    angles = []
    for line in lines:
        line = np.ravel(line)
        x1, y1, x2, y2 = int(line[0]), int(line[1]), int(line[2]), int(line[3])
        if abs(x2 - x1) < 1:
            continue
        angle = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        if abs(angle) < 45:
            angles.append(angle)
    if not angles:
        return 0.0
    hist, _ = np.histogram(angles, bins=90, range=(-45, 45))
    peak = int(np.argmax(hist)) - 45
    if abs(peak) < 1.0:
        return 0.0
    return peak


def correct_rotation(image):
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    angle = _angle_from_lines(gray)
    if abs(angle) < 1.0:
        return image, angle
    h, w = image.shape[:2]
    center = (w // 2, h // 2)
    matrix = cv2.getRotationMatrix2D(center, angle, 1.0)
    rotated = cv2.warpAffine(image, matrix, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
    return rotated, angle