# F1 Strategy Lab

Race-strategy dashboard and AI chat built on real F1 data, running entirely on a Windows laptop for **₹0**:
no paid APIs, no LLM tokens, no subscriptions, no hosted database.

| Layer | Technology | Cost |
|---|---|---|
| Frontend | Angular 22 + TypeScript, Apache ECharts (`ngx-echarts`) | free |
| Backend | Java 21+ / Spring Boot 4.1, Spring Data JPA + Hibernate | free |
| Database | H2 file database (`AI/data/f1db.mv.db`) | free, local |
| GenAI | Ollama + a local model (default `llama3.2`), via Spring AI 2.0 | free, local |
| F1 data | [Jolpica F1 API](https://github.com/jolpica/jolpica-f1) (Ergast-compatible, no key) | free |
| Tests | JUnit 5 + Mockito + AssertJ, Vitest | free |

## How the AI stays honest

The model never computes statistics. For each question the backend:

1. Parses the question (drivers mentioned or selected, and "N laps earlier/later" what-ifs). See `QuestionParser`.
2. Computes the facts deterministically in `StrategyAnalyzer`: stints, clean-lap pace, consistency, pit-stop
   time loss, who passed whom and why (undercut, or a rival that simply hadn't pitted yet), and a degradation-model what-if.
3. Sends only those facts to the local LLM, with instructions to quote numbers only from them (`RaceChatService`).

The UI shows the exact facts under **"Data used"** for every answer. If Ollama is not running,
the app still works and returns the computed facts without the written explanation.

**Data limits:** Jolpica has lap times, positions, pit-stop laps and pit-lane durations. It has **no tyre compounds**,
so stints are the laps between stops. Safety Car/VSC laps are *inferred* (field median lap >15% slower than usual).

## One-time setup (all free)

1. **Java 21+**: already installed (Temurin 25). Maven isn't needed because `mvnw.cmd` downloads it.
2. **Node 20+**: already installed (24.x).
3. **Ollama**: install from <https://ollama.com/download>, then:
   ```powershell
   ollama pull llama3.2        # ~2 GB, runs on CPU; for better answers: qwen2.5:7b or llama3.1:8b
   ```
   To change the model, edit `spring.ai.ollama.chat.model` in `AI/src/main/resources/application.properties`.

## Run

```powershell
# Terminal 1: backend on http://localhost:8080
cd AI
.\mvnw.cmd spring-boot:run

# Terminal 2: frontend on http://localhost:4200 (proxies /api to 8080)
cd f1-ui
npm install      # first time only
npm start
```

Open <http://localhost:4200>, pick a season and race. The first open of a race downloads it from Jolpica
(~15–20 s, about 16 requests within the free rate limit). After that it is served from H2 and works offline.
Deep links work: `http://localhost:4200/?season=2024&round=1`.

## Tests

```powershell
cd AI;    .\mvnw.cmd test            # analyzer maths, question parsing, AI grounding/fallback, Spring context
cd f1-ui; npx ng test --watch=false  # component + formatting tests
```

## REST API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/seasons/{season}/races` | Season calendar (cached in H2) |
| POST | `/api/seasons/{season}/races/{round}/import?force=false` | Download a race into H2 |
| GET | `/api/races` | Imported races |
| GET | `/api/races/{id}/summary` | Winner, strategies, gainers/losers, SC laps, fastest stop |
| GET | `/api/races/{id}/laps` | Lap times + positions for charts |
| GET | `/api/races/{id}/drivers/{driverId}/strategy` | Stints, pace, consistency, degradation |
| GET | `/api/races/{id}/drivers/{driverId}/pit-impact` | Time lost and positions lost per stop, with reasons |
| GET | `/api/races/{id}/drivers/{driverId}/what-if?stop=1&lapsEarlier=5` | Moved-stop estimate (negative = later) |
| GET | `/api/races/{id}/compare?a=..&b=..` | Head-to-head and gap by lap |
| POST | `/api/ai/chat` `{raceId, question, driverIds}` | Grounded AI answer + facts used |
| GET | `/api/ai/status` | Is Ollama running / model pulled? |

## What-if model (and its limits)

Each stint's clean laps are fitted as `lap time = intercept + slope × tyre age`. Moving a stop replaces the affected
laps with the fitted pace for the tyre age they would have had. The real stop's pit loss is reused, and SC laps keep
their real times. It does **not** model traffic, rival reactions, compound choice or fuel separately. The response lists
these assumptions and warns when it extrapolates beyond observed tyre ages or when the real stop was made under a Safety Car.
