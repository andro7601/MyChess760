# MyChess760 - Real-Time Multiplayer Chess Platform

MyChess760 is a full-stack, real-time multiplayer chess application designed with a high-performance Spring Boot backend and an interactive client interface. The platform supports secure user authentication, automatic matchmaking queues, real-time game state synchronization via WebSockets (STOMP), server-side move validation, game timers, and database persistence. 

It is ideal as a portfolio showcase demonstrating standard architectural patterns in real-time communication, microservice-ready caching (Redis), transactional persistence (PostgreSQL), and modular full-stack development.

---

## Architecture & Features

### 1. Real-Time Gameplay & WebSockets
* **STOMP Protocol over WebSocket**: Live match data, draw offers, resignations, and moves are broadcast instantly to players.
* **Server-Side Validation**: Every move is validated against chess rules on the server side using a dedicated chess library to prevent cheating.
* **In-Game Commands**: Implements real-time features like draw offering/acceptance, resignations, and game state restoration upon reconnection.

### 2. Matchmaking & Queue Management
* **Fast Matchmaking Queue**: Users can enter a matchmaking lobby. A background service automatically pairs waiting players based on queue arrivals.
* **Redis Caching**: Matchmaking state and active game snapshots (including board state, player IDs, and remaining time) are cached in Redis to achieve low-latency updates and reliable game resumes.

### 3. Authentication & Security
* **JWT-Based Security**: Secure user registration and authentication endpoints generating JSON Web Tokens.
* **Stateless Security Filter**: Re-authenticates STOMP connect headers using a stateless filter, ensuring that only authenticated sessions can join queues or play moves.

### 4. Game Timers & Connection Monitoring
* **Active Match Timers**: Each player has an active timer that ticks down on the client side and synchronizes with the server.
* **Timeout & Abandonment Detection**: Background scheduler monitors client heartbeats and pings to detect disconnections, automatically forfeiting matches if a player abandons or runs out of time.

### 5. Interactive Web client
* **Stateful Chess Board**: Responsive board interface rendering standard unicode chess pieces, showing legal move highlights, capture highlights, coordinates, and board flipping.
* **Console Logs & Match Info**: A raw logs console showing live STOMP frames, dynamic move history list, game timer indicators, and matchmaking queue status panels.

---

## Tech Stack

* **Backend**: Spring Boot 3.4.x, Java 21, Spring Security, Spring WebSockets + STOMP, Spring Data JPA, Spring Data Redis, ChessLib (for chess logic)
* **Database & Migrations**: PostgreSQL, Flyway
* **In-Memory Store**: Redis
* **Frontend**: HTML5, CSS3, JavaScript (ES6+), SockJS & StompClient
* **Testing**: JUnit 5, Mockito, Testcontainers (isolated PostgreSQL container)

---

## Local Setup & Installation

### Prerequisites
Make sure you have the following installed:
* [Docker & Docker Compose](https://www.docker.com/)
* [Java Development Kit (JDK 21)](https://adoptium.net/) (if running host-side)
* [Maven](https://maven.apache.org/) (optional, Maven wrapper `./mvnw` is included)

### Step 1: Clone the Repository & Configure Environment
1. Clone this repository to your local machine:
   ```bash
   git clone <repository-url>
   cd MyChess760
   ```
2. Create your `.env` file from the sample template:
   ```bash
   cp .env-sample .env
   ```
3. Open `.env` and fill in the missing fields (e.g. database credentials):
   ```env
   JWT_SECRET=your_super_long_secret_key_of_at_least_256_bits
   JWT_EXPIRATION=86400000

   POSTGRES_DB=chess
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres_password
   SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-chess:5432/chess

   REDIS_HOST=redis-chess
   REDIS_PORT=6379
   ```

### Step 2: Spin Up Infrastructure
Start the database, caching layer, and application using Docker Compose:
```bash
docker compose up -d
```
This starts:
1. **PostgreSQL** on port `5432`
2. **Redis** on port `6379`
3. **MyChess760 Application** on port `8080` (runs `./mvnw spring-boot:run` automatically)

### Step 3: Run the Application Locally (Alternative)
If you prefer running the Spring Boot application on your host machine:
1. Ensure the PostgreSQL and Redis containers are running:
   ```bash
   docker compose up -d postgres-chess redis-chess
   ```
2. Run the Spring Boot application:
   ```bash
   # On Linux / macOS
   ./mvnw spring-boot:run

   # On Windows
   mvnw.cmd spring-boot:run
   ```

### Step 4: Access the Client UI
Once the backend starts, open your browser and navigate to:
```text
http://localhost:8080/ws-tester.html
```
From here you can:
* Register and log in multiple users (using two different browser sessions/windows).
* Connect via WebSockets and find a match.
* Play moves, offer draws, and resign, or view game-ending states.

---

## Testing & Quality Assurance

To execute unit and integration test suites:
```bash
# Run all tests
./mvnw test
```

### Integration Test Details
* **Database Tests (`AuthIntegrationTest`)**: Automatically spins up an ephemeral PostgreSQL instance via **Testcontainers** to test migration, signup, and login processes. Requires a running local Docker daemon.
* **WebSocket & Logic Tests (`WebSocketIntegrationTest`)**: Verifies real-time messaging, match updates, and gameplay rules with mocked services and templates.
