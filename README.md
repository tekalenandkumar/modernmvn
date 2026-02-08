# Modern Maven (modernmvn.com)

[![Backend Build](https://github.com/tekalenandkumar/modernmvn/actions/workflows/backend.yml/badge.svg?branch=master)](https://github.com/tekalenandkumar/modernmvn/actions/workflows/backend.yml)
[![Frontend Build](https://github.com/tekalenandkumar/modernmvn/actions/workflows/frontend.yml/badge.svg?branch=master)](https://github.com/tekalenandkumar/modernmvn/actions/workflows/frontend.yml)

**Dependency Intelligence for the Modern Java/JVM Developer.**

Modern Maven is a reimagined repository browser and dependency analysis tool designed to replace the aging repository browser experience. It goes beyond simple artifact listings to provide deep insights into your project's dependency graph, resolution conflicts, and security posture.

## 🚀 Vision

Java development has evolved, but our tools for browsing libraries haven't. Developers struggle with "dependency hell"—transitive conflicts, shaded jars, and opaque resolution rules. 

**Modern Maven aims to answer:**
*   "Why is this specific version of Jackson being pulled in?"
*   "Which library is overriding my logging framework?"
*   "Is this artifact actually maintained and safe to use?"

## ⚠️ The Problem

Existing tools (like Maven Central search or other browsers) act as static phonebooks. They list artifacts but don't explain how they interact in a real build. They lack:
*   **Visual Context**: No way to see the full dependency tree.
*   **Resolution Logic**: No simulation of Maven's "nearest-wins" strategy.
*   **Modern UX**: Outdated interfaces that are hard to use on mobile or modern screens.

## 🗺️ Roadmap

### Phase 0: Foundation (Current Status: ✅)
*   [x] Project infrastructure (Spring Boot 4, Next.js 15).
*   [x] Dockerized PostgreSQL & Redis.
*   [x] Apache 2.0 Licensing.

### Phase 1: Resolution Engine (In Progress 🚧)
*   [ ] Backend logic to parse POMs and resolve dependency trees using Maven Resolver (Aether).
*   [ ] API to accept `groupId:artifactId:version` and return a graph.

### Phase 2: MVP Graph Viewer
*   [ ] **Interactive Graph**: Visualize dependencies using React Flow.
*   [ ] **Conflict Highlighting**: Instantly see which versions lost the "nearest-wins" battle.

### Phase 3: Explainability
*   [ ] "Why" analysis: Click any node to see exactly why that version was chosen.

## 🛠️ Technology Stack

*   **Backend**: Java 25, Spring Boot 3.4.2 (compiling to Java 21), Maven Resolver.
*   **Frontend**: Next.js 15, TypeScript, Tailwind CSS, React Flow.
*   **Data**: PostgreSQL (pgvector), Redis (Caching), Meilisearch (Search).

## 📦 Getting Started

### Prerequisites
*   Docker & Docker Compose
*   Java 25
*   Node.js 20+

### Running Locally

1.  **Start Services** (Postgres, Redis, Meilisearch):
    ```bash
    docker-compose up -d
    ```

2.  **Start Backend**:
    ```bash
    cd backend
    ./mvnw spring-boot:run
    ```

3.  **Start Frontend**:
    ```bash
    cd frontend
    npm run dev
    ```

### Deployment (Vercel)

If deploying the frontend to Vercel, ensure you configure the **Root Directory** correctly:

1.  Go to your Vercel Project Settings > General.
2.  Set **Root Directory** to `frontend`.
3.  Ensure the **Build Command** is `next build` (default).
4.  Ensure the **Output Directory** is `.next` (default).

If you configure the **Root Directory** as `frontend` in Vercel settings, a `vercel.json` file is typically not required as Next.js defaults will be auto-detected. However, if used, it should be placed inside `frontend/` and use relative paths (e.g., outputDirectory: `.next`).

## 📄 License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
