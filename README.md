<p align="center">
  <img src="src/main/resources/static/img/logo-light.png" alt="FixIt" width="320">
</p>

<p align="center"><strong>A trusted marketplace and business toolkit for independent technicians.</strong></p>

FixIt connects customers with verified local technicians (electricians, plumbers, carpenters, AC and appliance repairers and more) and gives technicians the tools to run their work: profiles, bookings, price negotiation, job tracking, routes, payment records and earnings. Built for the Hack For Humanity hackathon.

---

## Contents
- [Why FixIt](#why-fixit)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [How it works](#how-it-works)
- [Pages](#pages)
- [REST API](#rest-api)
- [Project structure](#project-structure)
- [Testing](#testing)
- [Security and privacy notes](#security-and-privacy-notes)
- [Limitations and roadmap](#limitations-and-roadmap)

## Why FixIt
Independent technicians mostly find work by word of mouth, keep no records of what they earn, and have no protection against price disputes or unpaid jobs. Customers struggle to find skilled, honest help nearby and don't know what a fair price is.

FixIt makes the whole job transparent, from finding a technician to getting paid:

- **Trust:** every customer and technician is verified by an admin (NIC and uploaded documents) before they can trade, and technicians build a public rating.
- **Fair prices:** the admin sets a minimum and maximum price per profession. Customer and technician negotiate inside that range.
- **Simple money:** the platform takes a flat **10%** service charge, deducted from the technician's agreed price. The customer pays exactly the agreed price and the technician keeps 90%. The balance can be paid in cash or through the platform. Every payment and payout is recorded.

## Features

**For customers**
- Register with NIC, contact details, languages and map location. Wait for admin verification.
- Find technicians near you on a map, filtered by profession, language, gender, budget, minimum rating and distance, or save these as preferences.
- Describe a problem in English, Sinhala or Tamil and an AI model suggests the best-fit profession.
- Book a technician, bid inside the allowed price range, and follow the job through every status.
- Pay the service charge through the platform (with a proof file), pay the balance in cash or through the platform, and rate the technician afterwards.

**For technicians**
- Create a profile with several professions, each with skill level and notes, plus qualification and experience.
- Receive and counter booking offers inside the platform's price range.
- See the route to each job (car, motorbike, bicycle, walking) with distance, time and turn-by-turn steps.
- Confirm cash payments, see payouts recorded by the platform, and track earnings, jobs and customers on a dashboard.
- Get a public profile page with ratings and reviews.

**For admins**
- Verify or reject profiles with a reason, and open the documents users uploaded.
- Confirm platform payments (by proof file) and record payouts to technicians.
- Manage languages and profession price ranges without code changes.

## Tech stack
| Area | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Security (session login, BCrypt), Spring Data JPA, Bean Validation, Lombok |
| Database | H2 (file based, data survives restarts) |
| Front end | Thymeleaf pages, vanilla JavaScript, one CSS file, Leaflet and OpenStreetMap |
| Routes and address search | [Geoapify](https://www.geoapify.com) (OpenRouteService supported as an alternative) |
| AI problem matching | [Groq](https://groq.com) (`openai/gpt-oss-20b`), with a built-in keyword fallback |
| Tests | JUnit 5, Spring Boot Test, MockMvc, Mockito |

## Getting started

### Requirements
- JDK 17 or newer
- Maven 3.9 or newer
- Internet access (the map library and map tiles load from the web)

### Run
```bash
git clone <your-repository-url>
cd FixIt
mvn spring-boot:run
```
Open <http://localhost:8080> and log in as the seeded admin (below). The app works without any API key. Add the keys in [Configuration](#configuration) to turn on AI matching, routes and address search.

On first start the app creates:
- an **admin account** from `application.properties` (`admin@fixit.local` / `Admin@12345`). **Change this password before any real use.**
- the languages Sinhala, Tamil and English
- eight professions with LKR price ranges: Electrician, Plumber, Carpenter, Mason, Painter, AC Technician, Appliance Repair, Mechanic

Data is stored in `./data/fixit.mv.db` (git-ignored). Delete the `data/` folder to start fresh.

### Try it quickly
1. Register one customer and one technician at `/register`.
2. Log in as the admin and approve both on `/admin/home`.
3. As the technician, set a map location and add a profession on `/profile`.
4. As the customer, open `/customer/find`, search, and book. Negotiate a price, run the job, record the payments, and leave a rating.

## Configuration
Settings live in `src/main/resources/application.properties`. **Never put API keys in that file or in Git.** Use environment variables.

| Environment variable | Purpose | If missing |
|---|---|---|
| `GROQ_API_KEY` | AI problem-to-profession matching ([free key](https://console.groq.com)) | the keyword matcher is used instead |
| `GEOAPIFY_API_KEY` | routes for technicians and address search ([free key](https://www.geoapify.com)) | routes show "not configured"; you can still click the map |
| `ORS_API_KEY` | only if you switch to OpenRouteService | n/a |

In IntelliJ: *Run > Edit Configurations > Environment variables*. In a terminal:
```bash
# macOS / Linux
export GROQ_API_KEY=...  GEOAPIFY_API_KEY=...
# Windows PowerShell
$env:GROQ_API_KEY="..."; $env:GEOAPIFY_API_KEY="..."
```

| Property | Default | Meaning |
|---|---|---|
| `app.admin.email`, `app.admin.password`, `app.admin.name` | `admin@fixit.local`, `Admin@12345` | seeded admin account |
| `app.service-charge-percent` | `10` | platform service charge, deducted from the technician |
| `app.matching.provider` | `groq` | `groq` or `keyword` |
| `app.matching.groq.model` | `openai/gpt-oss-20b` | any text model on your Groq account |
| `app.routing.provider` | `geoapify` | `geoapify` or `ors` |
| `app.geocoding.country` | `LK` | limit address search to a country (ISO code); empty for worldwide |
| `spring.servlet.multipart.max-file-size` | `5MB` | limit for proof and document uploads |

## How it works
1. **Sign up** as Customer or Technician and pick the languages you speak. Status starts as `PENDING`.
2. **Verification:** upload your NIC (and certificates). An admin approves or rejects with a reason. Pending and rejected users can edit their profile but cannot book, bid or be listed. Editing a rejected profile resubmits it.
3. **Discovery:** nearest-first search with filters and saved preferences, or describe the problem and let the AI pick the profession.
4. **Booking and bidding:** the customer proposes a price inside the profession's range, then both sides counter or accept. Contact numbers stay hidden until the price is agreed.
5. **Job status:** `REQUESTED` → `NEGOTIATING` → `CONFIRMED` → `IN_PROGRESS` → `COMPLETED` → `PAID` (or `CANCELLED`).
6. **Payments** (records only, there is no payment gateway):
   - the 10% **service charge** is paid through the platform with a proof file (PNG, JPEG or PDF, up to 5 MB) and confirmed by an admin
   - the 90% **balance** is paid in cash (the technician confirms) or through the platform (proof, admin confirms)
   - when both are confirmed the booking becomes `PAID`
   - for platform-paid balances the admin pays the technician outside the app and records the payout reference
7. **Rating:** after a completed job the customer rates the technician from 1 to 5. The average feeds search filters and the public profile.

## Pages
| Page | Who | What |
|---|---|---|
| `/register`, `/login` | everyone | sign up and log in |
| `/profile` | customer, technician | edit details, map location with address search, languages, verification documents; customer preferences; technician qualification and professions |
| `/customer/find` | customer | search on the map or describe a problem, then book |
| `/bookings`, `/bookings/{id}` | customer, technician | jobs, negotiation, status, payments, rating; technicians also see routes |
| `/technicians/{id}` | anyone signed in | public technician profile with rating and reviews (no contact details) |
| `/technician/dashboard` | technician | earnings, payouts, jobs by status, upcoming jobs, customers |
| `/admin/home` | admin | verification queue with documents, platform payments, payouts, languages and price ranges |

## REST API
The pages use the REST API under `/api`, with the same session login (CSRF is disabled for `/api/**` only). Errors are JSON: `{"error": "...", "details": {...}}`.

| Area | Endpoints |
|---|---|
| Account | `GET /api/me`, `PUT /api/me`, `PUT /api/me/location`, `PUT /api/me/languages`, `GET /api/languages`, `GET /api/professions` |
| Documents | `POST /api/me/documents` (multipart `file`, `label`), `GET /api/me/documents`, `DELETE /api/me/documents/{id}`, `GET /api/documents/{id}/file` |
| Customer | `GET/PUT /api/customer/preferences`, `GET /api/discovery/technicians`, `POST /api/discovery/match` |
| Technician | `PUT /api/technician/profile`, `POST /api/technician/skills`, `DELETE /api/technician/skills/{id}`, `GET /api/technician/dashboard`, `GET /api/technician/bookings/{id}/routes`, `GET /api/technician/payouts` |
| Bookings | `POST /api/bookings`, `GET /api/bookings`, `GET /api/bookings/{id}`, `POST /api/bookings/{id}/offers`, `accept`, `cancel`, `start`, `complete`, `rating` |
| Payments | `POST /api/bookings/{id}/payments` (multipart), `GET /api/bookings/{id}/payments`, `GET /api/payments`, `GET /api/payments/{id}/proof`, `POST /api/payments/{id}/confirm`, `reject` |
| Places | `GET /api/geocode?q=`, `GET /api/technicians/{id}` |
| Admin | `GET /api/admin/users?status=`, `POST /api/admin/users/{id}/approve`, `reject`, `GET /api/admin/users/{id}/documents`, `POST /api/admin/languages`, `POST/PUT /api/admin/professions`, `GET /api/admin/payments/pending`, `GET /api/admin/payouts/pending`, `POST /api/admin/bookings/{id}/payout`, `GET /api/admin/payouts` |

## Project structure
```
src/main/java/com/serviceportal/
  config/       security rules, startup data seeder
  controller/   page controllers and controller/api (REST)
  dto/          request and response records
  model/        JPA entities and enums
  repository/   Spring Data repositories
  service/      business rules: bookings, payments, payouts, discovery,
                matching (Groq / keyword), routing and geocoding (Geoapify / ORS)
  util/         distance (Haversine) and pricing helpers
src/main/resources/
  templates/    Thymeleaf pages
  static/       css, js, img (logos, background)
src/test/java/  unit and integration tests
```
Routing, address search and problem matching each sit behind an interface (`RoutingService`, `GeocodingService`, `MatchingService`), so a provider can be swapped by adding one class and changing a property.

## Testing
```bash
mvn test
```
- Unit tests: distance and 10% charge, price range and status transitions, keyword and AI-reply parsing, routing and address-search parsing.
- Integration tests (in-memory H2, MockMvc): the whole flow from verification to a paid, rated job, plus documents, payouts, address search, public profiles and page rendering. External services are mocked, so no API keys are needed.

## Security and privacy notes
- Passwords are stored with BCrypt. API keys are read from environment variables only.
- Roles (customer, technician, admin) are enforced on every page and API route. Admin accounts cannot be created through registration.
- Uploaded documents and payment proofs are stored in the database and only the owner, the other party to the job (proofs) or an admin can open them.
- Customer problem descriptions are sent to Groq for matching. Tell users not to type personal details.
- Change the default admin password, and rotate any key that was ever shared in chat or committed.

## Limitations and roadmap
- No payment gateway: payments, proofs and payouts are recorded manually.
- Public transport (bus and train) routes are not available.
- Notifications, password reset, an earnings report, technician availability and an admin overview are not built yet.
- The H2 file database suits a prototype. For production, switch to a server database such as PostgreSQL or MariaDB.

---
Built by the FixIt team for Hack For Humanity.
