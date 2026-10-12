import io

from fastapi import FastAPI, File, UploadFile
from PIL import Image

app = FastAPI(title="CertiChain document scan")


@app.get("/health")
def health():
    return {"status": "UP", "note": "Image OCR is available."}


@app.post("/extract")
async def extract(file: UploadFile = File(...)):
    raw = await file.read()
    try:
        import pytesseract

        image = Image.open(io.BytesIO(raw))
        text = pytesseract.image_to_string(image)
    except Exception as error:
        return {"text": "", "note": f"Image OCR could not read that file. {error}"}
    if not text.strip():
        return {"text": "", "note": "Image OCR did not find any text."}
    return {"text": text, "note": "Text was read from the image."}


@app.post("/analyze")
async def analyze(file: UploadFile = File(...)):
    raw = await file.read()
    if len(raw) > 10 * 1024 * 1024:
        return {"status": "REJECTED", "text": "", "note": "File is too large.", "fields": []}
    if raw.startswith(b"%PDF"):
        return {
            "status": "UNSUPPORTED",
            "text": "",
            "note": "PDF text is extracted by the API. This service reads images and does not decide authenticity.",
            "fields": [],
        }
    try:
        import pytesseract

        image = Image.open(io.BytesIO(raw))
        text = pytesseract.image_to_string(image)
        note = "Text was read from the image. OCR is not a verification."
    except Exception as error:
        return {"status": "UNAVAILABLE", "text": "", "note": f"Image OCR could not read that file. {error}", "fields": []}
    fields = []
    import re

    pattern = re.compile(
        r"^(student name|name|institution|university|degree|course|certificate|issue date|grade)\s*[:\-]\s*(.+)$",
        re.IGNORECASE,
    )
    for line in text.splitlines():
        match = pattern.match(line.strip())
        if match:
            fields.append({"name": match.group(1).lower(), "value": match.group(2).strip(), "method": "OCR", "page": 1})
    return {
        "status": "EXTRACTED" if text.strip() else "UNAVAILABLE",
        "text": text,
        "note": note,
        "fields": fields,
    }
