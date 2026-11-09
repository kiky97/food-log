# Food Log — AI-Powered Food Logging

[![CI](https://github.com/kiky97/food-log/actions/workflows/ci.yml/badge.svg)](https://github.com/kiky97/food-log/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A food logging application that turns natural-language meal descriptions—spoken or typed—into structured records and nutrient summaries.

The Spring Boot backend extracts food names, quantities, units, and meal types using an LLM. It then matches foods against a multilingual catalogue, converts portions into grams, calculates nutrients, and stores the results in MongoDB.

A React frontend supports text input, audio recording, food confirmation, and daily summaries.

## Processing Pipeline

```text
Voice → Speech-to-text → Text → LLM entity extraction
                                      ↓
                               Food matching
                                      ↓
                               Portion conversion
                                      ↓
                               Nutrient calculation
                                      ↓
                                   MongoDB
```

The LLM interprets the meal description. Catalogue data and application code handle food matching, portion conversion, and nutrient calculation.

## Features

- **Voice and text input:** record meals using natural language.
- **Structured extraction:** Gemini JSON response schemas or AWS Bedrock Converse with forced tool use, followed by application-level validation.
- **Multilingual food matching:** English, German, and Chinese names and aliases, with normalization, token scoring, and Jaro–Winkler typo tolerance for eligible non-Chinese tokens.
- **User confirmation:** ambiguous and unmatched items are excluded from nutrient totals until resolved.
- **Portion conversion:** convert pieces, slices, bowls, and other units into grams using food-specific weights or generic defaults.
- **Nutrient summaries:** calculate daily and per-meal totals from matched and confirmed items.
- **Resilient API calls:** configurable timeouts and exponential-backoff retries for transient failures.
- **In-memory catalogue:** avoid database reads for every matching request and refresh the cache after CSV imports.

## Technology Stack

| Area | Technologies |
|---|---|
| Backend | Java 17+, Spring Boot 3.5, Spring Web, Bean Validation |
| Database | MongoDB, Spring Data MongoDB |
| AI integrations | Gemini API, AWS Bedrock Converse API with tool use |
| Frontend | React 19, TypeScript, Vite |
| API documentation | springdoc, Swagger UI |
| Testing | JUnit 5, Mockito, Testcontainers, Vitest |

## Quick Start

### Prerequisites

- JDK 17 or later
- Docker for the local MongoDB instance
- Node.js and npm to run the frontend

### Start the Backend

From the repository root, start MongoDB:

```bash
docker compose up -d
```

Start the application:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
./mvnw.cmd spring-boot:run
```

Available endpoints:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

The application uses **mock providers by default**, so no API credentials are required. On the first run, it imports 45 sample food entries.

### Create a Food Log

Example using Bash:

```bash
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","text":"I had two boiled eggs, a bowl of rice and an apple for breakfast"}'
```

Retrieve the daily summary:

```bash
curl "http://localhost:8080/api/v1/logs/summary?userId=u1"
```

## AI Provider Configuration

### Gemini

Create an API key in [Google AI Studio](https://aistudio.google.com/apikey), then configure the environment variables before starting the application.

PowerShell:

```powershell
$env:GEMINI_API_KEY = "your-api-key"
$env:LLM_PROVIDER = "gemini"
$env:STT_PROVIDER = "gemini"
./mvnw.cmd spring-boot:run
```

| Variable | Default | Description |
|---|---|---|
| `GEMINI_API_KEY` | Empty | Google AI Studio API key, sent in a request header |
| `LLM_PROVIDER` | `mock` | Entity extraction provider: `gemini`, `bedrock`, or `mock` |
| `STT_PROVIDER` | `mock` | Speech-to-text provider: `gemini` or `mock` |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Gemini model identifier |
| `MONGODB_URI` | `mongodb://localhost:27017/foodlog` | MongoDB connection URI |

> **Mock provider limitations:** the mock entity extractor handles only simple English sentences. The mock speech-to-text provider cannot transcribe real audio; upload a `.txt` file to simulate a transcript. Use a real entity extraction provider for Chinese, conversational language, and complex sentences.
>
> Bedrock supports entity extraction in this project. Gemini supports both entity extraction and speech-to-text.
>
> Recommended audio formats: WAV, MP3, OGG, or FLAC. Maximum upload size: 15 MB.

### AWS Bedrock

With `LLM_PROVIDER=bedrock`, `BedrockEntityExtractionClient` uses the **Converse API with forced tool use** to obtain structured output.

Credentials are resolved through the standard AWS default credentials provider chain, including environment variables, shared credentials files, and IAM roles. AWS credentials are not hard-coded in application code.

PowerShell example:

```powershell
$env:AWS_ACCESS_KEY_ID = "your-access-key-id"
$env:AWS_SECRET_ACCESS_KEY = "your-secret-access-key"

# Also set AWS_SESSION_TOKEN when using temporary credentials.

$env:AWS_REGION = "us-east-1"
$env:LLM_PROVIDER = "bedrock"
./mvnw.cmd spring-boot:run
```

| Variable | Default | Description |
|---|---|---|
| `AWS_REGION` | `us-east-1` | AWS region for Bedrock requests |
| `BEDROCK_MODEL` | `anthropic.claude-3-5-haiku-20241022-v1:0` | Model identifier; must support the tool-use configuration used by this implementation |

Ensure that your AWS identity has the necessary permissions and access to the selected model in the configured region. Missing permissions or model access can result in an `AccessDeniedException`.

## Frontend

The frontend is located in `frontend/` and uses React 19, Vite, and TypeScript, without a UI component or charting library.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

Start the backend first. The Vite development server proxies `/api` requests to `localhost:8080`, keeping browser requests on the same origin during development.

### Preview Without the Backend

To preview the interface without Spring Boot or MongoDB:

```bash
npm run dev:mock
```

> The frontend mock backend recognizes eight foods and uses simple keyword parsing. It is intended for interface development and demonstrations and does not reproduce the backend's matching algorithm.

### Frontend Features

- Enter text, record audio using a microphone, or upload an audio file.
- Browser recordings are decoded and converted to 16 kHz mono WAV before upload.
- View the original text, matched foods, portion weights, and calories.
- Estimated portions are marked with `≈`.
- Resolve uncertain items by selecting a candidate or searching the catalogue.
- Correct existing matches using **Wrong food?**.
- View daily calories, macronutrient energy shares, fibre, sugar, salt, and per-meal calories.
- See how many unresolved items are excluded from the totals.
- Use a responsive layout with dark mode.

The user identifier is stored locally in the browser. There is no login flow.

### Frontend Checks

```bash
npm test        # Unit and component tests with Vitest
npm run build   # Type-check and build into frontend/dist
```

## API

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/logs` | Create a text log: `{userId, text, loggedAt?}` |
| POST | `/api/v1/logs/voice` | Create a voice log using multipart fields: `userId`, `audio`, and optional `loggedAt` |
| POST | `/api/v1/logs/{logId}/items/{index}/confirm` | Confirm or correct a food match: `{foodId}` |
| GET | `/api/v1/logs?userId=&date=` | Retrieve logs for a date; defaults to today in the application time zone |
| GET | `/api/v1/logs/summary?userId=&date=` | Retrieve daily and per-meal nutrient totals |
| GET | `/api/v1/foods/search?q=` | Search foods using fuzzy matching and return similarity scores |
| GET | `/api/v1/foods/{id}` | Retrieve a food entry |
| POST | `/api/v1/foods/import` | Import a CSV catalogue; existing entries with the same ID are updated |

## Matching and Confirmation

The matcher compares the extracted name against each food's English, German, and Chinese names and aliases.

Normalization includes lowercasing, accent and punctuation removal, selected English stop-word removal, and simple English singularization.

Token scores combine query coverage and candidate coverage:

```text
score = 0.7 × query coverage + 0.3 × candidate coverage
```

Jaro–Winkler similarity provides typo tolerance for eligible non-Chinese tokens. Chinese matching uses exact comparisons and a limited substring rule.

### Match Statuses

| Status | Default condition | Included in nutrient totals? |
|---|---|---|
| `MATCHED` | Similarity ≥ 0.85 and no applicable near-tie | Yes |
| `NEEDS_CONFIRMATION` | Similarity ≥ 0.6 but automatic acceptance conditions are not met | No |
| `UNMATCHED` | Similarity < 0.6, or no candidate is found | No |
| `CONFIRMED` | The user selects a food through the confirmation endpoint | Yes |

A near-tie occurs when the top two foods differ by less than `0.03` and the highest score is below `1.0`. The current implementation exempts a highest score of exactly `1.0` from this check.

Similarity scores are matching heuristics, not calibrated probabilities.

After confirmation, the application recalculates the item's portion and nutrients and updates the log totals.

## Portion Conversion and Nutrient Calculation

Portion conversion follows this priority:

```text
Fixed unit conversion → food-specific portion weight → generic default
```

For example, kilograms are converted directly into grams, while units such as `piece` or `bowl` use the food's portion weights when available.

If a generic default is required, `portionEstimated` is set to `true`.

Nutrients are calculated from catalogue values per 100 grams:

```text
Consumed nutrients = nutrients per 100 g × consumed grams / 100
```

Daily and per-meal summaries include only matched or confirmed items. Unresolved items are counted separately.

## Project Structure

Backend package directories are under `src/main/java/com/foodlog/`.

```text
frontend/     React frontend
controller/   REST endpoints: FoodLogController and FoodController
dto/          Request and response objects
service/      Pipeline orchestration, catalogue cache, summaries, and CSV import
client/       Speech-to-text, entity extraction, and external API clients
matching/     Food-name normalization and fuzzy matching
portion/      Unit normalization and conversion to grams
domain/       MongoDB documents and value objects
repository/   Spring Data MongoDB repositories
exception/    Centralized error handling with ProblemDetail
```

Speech-to-text and entity extraction are defined through interfaces. Implementations are selected through configuration, allowing additional providers to be added without changing the main pipeline.

Food logs use a compound MongoDB index on `userId` and `logDate`. Daily and per-meal summaries are calculated in the application from the selected day's records.

## Food Data

The 45 entries in `src/main/resources/data/foods_sample.csv` are **sample data with approximate nutrient values**. They are intended for demonstrations and tests and are **not the official Swiss Food Composition Database**.

To import data from the Swiss Food Composition Database:

1. Obtain the data from the [official website](https://naehrwertdaten.ch) and review its usage terms.
2. Convert it to a UTF-8 CSV using the columns below. Nutrient values must be expressed per 100 grams. Separate aliases with `;`; portion-weight fields may be left empty.
3. Upload the CSV through `POST /api/v1/foods/import`.

```csv
id,name_en,name_de,name_zh,aliases,category,kcal,protein_g,fat_g,carb_g,fiber_g,sugar_g,salt_g,piece_g,slice_g,cup_g,bowl_g,serving_g
```

## Testing

Run backend tests:

```bash
./mvnw test
```

Test coverage includes:

- **Unit tests:** name normalization, food matching, portion conversion, CSV parsing, and the `FoodLogService` pipeline using fake repositories without Docker.
- **Gemini client tests:** simulated HTTP responses using `MockRestServiceServer`, covering retries, malformed JSON, and rate limiting.
- **Bedrock client tests:** a mocked `BedrockRuntimeClient`, covering tool-use parsing, rate-limit retries, and network failures.
- **Integration tests:** `FoodLogApiIntegrationTest` starts MongoDB through Testcontainers. These tests require Docker and are automatically skipped if Docker is unavailable. The GitHub Actions CI configuration runs them on an Ubuntu runner with Docker.
- **Matcher evaluation:** `FoodMatcherTest` includes labeled queries covering plurals, spelling errors, word order, German, and Chinese. Reported automatic-matching accuracy reflects only the sample catalogue and must be re-evaluated for other datasets.

> The project sets `api.version=1.44` for Surefire to address compatibility between Docker Engine 29+ and Testcontainers 1.x. If you use Docker Engine older than version 25, remove this override from `pom.xml`.

## Known Limitations and Future Improvements

- Gemini integrations have been tested with simulated HTTP responses, and the Bedrock integration with a mocked SDK client. **They have not been verified end to end with live credentials.**
- **Microphone recording has not been verified in a real browser with a microphone.** Other interface flows have been exercised against the mock backend; integration between the frontend and real backend has not yet been verified.
- Food matching is lexical rather than semantic. Possible improvements include vector retrieval or LLM-based reranking of uncertain candidates.
- Portion weights are static and do not adjust for descriptions such as “a small bowl” or “a large bowl.”
- Volume conversions assume a density of 1 g/ml. This can introduce errors for foods with different densities, and the current implementation does not flag this assumption as an estimated portion.
- The food catalogue cache is local to each application instance. Multi-instance deployments would need coordinated refreshes or another cache strategy.
- There is no user authentication. The caller supplies `userId`.

## License

This project is licensed under the [MIT License](LICENSE).
