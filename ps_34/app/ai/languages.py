from __future__ import annotations

import re

DEVANAGARI_RANGE = re.compile(r"[\u0900-\u097F]")
TAMIL_RANGE = re.compile(r"[\u0B80-\u0BFF]")
LATIN_RANGE = re.compile(r"[A-Za-z]")
INDIC_DIGITS_HI = str.maketrans("०१२३४५६७८९", "0123456789")


def normalize_text(text: str) -> str:
    text = text.translate(INDIC_DIGITS_HI)
    text = text.replace("|", " ")
    return re.sub(r"\s+", " ", text).strip()


def detect_language(text: str) -> str:
    devanagari = len(DEVANAGARI_RANGE.findall(text))
    tamil = len(TAMIL_RANGE.findall(text))
    latin = len(LATIN_RANGE.findall(text))
    if devanagari > latin and devanagari > 0:
        return "hi"
    if tamil > latin and tamil > 0:
        return "ta"
    return "en"


def scripts_in_lines(lines: list[str]) -> set[str]:
    return {detect_language(line) for line in lines if line.strip()}


SYNONYMS: dict[str, dict[str, list[str]]] = {
    "mrp": {
        "en": ["mrp", "max retail price", "maximum retail price", "max. retail price", "retail sale price", "max retail", "mrp rs", "incl of all taxes", "inclusive of all taxes"],
        "hi": ["अधिकतम खुदरा मूल्य", "अनुशंसित खुदरा मूल्य", "खुदरा", "मूल्य"],
        "ta": ["அதிகபட்ச சில்லறை விலை", "சில்லறை விலை", "MRP"],
    },
    "net_quantity": {
        "en": ["net quantity", "net qty", "net wt", "net weight", "net content", "net contents", "weight", "quantity", "net"],
        "hi": ["शुद्ध मात्रा", "शुद्ध वज़न", "शुद्ध वजन", "शुद्ध", "मात्रा", "वज़न"],
        "ta": ["நிகர அளவு", "நிகர எடை", "நிகர", "அளவு"],
    },
    "manufacturer": {
        "en": ["manufactured by", "manufacturer", "mfd by", "mfg by", "mfr", "manufacturing", "made by"],
        "hi": ["निर्मित", "निर्माता", "निर्माण", "बनाया"],
        "ta": ["தயாரிப்பு", "தயாரித்தவர்", "உற்பத்தியாளர்", "தயாரிக்க"],
    },
    "packer": {
        "en": ["packed by", "packer", "packed at", "packaging by", "pre-packed", "pre packed", "packed"],
        "hi": ["पैक", "पैकर", "पैकिंग"],
        "ta": ["பேக்கிங்", "பேக்கர்"],
    },
    "importer": {
        "en": ["imported by", "importer", "imported", "imported from", "import:"],
        "hi": ["आयात", "आयातकर्ता"],
        "ta": ["இறக்குமதி", "இறக்குமதியாளர்"],
    },
    "date": {
        "en": ["mfg date", "manufacturing date", "mfd date", "date of mfg", "manf", "mfg.", "manufacture date", "manufactured date", "manuf date", "packed on", "packing date", "date of packing", "best before", "use before", "expiry", "exp date", "best by", "pack date", "packed"],
        "hi": ["निर्माण तिथि", "निर्माण की तारीख", "निर्मित", "पैकेजिंग तिथि", "पैकिंग", "निर्माण दिनांक"],
        "ta": ["தயாரிக்கும் தேதி", "தயாரிப்பு தேதி", "செய்யப்பட்ட தேதி", "பேக்கிங் தேதி"],
    },
    "consumer_care": {
        "en": ["customer care", "consumer care", "consumer complaint", "complaint", "helpline", "toll free", "contact us", "for complaints", "email", "contact"],
        "hi": ["उपभोक्ता", "ग्राहक", "शिकायत", "हेल्पलाइन"],
        "ta": ["நுகர்வோர்", "வாடிக்கையாளர்", "புகார்"],
    },
    "commodity": {
        "en": ["common name", "product name", "ingredients", "commodity"],
        "hi": ["सामान्य नाम", "घटक"],
        "ta": ["பொதுவான பெயர்"],
    },
}

LEGACY_FIELD_ALIASES = {
    "commodity_name": "commodity",
    "mfg_date": "date",
    "packing_date": "date",
    "consumer_care_contact": "consumer_care",
}


def field_synonyms(field: str) -> list[str]:
    if field in LEGACY_FIELD_ALIASES:
        field = LEGACY_FIELD_ALIASES[field]
    words: list[str] = []
    for lang_words in SYNONYMS.get(field, {}).values():
        words.extend(lang_words)
    return words


def strip_punctuation(text: str) -> str:
    return re.sub(r"[^A-Za-z0-9\u0900-\u097F\u0B80-\u0BFF\s]", " ", text)