from __future__ import annotations

from sqlalchemy import select

from app.database import SessionLocal, init_db
from app.models import User
from app.security import hash_password


def main() -> None:
    init_db()
    db = SessionLocal()
    try:
        existing = db.scalar(select(User).where(User.email == "admin@lmcs.gov.in"))
        if existing is None:
            db.add(
                User(
                    email="admin@lmcs.gov.in",
                    full_name="Admin Inspector",
                    role="admin",
                    hashed_password=hash_password("admin1234"),
                )
            )
            db.commit()
            print("Seeded admin@lmcs.gov.in / admin1234")
        else:
            print("Admin already exists")
    finally:
        db.close()


if __name__ == "__main__":
    main()