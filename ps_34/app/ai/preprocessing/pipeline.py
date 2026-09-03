from __future__ import annotations

import cv2
import numpy as np

from app.ai.preprocessing.enhance import contrast_enhance, denoise, sharpen, threshold_gray
from app.ai.preprocessing.perspective import correct_perspective
from app.ai.preprocessing.resize import resize
from app.ai.preprocessing.rotate import correct_rotation


def preprocess(
    image: np.ndarray,
    *,
    denoise_enabled: bool = True,
    contrast_enabled: bool = True,
    rotate_enabled: bool = True,
    sharpen_enabled: bool = False,
    perspective_enabled: bool = True,
    threshold_enabled: bool = False,
) -> np.ndarray:
    processed, _ = resize(image)
    if denoise_enabled:
        processed = denoise(processed)
    if contrast_enabled:
        processed = contrast_enhance(processed)
    if rotate_enabled:
        processed, _ = correct_rotation(processed)
    if perspective_enabled:
        processed = correct_perspective(processed)
    if sharpen_enabled:
        processed = sharpen(processed)
    if threshold_enabled:
        gray = cv2.cvtColor(processed, cv2.COLOR_BGR2GRAY)
        processed = threshold_gray(gray)
        processed = cv2.cvtColor(processed, cv2.COLOR_GRAY2BGR)
    return processed