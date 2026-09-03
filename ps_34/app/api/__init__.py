from fastapi import APIRouter

from app.api import auth, inspect, inspections, products, reports, violations

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(products.router)
api_router.include_router(inspect.router)
api_router.include_router(inspections.router)
api_router.include_router(violations.router)
api_router.include_router(reports.router)