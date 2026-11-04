# SlotMind

SlotMind is a front-desk appointment rescheduling app. Staff can book visits, respond to provider or slot changes, and review suggested replacement times.

## Run locally

Create a local environment file from the template, then set a local PostgreSQL password in it:

```powershell
Copy-Item backend/.env.example backend/.env
```

Start the PostgreSQL container from `backend` first:

```powershell
docker compose up -d postgres
```

Start the backend from `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

Start the Angular app from `frontend` in a second terminal:

```powershell
npm start
```

Open <http://localhost:4200>. The backend runs on port 8080 and creates or updates the PostgreSQL schema on startup, then seeds a small clinic schedule on first start. The local `.env` file is ignored by Git and supplies both Compose and Spring Boot credentials. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to use a different PostgreSQL instance. H2 remains available by overriding `DB_URL` with an H2 JDBC URL.

## Demo flow

1. Select **Set offline** next to Dr. Sarah Chen. Her confirmed visits move to review and receive alternate times.
2. Accept a replacement to confirm the new provider and slot in one transaction.
3. Use **New appointment** to book an open time, or cancel a confirmed visit to cancel its slot and trigger the same review flow.
4. Set a provider back online with **Restore**.

The dashboard polls for changes every five seconds.

## Optional LLM advisor

The rule-based scheduler works without external services. To enable an OpenAI-compatible chat completion endpoint, configure these environment variables before starting the backend:

```powershell
$env:AI_ENABLED = "true"
$env:AI_API_KEY = "your-api-key"
$env:AI_BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
$env:AI_MODEL = "llama-3.3-70b-versatile"
```

LLM output is restricted to actual open slots and falls back to the rule-based advisor when the request or response cannot be used.

## PostgreSQL

The Compose database is exposed on port `5433` so it can run alongside a local PostgreSQL instance on port `5432`. Copy `.env.example` to `.env` and set the password there. The database user and name default to `hackathon_user` and `hackathon_db`.

To use a different database, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the backend run configuration. Do not commit `.env` or real credentials.

## API overview

- `GET /api/appointments?status=CONFIRMED`
- `POST /api/appointments`
- `GET /api/providers` and `PATCH /api/providers/{id}/status`
- `GET /api/slots` and `PATCH /api/slots/{id}/status`
- `POST /api/appointments/{id}/suggest`
- `GET /api/suggestions` and `PATCH /api/suggestions/{id}`

See [ADR.md](ADR.md) for the key design decisions and current tradeoffs.
