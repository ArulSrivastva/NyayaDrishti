import os
import uuid

from fastapi.testclient import TestClient

from app.main import app

SAMPLE = os.path.join(os.path.dirname(__file__), "..", "samples", "sample_compliant.jpg")


def _unique_email(label: str) -> str:
    return f"{label}-{uuid.uuid4().hex[:10]}@example.com"


def _client():
    client = TestClient(app)
    return client


def _register(client: TestClient, email: str):
    response = client.post(
        "/auth/register",
        json={
            "email": email,
            "full_name": "Test Inspector",
            "password": "testpassword123",
            "role": "inspector",
        },
    )
    return response


def _login(client: TestClient, email: str) -> str:
    response = client.post("/auth/login", json={"email": email, "password": "testpassword123"})
    assert response.status_code == 200, response.text
    return response.json()["access_token"]


def test_health():
    with _client() as client:
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "ok"


def test_register_login_flow():
    email = _unique_email("flow")
    with _client() as client:
        register = _register(client, email)
        assert register.status_code == 201, register.text
        token = _login(client, email)
        assert token
        me = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert me.status_code == 200
        assert me.json()["email"] == email


def test_inspect_requires_auth():
    with _client() as client:
        with open(SAMPLE, "rb") as fh:
            response = client.post(
                "/inspect",
                files={"file": ("label.jpg", fh, "image/jpeg")},
            )
        assert response.status_code == 401


def test_full_inspection_flow():
    email = _unique_email("inspect")
    with _client() as client:
        _register(client, email)
        token = _login(client, email)
        headers = {"Authorization": f"Bearer {token}"}
        with open(SAMPLE, "rb") as fh:
            response = client.post(
                "/inspect",
                headers=headers,
                data={"product_name": "Premium Protein Biscuit"},
                files={"file": ("sample_compliant.jpg", fh, "image/jpeg")},
            )
        assert response.status_code == 200, response.text
        body = response.json()
        assert body["inspection_id"]
        assert body["product"] and body["product"]["name"] == "Premium Protein Biscuit"
        assert body["compliance"]["status"] in ("PASS", "FAIL", "REVIEW", "MANUAL")
        products = client.get("/products", headers=headers)
        assert products.status_code == 200
        names = [p["name"] for p in products.json()]
        assert "Premium Protein Biscuit" in names
        detail = client.get(f"/inspections/{body['inspection_id']}/full", headers=headers)
        assert detail.status_code == 200
        assert "declarations" in detail.json() or "product" in detail.json()


def test_admin_self_registration_forbidden():
    email = _unique_email("admin_exploit")
    with _client() as client:
        resp = client.post(
            "/auth/register",
            json={
                "email": email,
                "full_name": "Attacker",
                "password": "password123",
                "role": "admin",
            },
        )
        assert resp.status_code == 403


def test_report_download_path_traversal_blocked():
    email = _unique_email("traversal")
    with _client() as client:
        _register(client, email)
        token = _login(client, email)
        headers = {"Authorization": f"Bearer {token}"}
        # Attempt path traversal
        resp = client.get("/reports/file/..%2F..%2Fapp%2Fconfig.py", headers=headers)
        assert resp.status_code in (400, 404)
        # Unauthenticated request must be rejected with 401
        unauth_resp = client.get("/reports/file/safe_report.pdf")
        assert unauth_resp.status_code == 401