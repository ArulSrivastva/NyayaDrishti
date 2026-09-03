import cv2
import numpy as np


def _largest_quad_contour(gray):
    thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
    edges = cv2.Canny(thresh, 50, 150)
    dilated = cv2.dilate(edges, np.ones((5, 5), np.uint8), iterations=2)
    contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None
    contours = sorted(contours, key=cv2.contourArea, reverse=True)[:10]
    for contour in contours:
        peri = cv2.arcLength(contour, True)
        approx = cv2.approxPolyDP(contour, 0.02 * peri, True)
        if len(approx) == 4 and cv2.contourArea(approx) > 0.2 * gray.size:
            return approx
    return None


def _order_points(pts):
    pts = pts.reshape(4, 2).astype(np.float32)
    rect = np.zeros((4, 2), dtype=np.float32)
    s = pts.sum(axis=1)
    rect[0] = pts[np.argmin(s)]
    rect[2] = pts[np.argmax(s)]
    diff = np.diff(pts, axis=1)
    rect[1] = pts[np.argmin(diff)]
    rect[3] = pts[np.argmax(diff)]
    return rect


def correct_perspective(image):
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    quad = _largest_quad_contour(gray)
    if quad is None:
        return image
    rect = _order_points(quad)
    width = int(max(np.linalg.norm(rect[2] - rect[3]), np.linalg.norm(rect[1] - rect[0])))
    height = int(max(np.linalg.norm(rect[1] - rect[2]), np.linalg.norm(rect[0] - rect[3])))
    if width == 0 or height == 0 or width * height > 1.5 * gray.size:
        return image
    dst = np.array([[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]], dtype=np.float32)
    matrix = cv2.getPerspectiveTransform(rect, dst)
    return cv2.warpPerspective(image, matrix, (width, height))