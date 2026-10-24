# FixIt Service Portal

Spring Boot 3.3 / Java 17 / Lombok / Spring Security / JPA on a file-based **H2** database.
Thymeleaf pages (with a little JavaScript and a Leaflet/OpenStreetMap map) on top of a **REST API under `/api`**.
The map library loads from unpkg.com, so the pages with a map need internet access.

## Run
```
mvn spring-boot:run          # http://localhost:8080
mvn test                     # unit tests + end-to-end happy path (in-memory H2)
```
Data is kept in `./data/fixit.mv.db` (git-ignored). Delete the `data/` folder to start fresh.

On first start the app creates the admin account from `application.properties`
(`app.admin.email` / `app.admin.password`, default `admin@fixit.local` / `Admin@12345`: **change it**),
the languages Sinhala, Tamil, English, and eight professions with LKR price ranges.

Routes and address search need a free Geoapify key: set the `GEOAPIFY_API_KEY` environment variable.
Everything else works without it (you can still click the map). To use OpenRouteService instead, set
`app.routing.provider=ors` and `ORS_API_KEY`.

## Pages
| Page | Who | What |
|---|---|---|
| `/register`, `/login` | everyone | sign up (pick languages), log in |
| `/profile` | customer, technician | edit name/contact/gender, pin the map location (with address search), languages, upload verification documents; customer preferences; technician qualification and professions |
| `/technicians/{id}` | anyone signed in | public technician profile: professions, languages, rating and reviews (no contact details) |
| `/customer/find` | customer | describe a problem or search on the map, then book a technician |
| `/bookings`, `/bookings/{id}` | customer, technician | jobs, price negotiation, status, payments, rating; technician also gets routes |
| `/technician/dashboard` | technician | earnings, payouts received, jobs by status, upcoming jobs, customers |
| `/admin/home` | admin | verify profiles (with their uploaded documents), confirm platform payments, record payouts to technicians, manage languages and profession price ranges |

## Flow
1. Sign up as Customer or Technician (pick the languages you speak). Status starts as `PENDING`.
2. An admin verifies on `/admin/home` (or `POST /api/admin/users/{id}/approve|reject`). Pending/rejected users can edit their profile but cannot book, bid or be listed. Editing a rejected profile resubmits it.
3. Technician: set map location, languages, qualification, professions (`/api/technician/...`).
   Customer: set map location, languages, preferences (gender, distance, budget, rating, languages).
4. Customer finds technicians: `GET /api/discovery/technicians` (nearest first, preferences applied) or
   `POST /api/discovery/match` with a problem description.
5. Customer books with a price inside the profession's range; both sides counter/accept (`/api/bookings/...`).
6. Job: `CONFIRMED -> IN_PROGRESS -> COMPLETED -> PAID`. Technician sees routes at
   `GET /api/technician/bookings/{id}/routes`.
7. Payments (records only, no gateway): the 10% service charge is paid through the platform with a proof file
   (PNG/JPEG/PDF, max 5 MB, stored in H2) and confirmed by an admin; the 90% balance is paid in cash
   (technician confirms) or through the platform (proof, admin confirms). Both confirmed = `PAID`.
8. Customer rates the technician (1-5). Technician dashboard: `GET /api/technician/dashboard`.

### Extra endpoints
| Endpoint | What |
|---|---|
| `PUT /api/me` | edit full name, contact number, gender (email and NIC are fixed; address is saved with `PUT /api/me/location`) |
| `POST /api/me/documents` (multipart `file`, `label`), `GET /api/me/documents`, `DELETE /api/me/documents/{id}`, `GET /api/documents/{id}/file` | verification documents (PNG/JPEG/PDF, 5 MB, max 10). Owner and admin can open them; a verified profile cannot delete them. Uploading while REJECTED resubmits the profile |
| `GET /api/admin/users/{id}/documents` | admin: a user's documents |
| `GET /api/admin/payouts/pending`, `POST /api/admin/bookings/{id}/payout`, `GET /api/admin/payouts`, `GET /api/technician/payouts` | when a customer pays the balance through the platform and an admin confirms it, the admin pays the technician outside the app and records the reference here (once per booking) |
| `GET /api/geocode?q=` | address to map position (Geoapify, Sri Lanka only; change with `app.geocoding.country`). Needs `GEOAPIFY_API_KEY` |
| `GET /api/technicians/{id}` | public technician profile with the last 20 reviews (reviewer first name only) |

## Notes
- Problem matching uses an LLM on Groq's free tier (`openai/gpt-oss-20b`, `GroqMatchingService`). Set the
  `GROQ_API_KEY` environment variable (in IntelliJ: Run > Edit Configurations > Environment variables).
  Without a key, or if Groq is unreachable or over its daily free limit, it falls back to the keyword matcher
  (`app.matching.provider=keyword` forces that). The model can only answer with names from the profession list.
  Customer text is sent to Groq, so tell users not to type personal details.
- Routes: car, motorbike, bicycle and walking from Geoapify (a real motorbike profile; tuk-tuk is not available);
  bus/train routes are not available.
- The REST API uses the same session login as the pages; CSRF is off for `/api/**` only.
- The H2 web console is disabled (it conflicts with the path rules in Spring Security 6).
