# VLSI Booking App

Desk booking app for a lab (FastAPI backend + Android app).

## Structure
- `backend/` → API + admin page + DB (SQLite locally, Postgres in production)
- `VLSIBooking/` → Android app (Jetpack Compose)

## Main features
- 4×6 desk grid with `desk_type`: `staff`, `tesisti`, `bloccata`
- AM/PM bookings for `tesisti` desks
- `staff` desks with `holder_name` and absence coverage via a “temporary occupant”
- Web admin area protected by HTTP Basic

## Run backend (local)
From the project root:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Useful endpoints:
- `GET /health`
- `GET /desks?day=YYYY-MM-DD`
- Swagger: `GET /docs`
- Admin: `GET /admin/desks?day=YYYY-MM-DD`

### Environment variables (backend)
- `DATABASE_URL` (default: local SQLite)
- `LAB_ADMIN_USER`, `LAB_ADMIN_PASS` (admin credentials)
- `LAB_USERS` (fallback users, format `alice:pass,bob:pass2`)
- `TOKEN_TTL_DAYS`, `BOOKINGS_RETENTION_DAYS`, `INACTIVE_USER_DAYS`, `CLEANUP_INTERVAL_HOURS` (optional)

Data deletion endpoints:
- Self-service: `POST /auth/delete-account` (requires Bearer token + password)
- Admin: `DELETE /admin/users/{username}` (HTTP Basic)

## Android app
Open the `VLSIBooking/` folder in Android Studio.

The backend used by the app is configured in `VLSIBooking/app/build.gradle.kts` via `BACKEND_BASE_URL` (it must end with `/` for Retrofit).

## License
This project is released under the **GNU AGPL v3** (see `LICENSE`).
