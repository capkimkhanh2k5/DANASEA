<div align="center">

<h1 align="center">
  <img src="assets/danasea-logo.png" alt="DANASEA logo" width="64" align="middle"/>&nbsp; DANASEA
</h1>

**Marine Tourism & Coastal Experience Management Platform**  
*Da Nang, Vietnam*

<br/>

[![CI Status](https://github.com/capkimkhanh2k5/PBL6_HTTT/actions/workflows/ci.yml/badge.svg)](https://github.com/capkimkhanh2k5/PBL6_HTTT/actions/workflows/ci.yml)
[![CD](https://github.com/capkimkhanh2k5/DANASEA/actions/workflows/cd.yml/badge.svg)](https://github.com/capkimkhanh2k5/DANASEA/actions/workflows/cd.yml)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=flat&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?style=flat&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.5-3178C6?style=flat&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-5-646C9A?style=flat&logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3.4-06B6D4?style=flat&logo=tailwindcss&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=flat&logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-FF6600?style=flat&logo=rabbitmq&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Container-2496ED?style=flat&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-Reverse_Proxy-009639?style=flat&logo=nginx&logoColor=white)
![Tailscale](https://img.shields.io/badge/Tailscale-Funnel-24292E?style=flat&logo=tailscale&logoColor=white)

<br/>

<p align="center">
  An online booking and experience management platform tailored for coastal and marine tourism in Da Nang, Vietnam. Connecting travelers and local tour operators through real-time scheduling, secure automated transactions, cryptographic ticket verification, and weather-aware policy handling.
</p>

</div>

---

## Overview

Coastal adventure tourism requires strict capacity management, weather contingency handling, and reliable verification. DANASEA addresses these domain challenges through a modular architecture comprising:

- **Experience Catalog & Booking:** Real-time slot reservation, inventory locking, and multi-vendor checkout.
- **Payment Processing:** Dual integration with VNPay (VietQR / Domestic ATM) and PayPal, backed by idempotency keys to prevent duplicate transactions.
- **Cryptographic Verification:** HMAC-SHA256 signed QR tickets with constant-time verification to prevent ticket forgery and duplicate redemption.
- **Weather Contingency & Dynamic Refunds:** Automated slot cancellation and customer refund workflows driven by marine weather monitoring (Open-Meteo API).
- **Vendor Settlement & Disputes:** Transparent commission accounting, automated refund deduction, and structured arbitration workflows.
- **AI Concierge:** Natural language itinerary recommendations and policy clarification powered by large language models.

---

## System Architecture

The platform is designed around a **Modular Clean Monolith** adhering to Hexagonal Architecture (Ports & Adapters) principles. The diagram below illustrates the end-to-end request flow across presentation, edge, core domain modules, data layers, and external providers:

<p align="center">
  <img src="assets/SystemArchitecture.png" alt="DANASEA System Architecture" width="100%"/>
</p>

---

## Technology Stack

### Backend
- **Core Framework:** Spring Boot 3.4 (Java 21 LTS)
- **Security:** Spring Security 6 with stateless JWT authentication and strict cookie origin validation
- **Persistence:** Spring Data JPA / Hibernate, PostgreSQL 17, Flyway
- **Caching & Messaging:** Redis 7 (distributed locks, rate limiting), RabbitMQ 3.12 (asynchronous task execution)
- **Testing & Quality:** JUnit 5, Mockito, Testcontainers (isolated containerized integration testing)

### Frontend & Clients
- **Web:** React 18, TypeScript, Vite, Tailwind CSS
- **Mobile:** React Native / Expo (Vendor and Customer applications)

### Infrastructure & Operations
- **Containerization:** Docker, Docker Compose
- **Continuous Integration / Continuous Deployment:** GitHub Actions, GitHub Container Registry (GHCR)
- **Networking & Access:** Tailscale Funnel, Nginx

---

## AI Features & Capabilities

DANASEA integrates a **Hybrid Dual-Engine AI Architecture** combining conversational LLM orchestration with a pinned, SHA-256 verified local decision worker (`chinhnc/Quyet-1.0-Small` on FastAPI/PyTorch) for fast structured classification, scoring, moderation, and risk evaluation. Every AI capability is strictly bounded by backend domain rules and structured response cards so natural-language output never overrides verified pricing, inventory, maritime safety thresholds, or financial state.

### Architecture & Processing Principles
- **Dual-Engine Orchestration:** A proxied conversational LLM handles multilingual dialogue (VI/EN) and multi-turn tool calling, while the local **Quyet Small Worker** executes 7 deterministic rubric tasks (`intent`, `service_category`, `review`, `moderation`, `relevance`, `complaint`, `risk`).
- **Grounded Data & Non-Fabrication:** Conversational replies are paired with backend-verified structured cards (live pricing, slots, policies, KPIs, and cited review quotes) and marked as unverified prose so authoritative data always comes from PostgreSQL and domain services. AI read queries never mutate financial state, inventory, or listing metrics.
- **Execution Guardrails & Fail-Closed Handling:** Protected by JVM concurrency and deadline budgets (up to 8 concurrent AI requests), Redis rate limiting, idempotency replay protection, and optimistic locking. When models are unavailable or uncertain, moderation and risk flows fail closed to human review (`NEEDS_REVIEW`).

### 1. Customer AI Concierge
- **Smart Discovery & Recommendations:** Understands natural-language queries with group size, schedule windows, non-swimmer/safety preferences, and budgets (supporting both VND and USD via live exchange rates), combining backend catalog filtering, Quyet relevance scoring, and opt-in customer preference profiles.
- **Nearby Search & Side-by-Side Comparison:** Finds bookable coastal experiences around GPS coordinates (Haversine radius) and compares 2–4 services with exact group/package pricing (`PER_PERSON` and `PER_PACKAGE` participant allocation).
- **Weather-Aware Itinerary Planning & Auto-Replanning:** Builds chronological multi-activity plans validated against marine weather safety and inter-beach travel times (`15 min buffer + 25 km/h`), supporting full lifecycle management (preview, draft, accepted, archived, revision restore) plus background monitoring that detects slot cancellations or severe weather and raises replanning proposals.
- **Chat-to-Booking Confirmation:** Produces short-lived booking confirmation cards with distributed locks, real-time price and slot re-validation, atomic inventory holds, and automatic compensation rollback on failure.
- **Grounded Support & Pre-Trip Briefings:** Answers live enabled payment methods, canonical refund/cancellation policies, extractive review summaries citing verified review IDs, order cancellation previews, manual support requests, and automated 24-hour pre-trip marine safety checklists.

### 2. Vendor Growth & Operations Copilot
- **Shop Performance & Revenue Insights:** Delivers period-flexible GMV, estimated net payout, booking volume, and listing status summaries strictly scoped to the authenticated vendor.
- **4-Pillar Review Coaching & Reply Drafting:** Analyzes verified reviews across `GUIDE`, `SAFETY`, `PUNCTUALITY`, and `VALUE`, separates verified customer quotes from operational hypotheses, tracks badge progression guidance, and drafts contextual review replies.
- **Weather-Aware Slot Capacity Advisor:** Evaluates upcoming 1–14 day slots for occupancy and marine weather forecasts, prioritizing maritime safety over demand (never suggesting capacity expansion under unsafe or unknown weather).
- **Listing Optimization & Pre-Publish Content Check:** Scores listing quality (0–100), highlights missing safety/equipment information, drafts VI/EN copy from verified facts, and screens text for off-platform payments, external contacts, spam, or abuse as advisory-only checks.

### 3. Admin BI, Risk & Arbitration Copilot
- **Executive BI & Vendor Revenue Analytics:** Supports natural-language queries for platform KPIs, GMV/cash/refund/commission timeseries, month-over-month comparisons, cancellation cause breakdowns (`WEATHER`, `VENDOR_FAULT`, `CUSTOMER`, `ADMIN_OVERRIDE`, `DISPUTE`), and vendor revenue rankings (Top N, single-vendor drill-down, and multi-vendor comparison across flexible date ranges).
- **Vendor Health Diagnostics:** Evaluates operational thresholds (`HIGH_CANCELLATION` >20% warning / >40% critical, `LOW_RATING` with minimum review counts, `OVERLOADED`, `UNDERPERFORMING`) while requiring manual evidence verification before any suspension or penalty.
- **Transaction Risk & Content Moderation Governance:** Combines backend Velocity 3/5/3 rules (`>=3` failed payments/24h, `>=5` orders/1h, `>=3` refunds/7d) with Quyet risk/moderation scoring to route flagged cases into `NEEDS_REVIEW` without auto-locking accounts or confirming fraud automatically.
- **Dispute Evidence Summarization & Queue Oversight:** Consolidates customer claims, vendor responses, slot status, and HMAC-SHA256 QR check-in logs into arbitration briefs alongside unified risk, dispute, and optimistic-locked support queues.

---

## Getting Started

### Prerequisites
- Docker and Docker Compose
- Java 21 JDK (optional, for native backend builds)
- Node.js 20+ (optional, for native frontend builds)

### Local Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/capkimkhanh2k5/PBL6_HTTT.git
   cd PBL6_HTTT
   ```

2. **Configure environment variables:**
   ```bash
   cp .env.example .env
   ```
   Review and populate the required configuration values in `.env`.

3. **Start services with Docker Compose:**
   ```bash
   docker compose up -d
   ```

   Once healthy, the application endpoints will be available at:
   - Frontend: `http://localhost:3000`
   - Backend API: `http://localhost:8080/api`
   - RabbitMQ Management: `http://localhost:15672` (default: `guest` / `guest`)

---

## Server Demo Data

The manual **Demo Data** GitHub Actions workflow can inspect, replace and restore
application demo data after a verified backup. See the [operator runbook](docs/Data/demo-data-workflow.md)
for the required server bootstrap, production secrets and recovery procedure.

## Authors

This project was developed as part of **PBL6 — Capstone Project in Information Technology**, Department of Information Technology, Da Nang University of Science and Technology (DUT).

| Member | Role & Responsibilities |
|---|---|
| **Cap Kim Khanh** | Lead Developer — Backend, AI Integration, Infrastructure & DevOps |
| **Dinh Minh Tue** | Full Stack Support, Data Engineering |
| **Nguyen Cong Nhat** | Frontend Web Development |
| **Nguyen Thi Anh Thu** | Mobile Application Development |

---

## License

This project is licensed under the [MIT License](LICENSE).
