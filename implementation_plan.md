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

### Milestone 5: Artifact Pages (SEO Entry) ✅
**Goal**: Create static/indexable pages to compete with existing repositories.
- [x] **Route Structure**: `/artifact/[groupId]/[artifactId]` and `/artifact/[groupId]/[artifactId]/[version]` with catch-all slug routing.
- [x] **Backend DTOs & Service**: `ArtifactInfo`, `ArtifactVersion`, `ArtifactDetail` DTOs; `MavenCentralService` with Solr API integration and POM XML parsing.
- [x] **Redis Caching**: 6-hour TTL for artifact info, 24-hour TTL for artifact detail.
- [x] **REST Controller**: `/api/maven/artifact/{groupId}/{artifactId}` and `/api/maven/artifact/{groupId}/{artifactId}/{version}`.
- [x] **Metadata Fetching**: Fetch and display artifact details (description, license, dependency count, dates, packaging).
- [x] **"Recommended Version"**: Smart filtering of pre-release qualifiers (alpha, beta, RC, SNAPSHOT, milestone) to determine the recommended stable version.
- [x] **Dependency Snippets**: Auto-generated snippets for Maven, Gradle (Groovy/Kotlin), SBT, Ivy, Leiningen, and Apache Buildr with copy-to-clipboard.
- [x] **Version Browser**: Filterable/searchable version list with release date, "Show pre-release" toggle, and "Show all" expand.
- [x] **SEO Tags**: Dynamic title/description, OpenGraph metadata, canonical URLs, and JSON-LD structured data for each artifact.
- [x] **ISR (Incremental Static Regeneration)**: Server-side rendering with 1-hour revalidation for optimal SEO and performance.
- [x] **Sitemap.xml**: Dynamic sitemap with core pages and 20 popular artifacts for Google indexing seed.
- [x] **Popular Artifacts on Home**: Homepage "Popular Artifacts" section with links to key artifact pages.
- [x] **Custom 404**: Branded not-found page for invalid artifact routes.
- [x] **Navigation**: Navbar with Home/Analyze links and breadcrumb trail on artifact pages.

## 🟢 Milestone 6: Search & Discovery ✅
**Goal:** Make artifact browsing fast and pleasant.
- [x] Artifact search API (Solr-backed full-text search via Maven Central)
- [x] Full-text search with debounced input and pagination
- [x] Trending artifacts (curated list enriched with live Solr metadata)
- [x] Recently updated artifacts (`*:* sort=timestamp desc`)
- [x] Search UX (query suggestions, skeleton loading, result cards)
- [x] Enhanced homepage with live Trending + "What's New" feeds

### Exit Criteria
* ✅ Search feels fast and accurate
* ✅ Discoverability improves with trending + recent feeds

---

## 🟠 Milestone 7: Security & Version Intelligence (Partially Complete)
**Goal:** Go beyond listing — provide guidance.
- [x] CVE ingestion (OSV.dev API integration)
- [x] Vulnerability badges per version (SecurityBadge component)
- [x] Version stability scoring (pre-release filtering, recommended logic)
- [x] "Safe to use" / "No known vulnerabilities" indicators
- [x] Security disclaimer (collapsible panel)
- [x] Graceful error handling (CacheErrorHandler)
- [ ] Deeper NVD integration for CVSS scores
- [ ] Historical vulnerability trends

### Exit Criteria
* ✅ Users can quickly identify risky dependencies
* ✅ Clear, non-alarmist security UX

---

## 🟢 Milestone 8: Gap-Closing Features ✅
**Goal:** Close the most impactful gaps vs. mvnrepository.com.
- [x] **Reverse Dependencies ("Used By")**: Solr `d:"g:a"` queries for finding dependent artifacts
  - [x] Backend: `getReverseDependencies()` + `getReverseDependencyCount()` with Redis caching
  - [x] Controller: `GET /api/maven/artifact/{g}/{a}/usedby` (paginated) + `/usedby/count`
  - [x] Frontend: "Used by N" badge in header + expandable paginated list
- [x] **Embeddable Badges**: SVG version badges for GitHub READMEs
  - [x] Backend: `BadgeController` with inline SVG generation (flat + rounded styles)
  - [x] Frontend: Badges section on artifact page with preview + copyable snippets (Markdown/HTML/RST)
- [x] **Enhanced Homepage**: Live data feeds replacing static artifact list
  - [x] `HomeContent` component fetching trending + recently updated artifacts

### Exit Criteria
* ✅ Artifact pages show "Used By" count and dependents list
* ✅ Badge endpoint returns valid SVG at `/badge/{g}/{a}`
* ✅ Homepage shows live trending + recently updated data

---

## 🔵 Milestone 9: Ecosystem & Tooling (Optional / Long-Term)
**Goal:** Turn modernmvn into a platform.
- [ ] Public API (read-only)
- [ ] CLI tool (`modernmvn analyze`)
- [ ] Saved analyses (optional login)
- [ ] Usage analytics
- [ ] Rate limiting & abuse protection
- [ ] Categories / tag browsing
- [ ] Popularity / usage metrics
- [ ] BOM support

### Exit Criteria
* Repeat users
* External integrations appear

---
