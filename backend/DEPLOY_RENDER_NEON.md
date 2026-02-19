# Deploy FastAPI backend on Render + Neon (free tier friendly)

This backend supports:
- Local dev with SQLite (default)
- Production with Postgres via `DATABASE_URL`

## 1) Create a Neon Postgres database

1. Go to Neon and create an account.
2. Create a new **Project** (region near you).
3. In the project dashboard, copy the **connection string** (it looks like `postgresql://USER:PASSWORD@HOST/DB?sslmode=require`).

Keep it handy; you will paste it into Render as `DATABASE_URL`.

## 2) Push code to GitHub (or Git provider supported by Render)

Render deploys from a repo.

## 3) Create the Render Web Service

1. In Render: **New** → **Web Service**.
2. Connect your repo.
3. Configure:
   - **Root Directory**: `backend`
   - **Runtime**: Python
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `uvicorn main:app --host 0.0.0.0 --port $PORT`

## 4) Set environment variables in Render

In the Render service settings → **Environment**:

- `DATABASE_URL` = (paste Neon connection string)
- `LAB_ADMIN_USER` = admin username (choose your value)
- `LAB_ADMIN_PASS` = admin password (choose a strong value)

Optional (fallback users for the mobile app; DB-backed signup also exists):
- `LAB_USERS` = `alice:pass,bob:pass2`

Optional (security/retention tuning):
- `TOKEN_TTL_DAYS` = `30` (default 30)
- `BOOKINGS_RETENTION_DAYS` = `180` (default 180)
- `INACTIVE_USER_DAYS` = `365` (default 365)
- `CLEANUP_INTERVAL_HOURS` = `24` (default 24) → runs cleanup periodically (not only at startup)

## GDPR / data deletion endpoints

- Self-service (Bearer token + password): `POST /auth/delete-account`
- Admin (HTTP Basic): `DELETE /admin/users/{username}`

## 5) Deploy and verify

1. Click **Deploy**.
2. Once live, open:
   - `/health` → should return `{ "ok": true }`
   - `/desks?day=YYYY-MM-DD` → should return the desk grid
   - `/admin/desks?day=YYYY-MM-DD` → should ask for HTTP Basic auth

## Notes / gotchas

- Render free instances may sleep after inactivity, causing cold starts.
- SQLite is not suitable on Render free because the filesystem is ephemeral; use Neon Postgres in production.
- Schema is created on startup (`SQLModel.metadata.create_all`). For future schema changes, consider migrations.

### Driver note (Postgres)

Neon provides `postgresql://...` URLs. This project automatically rewrites them to `postgresql+psycopg://...` at runtime so SQLAlchemy uses psycopg (v3).
