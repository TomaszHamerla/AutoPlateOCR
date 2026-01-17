import sys
import os
import cv2
import easyocr
import re
import warnings

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
warnings.filterwarnings("ignore")
sys.stdout.reconfigure(encoding='utf-8')

def clean_text_strict(text):
    if not text: return ""
    return re.sub(r'[^A-Z0-9]', '', text.upper())

def smart_correction(detected):
    detected = clean_text_strict(detected)
    if detected.startswith("PL"): detected = detected[2:]

    chars = list(detected)
    for i in range(len(chars)):
        char = chars[i]
        if i < 2: # Prefix (Litery)
            if char == '0': chars[i] = 'O'
            elif char == '1': chars[i] = 'I'
            elif char == '5': chars[i] = 'S'
            elif char == '8': chars[i] = 'B'
        else: # Suffix (Cyfry)
            if char == 'O': chars[i] = '0'
            elif char == 'Q': chars[i] = '0'

    return "".join(chars)[:8]

# --- INICJALIZACJA SILNIKA (Tylko raz!) ---
try:
    # gpu=True jeśli masz NVIDIA, False jeśli CPU
    reader = easyocr.Reader(['pl'], gpu=False, verbose=False)
except Exception as e:
    sys.exit(1)

# Sygnał dla Javy, że jesteśmy gotowi
print("READY")
sys.stdout.flush()

# --- GŁÓWNA PĘTLA NASŁUCHUJĄCA ---
while True:
    try:
        # 1. Czekamy na ścieżkę pliku od Javy
        line = sys.stdin.readline()
        if not line: break

        image_path = line.strip()
        if image_path == "EXIT": break

        if not os.path.exists(image_path):
            print("ERROR_FILE")
            sys.stdout.flush()
            continue

        # 2. Przetwarzanie obrazu
        img = cv2.imread(image_path)

        # Detekcja i OCR
        results = reader.readtext(img, detail=0)

        # Wybór najlepszego kandydata
        best_plate = "NONE"
        for text in results:
            clean = smart_correction(text)
            if 4 <= len(clean) <= 9:
                # Prosta heurystyka: dłuższy tekst = lepszy (np. KR12345 > KR)
                if best_plate == "NONE" or len(clean) > len(best_plate):
                    best_plate = clean

        # 3. Wysłanie wyniku do Javy
        print(best_plate)
        sys.stdout.flush()

    except Exception:
        print("ERROR_PROCESS")
        sys.stdout.flush()