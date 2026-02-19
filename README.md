# VLSI Booking App

App di prenotazione scrivanie per laboratorio (backend FastAPI + app Android).

## Struttura
- `backend/` → API + pagina admin + DB (SQLite in locale, Postgres in produzione)
- `VLSIBooking/` → app Android (Jetpack Compose)

## Funzionalità principali
- Griglia scrivanie 4×6 con `desk_type`: `staff`, `tesisti`, `bloccata`
- Prenotazioni AM/PM per scrivanie `tesisti`
- Scrivanie `staff` con `holder_name` e gestione assenze (coverage) con “temporary occupant”
- Area admin web protetta da HTTP Basic

## Avvio backend (locale)
Da root progetto:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Endpoint utili:
- `GET /health`
- `GET /desks?day=YYYY-MM-DD`
- Swagger: `GET /docs`
- Admin: `GET /admin/desks?day=YYYY-MM-DD`

### Variabili d’ambiente (backend)
- `DATABASE_URL` (default: SQLite locale)
- `LAB_ADMIN_USER`, `LAB_ADMIN_PASS` (credenziali admin)
- `LAB_USERS` (fallback utenti, formato `alice:pass,bob:pass2`)
- `TOKEN_TTL_DAYS`, `BOOKINGS_RETENTION_DAYS`, `INACTIVE_USER_DAYS` (opzionali)

## App Android
Apri la cartella `VLSIBooking/` in Android Studio.

Il backend usato dall’app è configurato in `VLSIBooking/app/build.gradle.kts` tramite `BACKEND_BASE_URL` (deve terminare con `/` per Retrofit).

## Licenza
Questo progetto è rilasciato sotto **GNU AGPL v3** (vedi file `LICENSE`).

Nota: AGPL/GPL sono licenze copyleft, ma **non vietano l’uso commerciale**; impongono obblighi di redistribuzione del sorgente quando si distribuisce (e nel caso AGPL anche quando si offre il servizio via rete).
