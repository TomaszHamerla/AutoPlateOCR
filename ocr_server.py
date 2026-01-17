import sys
import os
import cv2
import easyocr
import numpy as np
import re
import warnings

# Wyciszenie ostrzeżeń
warnings.filterwarnings("ignore")
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

# --- LOGIKA Z TWOJEGO SKRYPTU ---

def clean_text_strict(text):
    if not text: return ""
    return re.sub(r'[^A-Z0-9]', '', text.upper())

def smart_correction(detected):
    detected = clean_text_strict(detected)
    if detected.startswith("PL"):
        detected = detected[2:]

    chars = list(detected)
    for i in range(len(chars)):
        char = chars[i]
        # ZONE 1: PREFIX (Litery)
        if i < 2:
            if char == '0': chars[i] = 'O'
            elif char == '1': chars[i] = 'I'
            elif char == '2': chars[i] = 'Z'
            elif char == '5': chars[i] = 'S'
            elif char == '6': chars[i] = 'G'
            elif char == '8': chars[i] = 'B'
            elif char == '4': chars[i] = 'A'
        # ZONE 2: SUFFIX (Cyfry/Litery)
        else:
            if char == 'O': chars[i] = '0'
            elif char == 'Q': chars[i] = '0'
            elif char == 'D': chars[i] = '0'

    return "".join(chars)[:8]

def cut_blue_strip(img):
    if img.size == 0: return img
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    h, w = img.shape[:2]
    # Blue Mask
    lower_blue = np.array([90, 50, 50])
    upper_blue = np.array([140, 255, 255])
    mask = cv2.inRange(hsv, lower_blue, upper_blue)

    scan_limit = int(w * 0.30)
    max_safe_crop = int(w * 0.18)
    cut_location = 0
    in_blue_strip = False

    for x in range(scan_limit):
        col = mask[:, x]
        density = np.count_nonzero(col) / h
        if density > 0.35:
            in_blue_strip = True
            cut_location = x
        elif in_blue_strip and density <= 0.35:
            cut_location = x
            break

    if cut_location > max_safe_crop: cut_location = max_safe_crop
    if cut_location > 0:
        return img[:, min(cut_location + 2, w - 1):]
    return img

# --- NOWA FUNKCJA: DETEKCJA (Dla zdjęć z API bez XML) ---
def process_single_image(reader, image_path):
    img = cv2.imread(image_path)
    if img is None: return "ERROR_READ"

    # 1. Detekcja tekstu na całym zdjęciu (EasyOCR radzi sobie z tym)
    # allowlist ogranicza błędy
    results = reader.readtext(img)

    best_text = ""

    for (bbox, text, prob) in results:
        # 2. Wycięcie fragmentu (crop) na podstawie bboxa znalezionego przez EasyOCR
        # bbox to [[x1,y1], [x2,y1], [x2,y2], [x1,y2]]
        (tl, tr, br, bl) = bbox
        tl = (int(tl[0]), int(tl[1]))
        br = (int(br[0]), int(br[1]))

        # Margines
        pad = 5
        x1 = max(0, tl[0] - pad)
        y1 = max(0, tl[1] - pad)
        x2 = min(img.shape[1], br[0] + pad)
        y2 = min(img.shape[0], br[1] + pad)

        roi = img[y1:y2, x1:x2]

        # 3. Twoja logika: Wycinka paska i preprocessing
        roi = cut_blue_strip(roi)

        roi_gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
        roi_blur = cv2.GaussianBlur(roi_gray, (3, 3), 0)
        _, roi_binary = cv2.threshold(roi_blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

        # 4. Ponowny odczyt z poprawionego wycinka
        clean_res = reader.readtext(roi_binary, detail=0, allowlist='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789')
        detected_raw = "".join(clean_res)

        # 5. Smart Correction
        final_text = smart_correction(detected_raw)

        # Walidacja: Szukamy najdłuższego sensownego ciągu (4-8 znaków)
        if 4 <= len(final_text) <= 9:
            if len(final_text) > len(best_text):
                best_text = final_text

    return best_text if best_text else "NONE"

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("ERROR_ARGS")
        sys.exit(1)

    image_path = sys.argv[1]

    # Inicjalizacja (raz na wywołanie, w trybie persistent byłoby szybciej, ale to jest proste API)
    try:
        reader = easyocr.Reader(['pl'], gpu=False, verbose=False)
        result = process_single_image(reader, image_path)
        print(result)
    except Exception as e:
        print(f"ERROR: {e}")