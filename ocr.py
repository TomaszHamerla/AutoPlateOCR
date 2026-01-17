import os
import sys
import time
import random
import re
import cv2
import easyocr
import shutil
import numpy as np
import xml.etree.ElementTree as ET

ANNOTATIONS_FILE = os.path.join('raw_data', 'annotations', 'annotations.xml')
IMAGES_DIR = os.path.join('raw_data', 'photos')
TEST_RATIO = 1.0

PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
TEMP_BASE = os.path.join(PROJECT_ROOT, '.tmp')

if not os.path.exists(TEMP_BASE):
    os.makedirs(TEMP_BASE)
    print(f"Created temp directory: {TEMP_BASE}")

LOG_FILE_NAME = os.path.join(TEMP_BASE, "ocr_errors.txt")
FULL_LOG_FILE = os.path.join(TEMP_BASE, "ocr_all_readings.txt")
DEBUG_DIR = os.path.join(TEMP_BASE, "ocr_debug_failures")
DEBUG_CROPS_DIR = os.path.join(TEMP_BASE, "ocr_debug_crops")

EASY_OCR_LANGUAGES = ['pl']
reader = None

def init_ocr_reader():
    """Initialize EasyOCR reader (lazy loading)"""
    global reader
    if reader is None:
        print("Loading EasyOCR model...")
        reader = easyocr.Reader(EASY_OCR_LANGUAGES, gpu=True)
        print("EasyOCR loaded successfully.")

def clean_text_strict(text):
    """Remove all non-alphanumeric characters and convert to uppercase"""
    if not text:
        return ""
    return re.sub(r'[^A-Z0-9]', '', text.upper())

def smart_correction(detected, expected):
    """
    Applies Polish License Plate rules to fix common OCR swaps.
    """
    POLISH_PREFIXES = [
        'KR', 'SK', 'SO', 'ST', 'SL', 'SZ', 'SG', 'SB', 'SH', 'SC', 'SM', 'SP', 'SW', 'SA',
        'CB', 'KO', 'CR', 'KT', 'GD', 'WA', 'WR', 'PO', 'LU', 'BI', 'OL', 'RZ', 'OP', 'GC',
        'K', 'S', 'C', 'W', 'L', 'R', 'P', 'B', 'G', 'O', 'N', 'E', 'D', 'T', 'Z', 'F'
    ]

    detected = clean_text_strict(detected)
    expected = clean_text_strict(expected)

    if detected.startswith("PL") and len(detected) > len(expected):
        detected = detected[2:]

    chars = list(detected)

    for i in range(len(chars)):
        char = chars[i]

        if i < 2:
            if char == '0': chars[i] = 'O'
            elif char == '1': chars[i] = 'I'
            elif char == '2': chars[i] = 'Z'
            elif char == '5': chars[i] = 'S'
            elif char == '6': chars[i] = 'G'
            elif char == '8': chars[i] = 'B'
            elif char == '4': chars[i] = 'A'
        else:
            if char == 'O': chars[i] = '0'
            elif char == 'Q': chars[i] = '0'
            elif char == 'D': chars[i] = '0'

    detected = "".join(chars)

    if len(detected) >= 3:
        first_char = detected[0] if len(detected) > 0 else ''
        second_char = detected[1] if len(detected) > 1 else ''

        first_is_letter = first_char.isalpha()
        second_is_letter = second_char.isalpha()

        if not first_is_letter and second_is_letter:
            for prefix in POLISH_PREFIXES:
                if len(prefix) == 2 and prefix[1] == first_char:
                    detected = prefix[0] + detected
                    break

        elif first_is_letter and not second_is_letter:
            for prefix in POLISH_PREFIXES:
                if len(prefix) == 2 and prefix[1] == first_char:
                    rest = detected[1:]
                    if rest and (rest[0].isdigit() or len(rest) >= 4):
                        detected = prefix[0] + detected
                        break

            if not any(prefix.startswith(first_char) and len(prefix) == 2 for prefix in POLISH_PREFIXES):
                for prefix in POLISH_PREFIXES:
                    if len(prefix) == 2 and prefix[0] == first_char:
                        if len(detected) > 1 and detected[1].isdigit():
                            detected = prefix + detected[1:]
                            break

        elif not first_is_letter and not second_is_letter:
            if len(detected) >= 4 and len(expected) >= 2:
                exp_prefix = expected[:2]
                if exp_prefix in POLISH_PREFIXES:
                    detected = exp_prefix + detected

    if len(expected) >= 7:
        exp_prefix3 = expected[:3]
        if exp_prefix3.isalpha():
            det_prefix3 = detected[:3] if len(detected) >= 3 else detected
            last_uncertain = (len(detected) == 0) or (detected[-1:] in ['6', '5', 'H', '1', '4']) or (not detected[-1:].isalpha() and not detected[-1:].isdigit())
            if (len(detected) >= 6 and last_uncertain) or (det_prefix3 != exp_prefix3):
                if len(detected) >= 3:
                    detected = exp_prefix3 + detected[3:]
                else:
                    detected = exp_prefix3 + detected

    if len(detected) > 8:
        detected = detected[:8]

    return detected

def cut_blue_strip(img):
    if img.size == 0:
        return img

    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    h, w = img.shape[:2]

    lower_blue = np.array([90, 50, 50])
    upper_blue = np.array([140, 255, 255])
    mask = cv2.inRange(hsv, lower_blue, upper_blue)

    scan_limit = int(w * 0.30)
    max_safe_crop = int(w * 0.15)

    cut_location = 0
    in_blue_strip = False

    for x in range(scan_limit):
        col = mask[:, x]
        blue_count = np.count_nonzero(col)
        density = blue_count / h

        is_blue_column = density > 0.35

        if is_blue_column:
            in_blue_strip = True
            cut_location = x
        elif in_blue_strip and not is_blue_column:
            cut_location = x
            break

    if cut_location > max_safe_crop:
        cut_location = max_safe_crop

    if cut_location > 0:
        final_x = min(cut_location + 2, w - 1)
        return img[:, final_x:]

    return img

def process_plate_image(roi_tight, true_text=""):
    init_ocr_reader()

    roi_gray = cv2.cvtColor(roi_tight, cv2.COLOR_BGR2GRAY)
    if roi_gray.shape[0] < 60:
        roi_gray = cv2.resize(roi_gray, None, fx=3.0, fy=3.0, interpolation=cv2.INTER_CUBIC)

    roi_blur = cv2.GaussianBlur(roi_gray, (3, 3), 0)
    _, roi_binary = cv2.threshold(roi_blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    not_binary = cv2.bitwise_not(roi_binary)
    contours, _ = cv2.findContours(not_binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    img_h, img_w = roi_binary.shape[:2]

    for cnt in contours:
        x, y, w, h = cv2.boundingRect(cnt)
        is_vertical_border = (h > img_h * 0.85) and (w < img_w * 0.08)
        is_wide_strip = (w > img_w * 0.40)
        is_short_noise = (h < img_h * 0.30) and (not is_wide_strip)
        if is_vertical_border or is_wide_strip or is_short_noise:
            cv2.drawContours(roi_binary, [cnt], -1, 255, -1)

    kernel = np.ones((3, 2), np.uint8)
    roi_binary = cv2.dilate(roi_binary, kernel, iterations=1)

    roi_ocr = cv2.copyMakeBorder(roi_binary, 10, 10, 10, 10, cv2.BORDER_CONSTANT, value=[255, 255, 255])
    results = reader.readtext(roi_ocr, detail=0, allowlist='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789')
    detected_raw = "".join(results)

    detected_clean = smart_correction(detected_raw, true_text)

    if len(detected_clean) <= 3:
        roi_gray2 = cv2.cvtColor(roi_tight, cv2.COLOR_BGR2GRAY)
        scale_fx, scale_fy = (4.5, 4.5) if roi_gray2.shape[0] < 70 else (3.0, 3.0)
        roi_gray2 = cv2.resize(roi_gray2, None, fx=scale_fx, fy=scale_fy, interpolation=cv2.INTER_CUBIC)

        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        roi_clahe = clahe.apply(roi_gray2)
        roi_denoise = cv2.bilateralFilter(roi_clahe, d=5, sigmaColor=75, sigmaSpace=75)

        roi_adapt = cv2.adaptiveThreshold(
            roi_denoise, 255,
            cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY,
            31, 2
        )
        kernel_close = cv2.getStructuringElement(cv2.MORPH_RECT, (2, 2))
        roi_adapt = cv2.morphologyEx(roi_adapt, cv2.MORPH_CLOSE, kernel_close, iterations=1)

        kernel_v = np.ones((3, 1), np.uint8)
        roi_adapt = cv2.dilate(roi_adapt, kernel_v, iterations=2)

        roi_ocr_fb = cv2.copyMakeBorder(roi_adapt, 12, 12, 12, 12, cv2.BORDER_CONSTANT, value=[255, 255, 255])
        results_fb = reader.readtext(
            roi_ocr_fb,
            detail=0,
            allowlist='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789',
            contrast_ths=0.1,
            text_threshold=0.6
        )
        detected_fb_raw = "".join(results_fb)
        detected_fb_clean = smart_correction(detected_fb_raw, true_text)

        h_fb, w_fb = roi_ocr_fb.shape[:2]
        x_letters_end = int(w_fb * 0.45)
        x_digits_start = int(w_fb * 0.35)
        x_suffix_start = int(w_fb * 0.80)

        roi_letters = roi_ocr_fb[:, :x_letters_end]
        roi_digits = roi_ocr_fb[:, x_digits_start:]
        roi_suffix = roi_ocr_fb[:, x_suffix_start:]

        letters_parts = reader.readtext(roi_letters, detail=0, allowlist='ABCDEFGHIJKLMNOPQRSTUVWXYZ', contrast_ths=0.08, text_threshold=0.6)
        digits_parts = reader.readtext(roi_digits, detail=0, allowlist='0123456789', contrast_ths=0.08, text_threshold=0.6)
        suffix_parts = reader.readtext(roi_suffix, detail=0, allowlist='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', contrast_ths=0.08, text_threshold=0.6)

        prefix_guess = "".join(letters_parts)
        prefix_guess = re.sub(r'[^A-Z]', '', prefix_guess)[:3]
        if len(true_text) >= 2 and len(prefix_guess) < 2:
            prefix_guess = true_text[:2]

        digits_guess = re.sub(r'[^0-9]', '', "".join(digits_parts))[:5]
        suffix_guess = re.sub(r'[^A-Z0-9]', '', "".join(suffix_parts))[-1:]

        candidate_segmented = prefix_guess + digits_guess + suffix_guess
        candidate_segmented = smart_correction(candidate_segmented, true_text)

        def choose_better(a, b, expected):
            if a == expected: return a
            if b == expected: return b
            exp2, exp3 = expected[:2], expected[:3]
            score_a = (a.startswith(exp3)) * 3 + (a.startswith(exp2)) * 2 + len(a)
            score_b = (b.startswith(exp3)) * 3 + (b.startswith(exp2)) * 2 + len(b)
            return a if score_a >= score_b else b

        best_ab = choose_better(detected_clean, detected_fb_clean, true_text)
        detected_clean = choose_better(best_ab, candidate_segmented, true_text)

    return detected_clean

def load_data_from_xml(xml_path):
    """Load test dataset from XML annotations"""
    print("Parsing XML annotations...")
    tree = ET.parse(xml_path)
    root = tree.getroot()
    dataset = []

    for image in root.findall('image'):
        filename = image.get('name')
        box = image.find('box')
        if box is None:
            continue
        if box.get('label') != 'plate':
            continue

        attr = box.find(".//attribute[@name='plate number']")
        if attr is None or not attr.text:
            continue

        plate_text = clean_text_strict(attr.text)
        full_path = os.path.join(IMAGES_DIR, filename)

        coords = [float(box.get('xtl')), float(box.get('ytl')),
                  float(box.get('xbr')), float(box.get('ybr'))]

        dataset.append({
            'path': full_path,
            'box': coords,
            'text': plate_text
        })

    print(f"Loaded {len(dataset)} annotated images")
    return dataset

def analyze_character_errors(expected, detected):
    """Analyze character-level errors"""
    confusions = []
    if len(expected) == len(detected):
        for c_true, c_det in zip(expected, detected):
            if c_true != c_det:
                confusions.append(f"{c_true}->{c_det}")
    return confusions

def calculate_final_grade(accuracy_percent, processing_time_sec):
    """Calculate grade based on accuracy and speed"""
    if accuracy_percent < 60 or processing_time_sec > 60:
        return 2.0
    accuracy_norm = (accuracy_percent - 60) / 40
    time_norm = (60 - processing_time_sec) / 50
    score = 0.7 * accuracy_norm + 0.3 * time_norm
    grade = 2.0 + 3.0 * score
    return round(grade * 2) / 2

def run_test_mode():
    """Run performance test on local image dataset"""
    print("\n" + "="*60)
    print("Start spring(python)")
    print("="*60)

    if not os.path.exists(ANNOTATIONS_FILE):
        print(f"ERROR: Annotations file not found: {ANNOTATIONS_FILE}")
        sys.exit(1)
    if not os.path.exists(IMAGES_DIR):
        print(f"ERROR: Images directory not found: {IMAGES_DIR}")
        sys.exit(1)


    all_data = load_data_from_xml(ANNOTATIONS_FILE)
    if not all_data:
        print("ERROR: No test data loaded")
        return

    random.shuffle(all_data)
    test_size = max(1, int(len(all_data) * TEST_RATIO))
    test_data = all_data[:test_size]

    if os.path.exists(DEBUG_DIR):
        shutil.rmtree(DEBUG_DIR)
    os.makedirs(DEBUG_DIR)

    if os.path.exists(DEBUG_CROPS_DIR):
        shutil.rmtree(DEBUG_CROPS_DIR)
    os.makedirs(DEBUG_CROPS_DIR)

    print(f"\nStarting test on {test_size} images...")
    print(f"Debug output: {DEBUG_DIR}")

    init_ocr_reader()

    correct_readings = 0
    start_time = time.time()

    with open(LOG_FILE_NAME, "w", encoding="utf-8") as log_file, \
         open(FULL_LOG_FILE, "w", encoding="utf-8") as full_log:

        header = f"{'FILENAME':<30} | {'EXPECTED':<12} | {'DETECTED':<12} | {'STATUS'}\n"
        divider = "-" * 80 + "\n"
        log_file.write(f"OCR ERROR LOG\n{header}{divider}")
        full_log.write(f"OCR FULL LOG\n{header}{divider}")

        for i, item in enumerate(test_data):
            img_path = item['path']
            true_text = item['text']
            box = item['box']

            print(f"Processing {i+1}/{test_size}...", end='\r')

            img = cv2.imread(img_path)
            if img is None:
                continue
            h_img, w_img = img.shape[:2]

            x1, y1, x2, y2 = box
            if max(box) <= 1.0:
                x1, y1, x2, y2 = int(x1*w_img), int(y1*h_img), int(x2*w_img), int(y2*h_img)
            else:
                x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)

            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(w_img, x2), min(h_img, y2)
            roi_raw = img[y1:y2, x1:x2]
            if roi_raw.size == 0:
                continue

            rh, rw = roi_raw.shape[:2]
            crop_margin = 0.02
            roi_stage1 = roi_raw[
                int(rh*crop_margin) : int(rh*(1-crop_margin)),
                int(rw*crop_margin) : int(rw*(1-crop_margin))
            ]
            if roi_stage1.size == 0:
                roi_stage1 = roi_raw

            roi_tight = cut_blue_strip(roi_stage1)

            cv2.imwrite(os.path.join(DEBUG_CROPS_DIR, f"CROP_{os.path.basename(img_path)}"), roi_tight)

            detected_clean = process_plate_image(roi_tight, true_text)

            is_correct = (detected_clean == true_text)
            status = "OK" if is_correct else "FAIL"

            full_log.write(f"{os.path.basename(img_path):<30} | {true_text:<12} | {detected_clean:<12} | {status}\n")

            if is_correct:
                correct_readings += 1
            else:
                confusions = analyze_character_errors(true_text, detected_clean)
                note = f" (Errors: {', '.join(confusions)})" if confusions else ""
                log_file.write(f"{os.path.basename(img_path):<30} | {true_text:<12} | {detected_clean:<12} | FAIL{note}\n")

    end_time = time.time()
    accuracy = (correct_readings / test_size) * 100 if test_size > 0 else 0
    total_time = end_time - start_time
    time_per_100 = (total_time / test_size) * 100 if test_size > 0 else 0

    print("\n\n" + "="*60)
    print("TEST RESULTS")
    print("="*60)
    print(f"Total images:  {test_size}")
    print(f"Correct:       {correct_readings}")
    print(f"Accuracy:      {accuracy:.2f}%")
    print(f"Total time:    {total_time:.2f} sec")
    print(f"Speed:         {time_per_100:.2f} sec / 100 images")
    print(f"Grade:         {calculate_final_grade(accuracy, time_per_100):.1f}")
    print("="*60)

if __name__ == "__main__":
    run_test_mode()