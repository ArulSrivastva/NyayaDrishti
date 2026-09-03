import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

os.environ.setdefault("OCR_ENGINE", "mock")
os.environ.setdefault("DATABASE_URL", "sqlite:///./test_lmcs.db")
os.environ.setdefault("UPLOAD_DIR", "./test_uploads")
os.environ.setdefault("EVIDENCE_DIR", "./test_evidence")
os.environ.setdefault("REPORT_DIR", "./test_reports")