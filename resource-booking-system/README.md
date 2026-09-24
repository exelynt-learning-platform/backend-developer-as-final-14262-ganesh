# Resource Booking System REST API

A production-style, high-performance REST API for managing bookable resources (e.g. rooms, vehicles, equipment) and reservations, built with **Java 21**, **Spring Boot 3.3.5**, **Spring Data JPA**, **Spring Security (RBAC)**, and **JWT**.

---

## Tech Stack
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.3.5 (Spring Web, Spring Data JPA, Spring Security, Bean Validation)
- **Database**: PostgreSQL (Production/Dev), H2 In-Memory (Test profile)
- **Security**: Stateless JWT with HMAC-SHA256 (`jjwt 0.12.6`), BCrypt password hashing
- **API Documentation**: SpringDoc OpenAPI 2.6.0 (Swagger UI at `/swagger-ui/index.html`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc, `spring-security-test`, DataJpaTest
- **Code Coverage**: JaCoCo (Enforced minimum line coverage: >= 85%)

---

## Prerequisites
- **Java 21** or higher (`java -version`)
- **Maven 3.9+** (`mvn -version`)
- **Docker & Docker Compose** (for running PostgreSQL container) or an external PostgreSQL instance

---

## Database Setup & Docker Compose

To spin up a local PostgreSQL database instance:
```bash
docker-compose up -d
```
This starts PostgreSQL 16 on port `5432` with database `booking_db`, user `postgres`, and password `postgres`.

---

## Environment Variables

| Variable | Description | Default Value |
|---|---|---|
| `DB_URL` | JDBC connection URL for PostgreSQL | `jdbc:postgresql://localhost:5432/booking_db` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `JWT_SECRET` | Secret key for signing HS256 JWT tokens (>= 32 chars) | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` |
| `JWT_EXPIRATION_MS` | JWT validity duration in milliseconds | `3600000` (1 hour) |

---

## How to Run

### 1. Start PostgreSQL
```bash
docker-compose up -d
```

### 2. Run Application
```bash
mvn spring-boot:run
```
The application starts at `http://localhost:8080`.

---

## Seed Credentials & Demo Data
The `DataSeeder` automatically initializes the database upon startup with the following test credentials:

| Username | Password | Role | Description |
|---|---|---|---|
| `admin` | `Admin@123` | `ADMIN` | System administrator with full access |
| `user1` | `User@123` | `USER` | Standard user for making bookings |
| `user2` | `User@123` | `USER` | Standard user for isolation testing |

### Default Resources:
1. **Conference Room A** (Type: `ROOM`, Available: `true`)
2. **Toyota Camry Hybrid** (Type: `VEHICLE`, Available: `true`)
3. **Sony FX3 Camera Kit** (Type: `EQUIPMENT`, Available: `true`)

---

## API Documentation & Postman
- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI 3.0 Specs JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Postman Collection**: `postman/booking-api.postman_collection.json` (Includes pre-configured requests and token capture scripts)

---

## Endpoints & RBAC Matrix

| Method | Endpoint | Allowed Roles | Description |
|---|---|---|---|
| `POST` | `/auth/login` | Public | Authenticates credentials and issues Bearer JWT |
| `GET` | `/swagger-ui/**`, `/v3/api-docs/**` | Public | OpenAPI documentation & interactive UI |
| `GET` | `/api/resources` | `ADMIN`, `USER` | List all resources |
| `GET` | `/api/resources/{id}` | `ADMIN`, `USER` | Retrieve a single resource by ID |
| `POST` | `/api/resources` | `ADMIN` | Create a new bookable resource |
| `PUT` | `/api/resources/{id}` | `ADMIN` | Update an existing resource |
| `DELETE`| `/api/resources/{id}` | `ADMIN` | Delete a resource |
| `POST` | `/api/reservations` | `USER`, `ADMIN` | Book a resource (owner extracted from JWT) |
| `GET` | `/api/reservations` | `USER`, `ADMIN` | List reservations with dynamic filtering & pagination (`USER` sees own only) |
| `GET` | `/api/reservations/{id}` | `USER`, `ADMIN` | Get reservation details (`USER` can only access own) |
| `PUT` | `/api/reservations/{id}` | `USER`, `ADMIN` | Update booking (`USER` can modify time / cancel own; `ADMIN` full control) |
| `DELETE`| `/api/reservations/{id}` | `ADMIN` | Delete reservation |

---

## Sample cURL Requests

### 1. User Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user1",
    "password": "User@123"
  }'
```
Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "role": "USER",
  "expiresIn": 3600000
}
```

### 2. Create Reservation
```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceId": 1,
    "startTime": "2026-10-01T10:00:00",
    "endTime": "2026-10-01T12:00:00",
    "price": 150.00
  }'
```
Response (`201 Created` with `Location: /api/reservations/1`):
```json
{
  "id": 1,
  "resourceId": 1,
  "resourceName": "Conference Room A",
  "resourceType": "ROOM",
  "userId": 2,
  "username": "user1",
  "startTime": "2026-10-01T10:00:00",
  "endTime": "2026-10-01T12:00:00",
  "price": 150.00,
  "status": "PENDING",
  "createdAt": "2026-09-24T23:00:00"
}
```

### 3. Filter & Paginate Reservations
```bash
curl -X GET "http://localhost:8080/api/reservations?status=PENDING&minPrice=50.00&maxPrice=300.00&page=0&size=10&sortBy=price&direction=asc" \
  -H "Authorization: Bearer <TOKEN>"
```

---

## Error Response Format
All errors return a consistent JSON response:
```json
{
  "timestamp": "2026-09-24T23:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/reservations",
  "validationErrors": {
    "price": "Price must be greater than 0"
  }
}
```

---

## Testing & JaCoCo Coverage Report

Run the complete test suite and JaCoCo coverage verification:
```bash
mvn clean verify
```
The build enforces a minimum line coverage ratio of **85%**.

View the generated HTML coverage report at:
```
target/site/jacoco/index.html
```
