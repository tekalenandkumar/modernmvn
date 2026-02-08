# Deployment Guide

This project is designed to be deployed with a separated Frontend (Next.js on Vercel) and Backend (Spring Boot on Railway/Render).

## 1. Backend Deployment (Railway or Render)

The backend is a Spring Boot application that requires a PostgreSQL database and Redis cache.

### Option A: Railway (Recommended)

1.  **Create a New Project** on [Railway](https://railway.app/).
2.  **Add a Service** from your GitHub repo (select the `modernmvn` repo).
3.  **Add Database**: Add a PostgreSQL service to your project.
4.  **Add Cache**: Add a Redis service to your project.
5.  **Configure Backend Service**:
    *   **Root Directory**: Set to `/backend` (Railway settings).
    *   **Variables**: data from the attached Postgres & Redis services should auto-inject or you need to manually set:
        *   `DATABASE_URL`: (e.g., `postgresql://...`)
        *   `REDIS_URL`: (e.g., `redis://...`)
        *   `PORT`: Railway sets this automatically (app listens on it).
    *   **Build**: Railway uses Nixpacks by default but we included a `Dockerfile`. Consider settings "Builder" to Dockerfile if nixpacks fails. The provided Dockerfile is in `/backend/Dockerfile`.

### Option B: Render

1.  **Create a Web Service** on [Render](https://render.com/).
2.  **Connect GitHub**: Select the `modernmvn` repo.
3.  **Root Directory**: `backend`.
4.  **Runtime**: Docker.
5.  **Environment Variables**:
    *   Add PostgreSQL and Redis instances on Render (or externally).
    *   Set `DATABASE_URL` and `REDIS_URL` / `REDIS_HOST`.

## 2. Frontend Deployment (Vercel)

The frontend is a Next.js application.

1.  **Import Project** on [Vercel](https://vercel.com/new).
2.  **Select Repository**: `modernmvn`.
3.  **Root Directory**: Select `frontend`.
4.  **Environment Variables**:
    *   `BACKEND_URL`: Set this to the public URL of your deployed backend (e.g., `https://modernmvn-backend.up.railway.app`).
    *   **Important**: Do NOT include a trailing slash.
    *   Example: `https://my-backend-app.railway.app`

## 3. How It Works

*   The Next.js `next.config.ts` is configured with a **Rewrite**.
*   All requests to `/api/maven/*` sent to the frontend are proxied to `${BACKEND_URL}/api/maven/*`.
*   This avoids CORS issues and keeps the API structure clean.

## 4. Local Development

*   **Backend**: Runs on `localhost:8080`.
*   **Frontend**: Runs on `localhost:3000`.
*   `next.config.ts` defaults `BACKEND_URL` to `http://localhost:8080` if the variable is not set.
