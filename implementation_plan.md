# implementation_plan.md

## Project: modernmvn.com
**Goal**: Build a modern "dependency intelligence" tool that visualizes effective dependencies, explains version selects (nearest-wins strategy), and helps resolve conflicts. It aims to be a superior alternative to `mvnrepository.com` with better UX and deeper insights.

---

## Technical Stack
- **Backend**: Spring Boot 3.4.2 (Target Java 21, Runtime Java 25) with Maven Resolver (Aether)
- **Frontend**: Next.js 15 (React 19) with Tailwind CSS and React Flow
- **Database**: PostgreSQL (Search/Metadata) + Redis (Caching)
- **Deployment**: Vercel (Frontend), Railway/Render/AWS (Backend)
- **Dev Tools**: Docker Compose for local PostgreSQL and Redis

---

## Milestones & Implementation Plan

### Milestone 0: Foundation & Infrastructure (COMPLETED)
**Goal**: Setup project structure, repositories, and CI/CD basics.
- [x] **Repository Setup**: Initialize a monorepo structure (`/backend`, `/frontend`).
- [x] **Backend Init**: Create a basic Spring Boot 4.0.2 application.
- [x] **Frontend Init**: Create a Next.js application with Tailwind CSS (Completed using `/tmp/npm-cache` to bypass permission issues).
- [x] **Data Stores**: Configure `docker-compose.yml` for Postgres and Redis (Existing).
- [x] **Config**: Update `application.properties` to connect to local services.
- [x] **License**: Apply Apache 2.0 License.
- [x] **CI/CD**: specific basic build workflows (GitHub Actions) for both backend and frontend.

### Milestone 1: Resolution Engine (Backend Core) ✅
**Goal**: Implement the core logic to resolve Maven dependencies.
- [x] **Maven Resolver Integration**: Integrate `maven-resolver` (Aether) libraries into Spring Boot.
- [x] **API Endpoint**: Create an endpoint that accepts `groupId:artifactId:version` and returns a dependency tree.
- [x] **POM Parsing**: Implement logic to parse raw POM snippets.
- [x] **Caching Strategy**: Implement Redis caching for resolved dependency trees to improve performance.

### Milestone 2: MVP UI (Graph Viewer) ✅
**Goal**: Visualize the dependency tree.
- [x] **Graph Component**: Integrate `React Flow` in Next.js.
- [x] **Search Input**: Create a home page with a search bar for Maven coordinates.
- [x] **Visualization**: Render the dependency graph returned by the backend.
- [x] **Legend & Tooltips**: Add visual cues for scopes (compile, test, etc.) and node details.
- [x] **Table View**: Sortable, filterable dependency table with scope and status filters.
- [x] **Mobile Responsiveness**: Ensure the graph viewer works reasonably well on smaller screens.

### Milestone 3: Explainability & Conflict Intelligence ✅
**Goal**: Explain *why* a specific version was selected.
- [x] **Conflict Logic**: Implement logic to identify conflicting versions in the tree.
- [x] **"Why" Analysis**: Detailed conflict messages ("X omitted for Y").
- [x] **UI Indicators**: Highlight conflict nodes and show the "winning" vs "omitted" versions in the graph.
- [x] **Conflict Summary**: Dedicated view summarizing all dependency conflicts.
- [x] **Export & Share**: Export dependency data as JSON; share analysis via URL.

### Milestone 4: Advanced Inputs ✅
**Goal**: Support real-world project scenarios.
- [x] **POM File Upload**: Drag-and-drop or click-to-browse POM file upload with validation (.xml/.pom, 512KB limit).
- [x] **Multi-module Project Detection**: Detect `<modules>` block in parent POMs and present module selector.
- [x] **Local Module Treatment**: Modules referenced in parent POM are treated as LOCAL artifacts with visual markers.
- [x] **Visual Grouping**: Multi-module projects show a module selector bar with per-module and merged views.
- [x] **Custom Repository Input**: Add up to 5 custom HTTPS repository URLs alongside Maven Central.
- [x] **Security Disclaimer & Limits**: Collapsible security panel detailing file limits, HTTPS enforcement, and no-storage policy.
- [x] **Session Timeout & Cleanup**: 30-minute session timer with 5-minute warning; auto-clears results on expiry.

### Milestone 5: Artifact Pages (SEO Entry)
**Goal**: Create static/indexable pages to compete with existing repositories.
- [ ] **Route Structure**: Implement `/artifact/[groupId]/[artifactId]/[version]` routes.
- [ ] **Metadata Fetching**: Fetch and display artifact details (description, license, size, dates).
- [ ] **"Recommended Version"**: Highlight the most stable/popular version.
- [ ] **SEO Tags**: Dynamic OpenGraph images and meta tags.

### Milestone 6: Discovery & Search
**Goal**: Enable full catalog search.
- [ ] **Search API**: Implement full-text search using Meilisearch (via Docker).
- [ ] **Trending**: Display "Trending Artifacts" or "Recently Updated" on the homepage.
- [ ] **Categories**: Tagging and categorization of artifacts.

### Milestone 7: Security & Ecosystem
**Goal**: Add security intelligence.
- [ ] **Vulnerability Data**: Ingest CVE data (OSV/NVD).
- [ ] **Badges**: Show security badges/warnings on artifact nodes in the graph.
- [ ] **CLI Tool**: (Optional) Prototype a `modernmvn analyze` CLI tool.
