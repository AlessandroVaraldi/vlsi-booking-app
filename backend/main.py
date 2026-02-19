"""
main.py — Lab Desk Booking + Admin + Staff Vacation/Temporary Occupant

Features added vs your current version:
- Staff desks can have a holder_name
- You can define vacation/coverage periods where the holder is away and a temporary occupant is assigned
- /desks now returns current_occupant for the requested day (holder or temporary occupant)
- Admin authentication (HTTP Basic) protects admin pages + admin endpoints
- Automatic deletion of ALL bookings for a desk when it stops being a "tesisti" desk
"""

from datetime import date, datetime, timedelta
from enum import Enum
from typing import Optional, List, Dict, Tuple
from contextlib import asynccontextmanager
import asyncio
import os
import secrets
import hashlib

from fastapi import FastAPI, HTTPException, Query, Depends, Form
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.security import HTTPBasic, HTTPBasicCredentials, HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field
from sqlmodel import SQLModel, Field as SQLField, Session, create_engine, select, UniqueConstraint


# ----------------------------
# Config
# ----------------------------
def _int_env(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        v = int(raw)
    except ValueError:
        return default
    return v


# Token TTL for the mobile app (days)
TOKEN_TTL_DAYS = _int_env("TOKEN_TTL_DAYS", 30)

# Data retention (days)
BOOKINGS_RETENTION_DAYS = _int_env("BOOKINGS_RETENTION_DAYS", 180)
INACTIVE_USER_DAYS = _int_env("INACTIVE_USER_DAYS", 365)

# How often to run the retention cleanup loop (hours)
CLEANUP_INTERVAL_HOURS = _int_env("CLEANUP_INTERVAL_HOURS", 24)


def get_database_url() -> str:
    """Resolve DB URL from env, with a local SQLite default."""

    url = os.getenv("DATABASE_URL") or os.getenv("DB_URL") or "sqlite:///./lab_desks.db"

    # Some providers historically used postgres://.
    # Also, SQLAlchemy defaults to psycopg2 for postgresql:// URLs, but this project
    # ships with psycopg (v3). Force the driver explicitly.
    if url.startswith("postgres://"):
        url = url.replace("postgres://", "postgresql://", 1)

    if url.startswith("postgresql://") and not url.startswith("postgresql+"):
        url = url.replace("postgresql://", "postgresql+psycopg://", 1)

    return url


DB_URL = get_database_url()

if DB_URL.startswith("sqlite:"):
    engine = create_engine(DB_URL, echo=False, connect_args={"check_same_thread": False})
else:
    engine = create_engine(DB_URL, echo=False, pool_pre_ping=True)

# Admin credentials (set via env in production!)
# Example:
#   export LAB_ADMIN_USER="admin"
#   export LAB_ADMIN_PASS="change-me"
ADMIN_USER = os.getenv("LAB_ADMIN_USER", "admin")
ADMIN_PASS = os.getenv("LAB_ADMIN_PASS", "change-me")

# Simple user auth credentials for the mobile app.
# Format: "alice:pass,bob:pass2" (avoid using this in production).
LAB_USERS = os.getenv("LAB_USERS", "user:password")

security = HTTPBasic()
bearer = HTTPBearer(auto_error=False)


def parse_users(spec: str) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for part in (spec or "").split(","):
        part = part.strip()
        if not part:
            continue
        if ":" not in part:
            continue
        u, p = part.split(":", 1)
        u = u.strip()
        p = p.strip()
        if u:
            out[u] = p
    return out


APP_USERS = parse_users(LAB_USERS)


def require_admin(credentials: HTTPBasicCredentials = Depends(security)) -> None:
    """
    HTTP Basic auth guard for admin-only endpoints.
    Browsers will show a login popup automatically.
    """
    user_ok = secrets.compare_digest(credentials.username, ADMIN_USER)
    pass_ok = secrets.compare_digest(credentials.password, ADMIN_PASS)
    if not (user_ok and pass_ok):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized",
            headers={"WWW-Authenticate": "Basic"},
        )


# ----------------------------
# Models
# ----------------------------
class DeskType(str, Enum):
    STAFF = "staff"
    THESIS = "tesisti"
    BLOCKED = "bloccata"


class Slot(str, Enum):
    AM = "AM"
    PM = "PM"


class Desk(SQLModel, table=True):
    id: Optional[int] = SQLField(default=None, primary_key=True)
    row: int
    col: int
    desk_type: DeskType = SQLField(index=True)
    label: str = ""  # e.g. "D12"
    holder_name: Optional[str] = SQLField(default=None, index=True, max_length=80)  # for STAFF desks


class Booking(SQLModel, table=True):
    __table_args__ = (
        UniqueConstraint("desk_id", "day", "slot", name="uq_desk_day_slot"),
        UniqueConstraint("booked_by", "day", "slot", name="uq_person_day_slot"),
    )

    id: Optional[int] = SQLField(default=None, primary_key=True)
    desk_id: int = SQLField(foreign_key="desk.id", index=True)
    day: date = SQLField(index=True)
    slot: Slot = SQLField(index=True)
    booked_by: str = SQLField(index=True, max_length=80)


class StaffCoverage(SQLModel, table=True):
    """
    A period where the desk holder is away and a temporary occupant uses the desk.

    Rules:
    - Intended for STAFF desks
    - We prevent overlapping coverages for the same desk (checked in code)
    """
    id: Optional[int] = SQLField(default=None, primary_key=True)
    desk_id: int = SQLField(foreign_key="desk.id", index=True)
    start_day: date = SQLField(index=True)
    end_day: date = SQLField(index=True)
    temp_occupant: str = SQLField(index=True, max_length=80)
    note: str = SQLField(default="", max_length=200)


class AuthToken(SQLModel, table=True):
    token: str = SQLField(primary_key=True)
    username: str = SQLField(index=True, max_length=80)
    created_at: datetime = SQLField(index=True)
    expires_at: datetime = SQLField(index=True)


class User(SQLModel, table=True):
    username: str = SQLField(primary_key=True, max_length=80)
    password_salt_hex: str = SQLField(max_length=64)
    password_hash_hex: str = SQLField(max_length=128)
    created_at: datetime = SQLField(index=True)


# ----------------------------
# API Schemas
# ----------------------------
class BookingCreate(BaseModel):
    desk_id: int
    day: date
    booked_by: str = Field(min_length=1, max_length=80)
    am: bool = True
    pm: bool = False


class BookingOut(BaseModel):
    id: int
    desk_id: int
    day: date
    slot: Slot
    booked_by: str


class DeskStatusOut(BaseModel):
    id: int
    row: int
    col: int
    desk_type: DeskType
    label: str

    # For STAFF desks
    holder_name: Optional[str] = None
    current_occupant: Optional[str] = None
    holder_away: bool = False
    away_start: Optional[date] = None
    away_end: Optional[date] = None
    away_temp_occupant: Optional[str] = None

    # For THESIS desks
    booking_am: Optional[str] = None
    booking_pm: Optional[str] = None


class CancelRequest(BaseModel):
    booked_by: str = Field(min_length=1, max_length=80)


class DeskUpdate(BaseModel):
    desk_type: DeskType
    label: Optional[str] = None
    holder_name: Optional[str] = None  # can be set for STAFF via admin API


class CoverageCreate(BaseModel):
    desk_id: int
    start_day: date
    end_day: date
    temp_occupant: str = Field(min_length=1, max_length=80)
    note: str = Field(default="", max_length=200)


class CoverageOut(BaseModel):
    id: int
    desk_id: int
    start_day: date
    end_day: date
    temp_occupant: str
    note: str


class LoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=80)
    password: str = Field(min_length=1, max_length=200)


class LoginResponse(BaseModel):
    token: str
    username: str
    expires_at: datetime


class ChangePasswordRequest(BaseModel):
    old_password: str = Field(min_length=1, max_length=200)
    new_password: str = Field(min_length=1, max_length=200)


class DeleteAccountRequest(BaseModel):
    password: str = Field(min_length=1, max_length=200)


# ----------------------------
# App lifecycle
# ----------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    SQLModel.metadata.create_all(engine)
    seed_if_empty()
    cleanup_old_data()

    async def _periodic_cleanup() -> None:
        # Best-effort loop: never crash the app due to cleanup.
        interval = max(1, CLEANUP_INTERVAL_HOURS) * 3600
        while True:
            await asyncio.sleep(interval)
            try:
                cleanup_old_data()
            except Exception:
                # Avoid leaking PII into logs; keep it silent.
                pass

    task = asyncio.create_task(_periodic_cleanup())
    yield
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass


app = FastAPI(title="Lab Desk Booking + Admin + Coverage", lifespan=lifespan)


# ----------------------------
# Helpers
# ----------------------------
def seed_if_empty() -> None:
    """Create a 4x6 grid if there are no desks in the DB."""
    with Session(engine) as session:
        existing = session.exec(select(Desk)).first()
        if existing:
            return

        desks: List[Desk] = []
        for r in range(4):
            for c in range(6):
                if r in (0, 1):
                    t = DeskType.STAFF
                elif r == 2:
                    t = DeskType.THESIS
                else:
                    t = DeskType.THESIS
                    if c in (0, 5):
                        t = DeskType.BLOCKED

                label = f"D{r+1}{c+1}"  # D11..D46
                holder = None
                if t == DeskType.STAFF:
                    holder = f"Holder {label}"  # placeholder, edit in admin
                desks.append(Desk(row=r, col=c, desk_type=t, label=label, holder_name=holder))

        session.add_all(desks)
        session.commit()


def normalize_name(name: str, field_name: str = "name") -> str:
    n = name.strip()
    if not n:
        raise HTTPException(status_code=400, detail=f"{field_name} cannot be empty.")
    return n


PWD_KDF_ITERATIONS = 200_000


def _hash_password(password: str, salt: bytes) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PWD_KDF_ITERATIONS)


def make_password_record(password: str) -> tuple[str, str]:
    if len(password) < 4:
        raise HTTPException(status_code=400, detail="Password too short")
    salt = secrets.token_bytes(16)
    digest = _hash_password(password, salt)
    return salt.hex(), digest.hex()


def verify_password(password: str, salt_hex: str, hash_hex: str) -> bool:
    try:
        salt = bytes.fromhex(salt_hex)
        expected = bytes.fromhex(hash_hex)
    except Exception:
        return False
    got = _hash_password(password, salt)
    return secrets.compare_digest(got, expected)


def require_user(credentials: HTTPAuthorizationCredentials = Depends(bearer)) -> str:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(status_code=401, detail="Missing bearer token")

    token = credentials.credentials
    if not token:
        raise HTTPException(status_code=401, detail="Missing bearer token")

    with Session(engine) as session:
        row = session.get(AuthToken, token)
        if not row:
            raise HTTPException(status_code=401, detail="Invalid token")
        now = datetime.utcnow()

        # Enforce global max TTL even for older tokens.
        max_expires_at = row.created_at + timedelta(days=TOKEN_TTL_DAYS)
        effective_expires_at = row.expires_at if row.expires_at <= max_expires_at else max_expires_at

        if effective_expires_at < now:
            session.delete(row)
            session.commit()
            raise HTTPException(status_code=401, detail="Token expired")
        return row.username


def cleanup_old_data() -> None:
    """Best-effort cleanup for retention (runs at startup)."""

    today = date.today()
    bookings_cutoff = today - timedelta(days=BOOKINGS_RETENTION_DAYS)
    inactive_cutoff = datetime.utcnow() - timedelta(days=INACTIVE_USER_DAYS)
    now = datetime.utcnow()

    with Session(engine) as session:
        # 1) Remove expired tokens
        expired = session.exec(select(AuthToken).where(AuthToken.expires_at < now)).all()
        for t in expired:
            session.delete(t)

        # 2) Remove old bookings
        old_bookings = session.exec(select(Booking).where(Booking.day < bookings_cutoff)).all()
        for b in old_bookings:
            session.delete(b)

        # 3) Remove inactive users (DB-backed users only)
        # Criteria:
        # - user.created_at < inactive_cutoff
        # - no tokens created after cutoff
        # - no bookings by username after cutoff date
        users = session.exec(select(User)).all()
        for u in users:
            if u.created_at >= inactive_cutoff:
                continue

            recent_token = session.exec(
                select(AuthToken)
                .where(AuthToken.username == u.username, AuthToken.created_at >= inactive_cutoff)
                .limit(1)
            ).first()
            if recent_token:
                continue

            # Bookings store a free string; we assume app uses username as booked_by.
            recent_booking = session.exec(
                select(Booking)
                .where(Booking.booked_by == u.username, Booking.day >= inactive_cutoff.date())
                .limit(1)
            ).first()
            if recent_booking:
                continue

            # Delete user tokens
            user_tokens = session.exec(select(AuthToken).where(AuthToken.username == u.username)).all()
            for t in user_tokens:
                session.delete(t)

            # Delete all bookings for this username
            user_bookings = session.exec(select(Booking).where(Booking.booked_by == u.username)).all()
            for b in user_bookings:
                session.delete(b)

            session.delete(u)

        session.commit()


def delete_user_data(session: Session, username: str) -> dict:
    """Delete user-linked data (tokens, bookings, user record) for a username."""

    deleted_tokens = 0
    deleted_bookings = 0
    deleted_user = False

    tokens = session.exec(select(AuthToken).where(AuthToken.username == username)).all()
    for t in tokens:
        session.delete(t)
        deleted_tokens += 1

    bookings = session.exec(select(Booking).where(Booking.booked_by == username)).all()
    for b in bookings:
        session.delete(b)
        deleted_bookings += 1

    user = session.get(User, username)
    if user:
        session.delete(user)
        deleted_user = True

    return {
        "deleted_tokens": deleted_tokens,
        "deleted_bookings": deleted_bookings,
        "deleted_user": deleted_user,
    }


@app.post("/auth/login", response_model=LoginResponse)
def auth_login(req: LoginRequest):
    username = normalize_name(req.username, "username")
    password = req.password

    # Prefer DB users (supports signup). Fallback to env LAB_USERS for backward compatibility.
    ok = False
    with Session(engine) as session:
        user = session.get(User, username)
        if user:
            ok = verify_password(password, user.password_salt_hex, user.password_hash_hex)

    if not ok:
        expected = APP_USERS.get(username)
        ok = expected is not None and secrets.compare_digest(password, expected)

    if not ok:
        raise HTTPException(status_code=401, detail="Invalid credentials")

    now = datetime.utcnow()
    expires_at = now + timedelta(days=TOKEN_TTL_DAYS)
    token = secrets.token_urlsafe(32)

    with Session(engine) as session:
        session.add(AuthToken(token=token, username=username, created_at=now, expires_at=expires_at))
        session.commit()

    return LoginResponse(token=token, username=username, expires_at=expires_at)


@app.post("/auth/signup", response_model=LoginResponse)
@app.post("/auth/register", response_model=LoginResponse)
def auth_signup(req: LoginRequest):
    username = normalize_name(req.username, "username")
    password = req.password

    salt_hex, hash_hex = make_password_record(password)
    now = datetime.utcnow()

    with Session(engine) as session:
        existing = session.get(User, username)
        if existing:
            raise HTTPException(status_code=409, detail="User already exists")

        session.add(User(username=username, password_salt_hex=salt_hex, password_hash_hex=hash_hex, created_at=now))

        expires_at = now + timedelta(days=TOKEN_TTL_DAYS)
        token = secrets.token_urlsafe(32)
        session.add(AuthToken(token=token, username=username, created_at=now, expires_at=expires_at))

        session.commit()

    return LoginResponse(token=token, username=username, expires_at=expires_at)


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/auth/logout")
def auth_logout(username: str = Depends(require_user), credentials: HTTPAuthorizationCredentials = Depends(bearer)):
    # Revoke current token.
    token = credentials.credentials if credentials else None
    if not token:
        return {"ok": True}
    with Session(engine) as session:
        row = session.get(AuthToken, token)
        if row:
            session.delete(row)
            session.commit()
    return {"ok": True}


@app.post("/auth/change-password", response_model=LoginResponse)
def auth_change_password(req: ChangePasswordRequest, username: str = Depends(require_user)):
    old_password = req.old_password
    new_password = req.new_password

    # Only DB-backed users can change password.
    with Session(engine) as session:
        user = session.get(User, username)
        if not user:
            raise HTTPException(status_code=400, detail="Password change not available for this user")

        if not verify_password(old_password, user.password_salt_hex, user.password_hash_hex):
            raise HTTPException(status_code=401, detail="Invalid old password")

        salt_hex, hash_hex = make_password_record(new_password)
        user.password_salt_hex = salt_hex
        user.password_hash_hex = hash_hex

        # Revoke all existing tokens for this user and issue a new one.
        tokens = session.exec(select(AuthToken).where(AuthToken.username == username)).all()
        for t in tokens:
            session.delete(t)

        now = datetime.utcnow()
        expires_at = now + timedelta(days=TOKEN_TTL_DAYS)
        token = secrets.token_urlsafe(32)
        session.add(AuthToken(token=token, username=username, created_at=now, expires_at=expires_at))
        session.add(user)
        session.commit()

    return LoginResponse(token=token, username=username, expires_at=expires_at)


@app.post("/auth/delete-account")
def auth_delete_account(req: DeleteAccountRequest, username: str = Depends(require_user)):
    """Self-service deletion: deletes tokens, bookings and (if present) the DB user."""

    password = req.password

    # Verify password against DB user if present; otherwise fallback to env users.
    with Session(engine) as session:
        user = session.get(User, username)
        if user:
            if not verify_password(password, user.password_salt_hex, user.password_hash_hex):
                raise HTTPException(status_code=401, detail="Invalid password")
        else:
            expected = APP_USERS.get(username)
            if expected is None or not secrets.compare_digest(password, expected):
                raise HTTPException(status_code=401, detail="Invalid password")

        deleted = delete_user_data(session, username)
        session.commit()
        return {"ok": True, **deleted}


def find_active_coverage(
    session: Session, desk_id: int, day: date
) -> Optional[StaffCoverage]:
    return session.exec(
        select(StaffCoverage).where(
            StaffCoverage.desk_id == desk_id,
            StaffCoverage.start_day <= day,
            StaffCoverage.end_day >= day,
        )
    ).first()


def check_coverage_overlap(
    session: Session, desk_id: int, start_day: date, end_day: date, exclude_id: Optional[int] = None
) -> bool:
    """
    Returns True if there is an overlapping coverage for the desk.
    Overlap condition: not (new_end < old_start or new_start > old_end)
    """
    q = select(StaffCoverage).where(StaffCoverage.desk_id == desk_id)
    rows = session.exec(q).all()
    for r in rows:
        if exclude_id is not None and r.id == exclude_id:
            continue
        if not (end_day < r.start_day or start_day > r.end_day):
            return True
    return False


def delete_all_bookings_for_desk(session: Session, desk_id: int) -> int:
    """
    Deletes ALL bookings for a desk (all days, AM/PM).
    Returns how many were deleted.
    """
    rows = session.exec(select(Booking).where(Booking.desk_id == desk_id)).all()
    for b in rows:
        session.delete(b)
    return len(rows)


# ----------------------------
# User endpoints (Bearer token)
# ----------------------------
@app.get("/desks", response_model=List[DeskStatusOut])
def get_desks(day: date = Query(..., description="YYYY-MM-DD"), username: str = Depends(require_user)):
    """
    Returns all desks with:
    - For THESIS desks: AM/PM bookings for the requested day
    - For STAFF desks: holder_name + current_occupant for the requested day
      (current_occupant = temp occupant if holder is away, otherwise holder)
    """
    with Session(engine) as session:
        desks = session.exec(select(Desk).order_by(Desk.row, Desk.col)).all()
        bookings = session.exec(select(Booking).where(Booking.day == day)).all()

        booking_idx: Dict[Tuple[int, Slot], str] = {}
        for b in bookings:
            booking_idx[(b.desk_id, b.slot)] = b.booked_by

        out: List[DeskStatusOut] = []
        for d in desks:
            status = DeskStatusOut(
                id=d.id,
                row=d.row,
                col=d.col,
                desk_type=d.desk_type,
                label=d.label,
                holder_name=d.holder_name,
            )

            if d.desk_type == DeskType.THESIS:
                status.booking_am = booking_idx.get((d.id, Slot.AM))
                status.booking_pm = booking_idx.get((d.id, Slot.PM))

            elif d.desk_type == DeskType.STAFF:
                cov = find_active_coverage(session, d.id, day)
                if cov:
                    status.holder_away = True
                    status.away_start = cov.start_day
                    status.away_end = cov.end_day
                    status.away_temp_occupant = cov.temp_occupant
                    status.current_occupant = cov.temp_occupant
                else:
                    status.current_occupant = d.holder_name

            out.append(status)

        _ = username  # auth guard; not used otherwise
        return out


@app.get("/bookings", response_model=List[BookingOut])
def list_bookings(day: date = Query(...), username: str = Depends(require_user)):
    with Session(engine) as session:
        rows = session.exec(
            select(Booking).where(Booking.day == day).order_by(Booking.slot, Booking.desk_id)
        ).all()
        _ = username  # auth guard; not used otherwise
        return [BookingOut(id=b.id, desk_id=b.desk_id, day=b.day, slot=b.slot, booked_by=b.booked_by) for b in rows]


@app.post("/bookings", response_model=List[BookingOut])
def create_booking(req: BookingCreate, username: str = Depends(require_user)):
    # Tie bookings to the authenticated user to prevent spoofing.
    booked_by = username

    if not req.am and not req.pm:
        raise HTTPException(status_code=400, detail="Select at least AM or PM.")

    with Session(engine) as session:
        desk = session.get(Desk, req.desk_id)
        if not desk:
            raise HTTPException(status_code=404, detail="Desk not found.")
        if desk.desk_type != DeskType.THESIS:
            raise HTTPException(status_code=400, detail="Desk is not bookable (only 'tesisti').")

        created: List[Booking] = []
        for slot, enabled in [(Slot.AM, req.am), (Slot.PM, req.pm)]:
            if not enabled:
                continue

            b = Booking(desk_id=req.desk_id, day=req.day, slot=slot, booked_by=booked_by)
            session.add(b)
            try:
                session.commit()
                session.refresh(b)
                created.append(b)
            except Exception:
                session.rollback()

                desk_conflict = session.exec(
                    select(Booking).where(
                        Booking.desk_id == req.desk_id,
                        Booking.day == req.day,
                        Booking.slot == slot,
                    )
                ).first()
                if desk_conflict:
                    raise HTTPException(status_code=409, detail=f"Conflict: desk already booked for {slot}.")

                person_conflict = session.exec(
                    select(Booking).where(
                        Booking.booked_by == booked_by,
                        Booking.day == req.day,
                        Booking.slot == slot,
                    )
                ).first()
                if person_conflict:
                    raise HTTPException(status_code=409, detail=f"Conflict: {booked_by} already booked a desk for {slot}.")

                raise HTTPException(status_code=500, detail="Booking error.")

        return [BookingOut(id=b.id, desk_id=b.desk_id, day=b.day, slot=b.slot, booked_by=b.booked_by) for b in created]


@app.delete("/bookings/{booking_id}")
def delete_booking(booking_id: int, req: CancelRequest, username: str = Depends(require_user)):
    """
    Deletes a booking owned by the authenticated user.
    """
    # Keep request shape for backward compatibility, but enforce auth-bound ownership.
    if (req.booked_by or "").strip() and req.booked_by.strip() != username:
        raise HTTPException(status_code=403, detail="Name does not match authenticated user")

    with Session(engine) as session:
        b = session.get(Booking, booking_id)
        if not b:
            raise HTTPException(status_code=404, detail="Booking not found.")
        if b.booked_by != username:
            raise HTTPException(status_code=403, detail="Not allowed to delete this booking.")

        session.delete(b)
        session.commit()
        return {"ok": True}


# ----------------------------
# Admin API endpoints (Basic Auth)
# ----------------------------
@app.delete("/admin/users/{username}", dependencies=[Depends(require_admin)])
def admin_delete_user(username: str):
    """Admin deletion: deletes tokens, bookings and DB user record for a username."""

    u = normalize_name(username, "username")
    with Session(engine) as session:
        deleted = delete_user_data(session, u)
        session.commit()
        return {"ok": True, **deleted}


@app.patch("/desks/{desk_id}", response_model=DeskStatusOut, dependencies=[Depends(require_admin)])
def update_desk(desk_id: int, req: DeskUpdate, day: date = Query(..., description="YYYY-MM-DD")):
    """
    Admin update for desk_type/label/holder_name.

    IMPORTANT: If a desk changes from THESIS to STAFF/BLOCKED,
    all existing bookings for that desk are automatically deleted.
    """
    with Session(engine) as session:
        desk = session.get(Desk, desk_id)
        if not desk:
            raise HTTPException(status_code=404, detail="Desk not found.")

        old_type = desk.desk_type

        desk.desk_type = req.desk_type
        if req.label is not None:
            desk.label = req.label

        # Only meaningful for STAFF desks; we still allow storing it, but you can ignore it elsewhere.
        if req.holder_name is not None:
            desk.holder_name = req.holder_name.strip() or None

        # If desk stops being THESIS => delete all bookings for that desk
        deleted_count = 0
        if old_type == DeskType.THESIS and req.desk_type != DeskType.THESIS:
            deleted_count = delete_all_bookings_for_desk(session, desk_id)

        # If desk becomes STAFF, ensure it has some holder (optional)
        if desk.desk_type == DeskType.STAFF and (desk.holder_name is None or desk.holder_name.strip() == ""):
            desk.holder_name = f"Holder {desk.label}"

        session.add(desk)
        session.commit()
        session.refresh(desk)

        # Return status for requested day
        status = DeskStatusOut(
            id=desk.id,
            row=desk.row,
            col=desk.col,
            desk_type=desk.desk_type,
            label=desk.label,
            holder_name=desk.holder_name,
        )

        if desk.desk_type == DeskType.THESIS:
            bookings = session.exec(select(Booking).where(Booking.day == day, Booking.desk_id == desk_id)).all()
            status.booking_am = next((b.booked_by for b in bookings if b.slot == Slot.AM), None)
            status.booking_pm = next((b.booked_by for b in bookings if b.slot == Slot.PM), None)

        elif desk.desk_type == DeskType.STAFF:
            cov = find_active_coverage(session, desk_id, day)
            if cov:
                status.holder_away = True
                status.away_start = cov.start_day
                status.away_end = cov.end_day
                status.away_temp_occupant = cov.temp_occupant
                status.current_occupant = cov.temp_occupant
            else:
                status.current_occupant = desk.holder_name

        # You can inspect deleted_count in logs if needed; keeping response clean.
        # (If you want it in the response, we can add a field.)
        _ = deleted_count
        return status


@app.get("/coverages", response_model=List[CoverageOut], dependencies=[Depends(require_admin)])
def list_coverages(desk_id: Optional[int] = Query(None)):
    with Session(engine) as session:
        q = select(StaffCoverage).order_by(StaffCoverage.desk_id, StaffCoverage.start_day)
        if desk_id is not None:
            q = q.where(StaffCoverage.desk_id == desk_id)
        rows = session.exec(q).all()
        return [
            CoverageOut(
                id=c.id,
                desk_id=c.desk_id,
                start_day=c.start_day,
                end_day=c.end_day,
                temp_occupant=c.temp_occupant,
                note=c.note,
            )
            for c in rows
        ]


@app.post("/coverages", response_model=CoverageOut, dependencies=[Depends(require_admin)])
def create_coverage(req: CoverageCreate):
    temp_occupant = normalize_name(req.temp_occupant, "temp_occupant")

    if req.end_day < req.start_day:
        raise HTTPException(status_code=400, detail="end_day must be >= start_day.")

    with Session(engine) as session:
        desk = session.get(Desk, req.desk_id)
        if not desk:
            raise HTTPException(status_code=404, detail="Desk not found.")
        if desk.desk_type != DeskType.STAFF:
            raise HTTPException(status_code=400, detail="Coverage can only be set for STAFF desks.")

        if check_coverage_overlap(session, req.desk_id, req.start_day, req.end_day):
            raise HTTPException(status_code=409, detail="Coverage overlaps with an existing one for this desk.")

        cov = StaffCoverage(
            desk_id=req.desk_id,
            start_day=req.start_day,
            end_day=req.end_day,
            temp_occupant=temp_occupant,
            note=req.note or "",
        )
        session.add(cov)
        session.commit()
        session.refresh(cov)

        return CoverageOut(
            id=cov.id,
            desk_id=cov.desk_id,
            start_day=cov.start_day,
            end_day=cov.end_day,
            temp_occupant=cov.temp_occupant,
            note=cov.note,
        )


@app.delete("/coverages/{coverage_id}", dependencies=[Depends(require_admin)])
def delete_coverage(coverage_id: int):
    with Session(engine) as session:
        cov = session.get(StaffCoverage, coverage_id)
        if not cov:
            raise HTTPException(status_code=404, detail="Coverage not found.")
        session.delete(cov)
        session.commit()
        return {"ok": True}


@app.post("/coverages/clear", dependencies=[Depends(require_admin)])
def clear_coverages(desk_id: int = Query(...)):
    """Delete all coverages for a given desk."""
    with Session(engine) as session:
        rows = session.exec(select(StaffCoverage).where(StaffCoverage.desk_id == desk_id)).all()
        for r in rows:
            session.delete(r)
        session.commit()
        return {"ok": True, "deleted": len(rows)}


# ----------------------------
# Admin Web UI (Basic Auth)
# ----------------------------
@app.get("/admin/desks", response_class=HTMLResponse, dependencies=[Depends(require_admin)])
def admin_desks(day: date = Query(..., description="YYYY-MM-DD")):
    """
    Admin grid page:
    - Change desk type + label
    - Edit holder_name for STAFF desks
    - Set vacation coverage (start/end + temp occupant)
    - Shows AM/PM bookings for THESIS desks
    - Shows current occupant / away badge for STAFF desks
    """
    with Session(engine) as session:
        desks = session.exec(select(Desk).order_by(Desk.row, Desk.col)).all()
        bookings = session.exec(select(Booking).where(Booking.day == day)).all()
        coverages = session.exec(select(StaffCoverage)).all()

    booking_idx = {(b.desk_id, b.slot): b.booked_by for b in bookings}

    # Active coverage per desk for the selected day (if any)
    active_cov: Dict[int, StaffCoverage] = {}
    for c in coverages:
        if c.start_day <= day <= c.end_day:
            # if multiple exist (shouldn't due to overlap check), first wins
            active_cov.setdefault(c.desk_id, c)

    grid = [[None for _ in range(6)] for _ in range(4)]
    for d in desks:
        grid[d.row][d.col] = d

    def option(selected: bool, value: str, text: str) -> str:
        return f'<option value="{value}"{" selected" if selected else ""}>{text}</option>'

    rows_html = ""
    for r in range(4):
        rows_html += "<tr>"
        for c in range(6):
            d = grid[r][c]
            if d is None:
                rows_html += "<td style='padding:8px;border:1px solid #ddd;'>N/A</td>"
                continue

            # THESIS booking badges
            am = booking_idx.get((d.id, Slot.AM))
            pm = booking_idx.get((d.id, Slot.PM))
            thesis_badges = ""
            if am:
                thesis_badges += f"<div style='margin-top:6px;font-size:12px;'>🟦 AM: {am}</div>"
            if pm:
                thesis_badges += f"<div style='margin-top:2px;font-size:12px;'>🟪 PM: {pm}</div>"

            # STAFF occupancy info
            staff_info = ""
            if d.desk_type == DeskType.STAFF:
                cov = active_cov.get(d.id)
                if cov:
                    staff_info = (
                        f"<div style='margin-top:6px;font-size:12px;'>🏖 Holder away "
                        f"({cov.start_day.isoformat()} → {cov.end_day.isoformat()})</div>"
                        f"<div style='margin-top:2px;font-size:12px;'>👤 Temp occupant: {cov.temp_occupant}</div>"
                    )
                else:
                    staff_info = (
                        f"<div style='margin-top:6px;font-size:12px;'>👤 Current occupant: {d.holder_name or '—'}</div>"
                    )

            # Desk editor form
            holder_input = ""
            if d.desk_type == DeskType.STAFF:
                holder_val = (d.holder_name or "").replace('"', "&quot;")
                holder_input = (
                    "<div style='margin-top:6px'>"
                    f"<input name='holder_name' value=\"{holder_val}\" placeholder='Holder name' style='width:140px'/>"
                    "</div>"
                )
            else:
                holder_input = "<input type='hidden' name='holder_name' value=''/>"

            label_val = (d.label or "").replace('"', "&quot;")

            editor_form = (
                f"<form method='post' action='/admin/desks/{d.id}?day={day.isoformat()}'>"
                f"<div style='font-weight:600'>{d.label} (id {d.id})</div>"
                f"<div style='font-size:12px;color:#444'>r{d.row+1} c{d.col+1}</div>"
                f"<div style='margin-top:6px'>"
                f"<select name='desk_type'>"
                f"{option(d.desk_type == DeskType.STAFF, 'staff', 'staff')}"
                f"{option(d.desk_type == DeskType.THESIS, 'tesisti', 'tesisti')}"
                f"{option(d.desk_type == DeskType.BLOCKED, 'bloccata', 'bloccata')}"
                f"</select>"
                f"</div>"
                f"<div style='margin-top:6px'>"
                f"<input name='label' value=\"{label_val}\" style='width:100px'/>"
                f"</div>"
                f"{holder_input}"
                f"<button style='margin-top:6px'>Save</button>"
                f"</form>"
            )

            # Coverage form for STAFF
            coverage_form = ""
            if d.desk_type == DeskType.STAFF:
                coverage_form = (
                    f"<hr style='margin:10px 0'/>"
                    f"<div style='font-size:12px;font-weight:600;'>Set vacation/coverage</div>"
                    f"<form method='post' action='/admin/coverages?day={day.isoformat()}'>"
                    f"<input type='hidden' name='desk_id' value='{d.id}'/>"
                    f"<div style='margin-top:6px;font-size:12px;'>Start</div>"
                    f"<input name='start_day' value='{day.isoformat()}' style='width:140px'/>"
                    f"<div style='margin-top:6px;font-size:12px;'>End</div>"
                    f"<input name='end_day' value='{day.isoformat()}' style='width:140px'/>"
                    f"<div style='margin-top:6px;font-size:12px;'>Temp occupant</div>"
                    f"<input name='temp_occupant' value='' placeholder='Name' style='width:140px'/>"
                    f"<div style='margin-top:6px;font-size:12px;'>Note</div>"
                    f"<input name='note' value='' placeholder='Optional' style='width:140px'/>"
                    f"<button style='margin-top:8px'>Add</button>"
                    f"</form>"
                    f"<form method='post' action='/admin/coverages/clear?desk_id={d.id}&day={day.isoformat()}'>"
                    f"<button style='margin-top:6px'>Clear all coverages</button>"
                    f"</form>"
                )

            # Compose cell
            cell = editor_form
            if d.desk_type == DeskType.THESIS:
                cell += thesis_badges
            if d.desk_type == DeskType.STAFF:
                cell += staff_info + coverage_form

            rows_html += f"<td style='vertical-align:top;padding:8px;border:1px solid #ddd'>{cell}</td>"

        rows_html += "</tr>"

    html = f"""
    <html>
      <head>
        <meta charset="utf-8"/>
        <title>Admin — Desks</title>
      </head>
      <body style="font-family: Arial, sans-serif; padding: 16px;">
        <h2>Admin — Desks (day: {day.isoformat()})</h2>

        <div style="margin-bottom:12px;">
          Change day:
          <form method="get" action="/admin/desks" style="display:inline;">
            <input name="day" value="{day.isoformat()}" />
            <button>Go</button>
          </form>
        </div>

        <table style="border-collapse:collapse;">
          {rows_html}
        </table>

        <p style="margin-top:14px;color:#555;font-size:12px;">
          Admin area protected by HTTP Basic.
          Tip: set LAB_ADMIN_USER / LAB_ADMIN_PASS environment variables.
        </p>
      </body>
    </html>
    """
    return HTMLResponse(content=html)


@app.post("/admin/desks/{desk_id}", dependencies=[Depends(require_admin)])
def admin_update_desk(
    desk_id: int,
    day: date = Query(...),
    desk_type: str = Form(...),
    label: str = Form(...),
    holder_name: str = Form(""),
):
    if desk_type not in {"staff", "tesisti", "bloccata"}:
        raise HTTPException(status_code=400, detail="Invalid desk_type.")

    label = (label or "").strip()
    holder_name = (holder_name or "").strip()

    with Session(engine) as session:
        desk = session.get(Desk, desk_id)
        if not desk:
            raise HTTPException(status_code=404, detail="Desk not found.")

        old_type = desk.desk_type
        new_type = DeskType(desk_type)

        desk.desk_type = new_type
        desk.label = label or desk.label

        if new_type == DeskType.STAFF:
            desk.holder_name = holder_name or desk.holder_name or f"Holder {desk.label}"
        else:
            # Keep holder_name in DB or clear it—your choice. Here we keep it.
            # desk.holder_name = desk.holder_name
            pass

        # Auto-cancel bookings if leaving THESIS
        if old_type == DeskType.THESIS and new_type != DeskType.THESIS:
            delete_all_bookings_for_desk(session, desk_id)

        session.add(desk)
        session.commit()

    return RedirectResponse(url=f"/admin/desks?day={day.isoformat()}", status_code=303)


@app.post("/admin/coverages", dependencies=[Depends(require_admin)])
def admin_add_coverage(
    day: date = Query(...),
    desk_id: int = Form(...),
    start_day: str = Form(...),
    end_day: str = Form(...),
    temp_occupant: str = Form(...),
    note: str = Form(""),
):
    try:
        s = date.fromisoformat(start_day)
        e = date.fromisoformat(end_day)
    except ValueError:
        raise HTTPException(status_code=400, detail="Dates must be YYYY-MM-DD.")

    temp_occupant = normalize_name(temp_occupant, "temp_occupant")
    if e < s:
        raise HTTPException(status_code=400, detail="End date must be >= start date.")

    with Session(engine) as session:
        desk = session.get(Desk, desk_id)
        if not desk:
            raise HTTPException(status_code=404, detail="Desk not found.")
        if desk.desk_type != DeskType.STAFF:
            raise HTTPException(status_code=400, detail="Coverage can only be set for STAFF desks.")

        if check_coverage_overlap(session, desk_id, s, e):
            raise HTTPException(status_code=409, detail="Coverage overlaps an existing one for this desk.")

        cov = StaffCoverage(desk_id=desk_id, start_day=s, end_day=e, temp_occupant=temp_occupant, note=note or "")
        session.add(cov)
        session.commit()

    return RedirectResponse(url=f"/admin/desks?day={day.isoformat()}", status_code=303)


@app.post("/admin/coverages/clear", dependencies=[Depends(require_admin)])
def admin_clear_coverages(desk_id: int = Query(...), day: date = Query(...)):
    with Session(engine) as session:
        rows = session.exec(select(StaffCoverage).where(StaffCoverage.desk_id == desk_id)).all()
        for r in rows:
            session.delete(r)
        session.commit()

    return RedirectResponse(url=f"/admin/desks?day={day.isoformat()}", status_code=303)
