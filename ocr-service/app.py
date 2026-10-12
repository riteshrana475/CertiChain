import io
import re

from fastapi import FastAPI, File, UploadFile
from PIL import Image

app = FastAPI(title="CertiChain document scan")
MAX_BYTES = 10 * 1024 * 1024
MAX_PAGES = 8
FIELD_PATTERN = re.compile(
    r"^(student name|student|name|institution|university|degree|course|credential number|certificate id|certificate number|credential|certificate|issue date|date of issue|expiry date|expiry|grade)\s*[:\-]\s*(.+)$",
    re.IGNORECASE,
)


@app.get("/health")
def health():
    return {"status": "UP", "note": "Image and PDF reading are available. OCR text is not a verification."}


@app.post("/extract")
async def extract(file: UploadFile = File(...)):
    raw = await file.read()
    if len(raw) > MAX_BYTES:
        return {"text": "", "note": "File is too large."}
    if raw.startswith(b"%PDF"):
        try:
            text = pdf_text(raw)
        except Exception as error:
            return {"text": "", "note": f"The PDF could not be read. {error}"}
        if not text.strip():
            return {"text": "", "note": "The PDF had no embedded text and OCR did not find any."}
        return {"text": text, "note": "Text was read from the PDF. This is not a verification."}
    try:
        text = image_text(raw)
    except Exception as error:
        return {"text": "", "note": f"Image OCR could not read that file. {error}"}
    if not text.strip():
        return {"text": "", "note": "Image OCR did not find any text."}
    return {"text": text, "note": "Text was read from the image. This is not a verification."}


@app.post("/analyze")
async def analyze(file: UploadFile = File(...)):
    raw = await file.read()
    if len(raw) > MAX_BYTES:
        return {"status": "REJECTED", "text": "", "note": "File is too large.", "fields": []}
    try:
        if raw.startswith(b"%PDF"):
            text = pdf_text(raw)
            note = "PDF text or page OCR was read locally. This is not a verification."
        else:
            text = image_text(raw)
            note = "Image text was read locally. This is not a verification."
    except Exception as error:
        return {"status": "UNAVAILABLE", "text": "", "note": f"The file could not be read. {error}", "fields": []}
    return {
        "status": "EXTRACTED" if text.strip() else "UNAVAILABLE",
        "text": text,
        "note": note if text.strip() else "No text was found.",
        "fields": fields_from(text),
    }


def image_text(raw: bytes) -> str:
    import pytesseract

    image = Image.open(io.BytesIO(raw))
    return pytesseract.image_to_string(image)


def pdf_text(raw: bytes) -> str:
    import fitz
    import pytesseract

    document = fitz.open(stream=raw, filetype="pdf")
    parts = []
    for index, page in enumerate(document):
        if index >= MAX_PAGES:
            break
        embedded = page.get_text("text") or ""
        if len(embedded.strip()) >= 20:
            parts.append(embedded)
            continue
        pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2))
        image = Image.open(io.BytesIO(pixmap.tobytes("png")))
        recognized = pytesseract.image_to_string(image)
        parts.append(f"--- page {index + 1} ---\n{recognized}")
    return "\n".join(parts)


def fields_from(text: str) -> list[dict]:
    found = []
    for line in text.splitlines():
        match = FIELD_PATTERN.match(line.strip())
        if match:
            found.append({"name": match.group(1).lower(), "value": match.group(2).strip(), "method": "OCR", "page": 1})
    return found
