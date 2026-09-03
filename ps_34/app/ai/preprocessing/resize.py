import cv2


def resize(image, max_width: int = 1600, max_height: int = 1600) -> tuple:
    h, w = image.shape[:2]
    scale = min(1.0, max_width / w, max_height / h)
    if scale < 1.0:
        image = cv2.resize(image, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
    return image, scale