from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    app_name: str = "Legal Metrology Compliance System"
    debug: bool = True
    host: str = "0.0.0.0"
    port: int = 8000

    jwt_secret: str = "change-me-in-production"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 480

    database_url: str = "sqlite:///./lmcs.db"

    upload_dir: str = "./uploads"
    evidence_dir: str = "./evidence"
    report_dir: str = "./reports"
    rules_file: str = "./data/rules.json"

    ocr_engine: str = "rapidocr"
    ocr_confidence_threshold: float = 0.4
    scan_dpi: float = 300.0
    auto_verdict_threshold: float = 95.0
    review_verdict_threshold: float = 80.0

    cors_origins: list[str] = ["http://localhost:8000", "http://127.0.0.1:8000"]


@lru_cache
def get_settings() -> Settings:
    return Settings()