import cv2
import numpy as np


def to_grayscale(image):
    if image.ndim == 3 and image.shape[2] == 3:
        return cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return image


def denoise(image, strength: int = 5) -> np.ndarray:
    return cv2.fastNlMeansDenoisingColored(image, None, strength, strength, 7, 21)


def contrast_enhance(image, clip_limit: float = 2.0, tile: int = 8) -> np.ndarray:
    lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(tile, tile))
    l = clahe.apply(l)
    return cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)


def sharpen(image, amount: float = 1.2) -> np.ndarray:
    blur = cv2.GaussianBlur(image, (0, 0), 3)
    return cv2.addWeighted(image, 1.0 + amount, blur, -amount, 0)


def threshold_gray(image_gray: np.ndarray, block=31, c=15) -> np.ndarray:
    return cv2.adaptiveThreshold(image_gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, block, c)