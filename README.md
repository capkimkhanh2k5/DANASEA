<div align="center">

<h1 align="center">
  <img src="assets/danasea-logo.png" alt="DANASEA logo" width="64" align="middle"/>&nbsp; DANASEA
</h1>

**Marine Tourism & Coastal Experience Management Platform**  
*Da Nang, Vietnam*

<br/>

[![CI Status](https://github.com/capkimkhanh2k5/PBL6_HTTT/actions/workflows/ci.yml/badge.svg)](https://github.com/capkimkhanh2k5/PBL6_HTTT/actions/workflows/ci.yml)
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
