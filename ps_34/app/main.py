from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api import api_router
from app.config import get_settings
from app.database import init_db
from app.services.inspection_service import ensure_dirs

settings = get_settings()

from contextlib import asynccontextmanager


@asynccontextmanager
async def lifespan(app: FastAPI):
    ensure_dirs()
    init_db()
    yield


app = FastAPI(
    title=settings.app_name,
    version="1.0.0",
    description="AI scanning pipeline to check packaged-commodity compliance against the Legal Metrology (Packaged Commodities) Rules, 2011.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


app.include_router(api_router)


@app.get("/health")
def health():
    return {"status": "ok", "app": settings.app_name}


# Note: StaticFiles public mounts for /uploads, /evidence, and /reports have been
# removed to comply with Section 65B evidential integrity and access control.
# Evidence and report artifacts are securely served via authenticated endpoints in app.api.reports.


STATIC_UI = Path(__file__).resolve().parent / "static"
app.mount("/static", StaticFiles(directory=STATIC_UI), name="static")


@app.get("/", include_in_schema=False)
def index():
    return FileResponse(STATIC_UI / "index.html")


@app.get("/favicon.ico", include_in_schema=False)
def favicon():
    icon = STATIC_UI / "favicon.ico"
    if not icon.exists():
        raise HTTPException(status_code=404, detail="Not found")
    return FileResponse(icon)