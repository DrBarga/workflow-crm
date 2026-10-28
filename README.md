# Workflow CRM

A CRM and workflow platform for managing business locations, contacts and the work required to move each location through its lifecycle.

The system brings operational records, documents, discussions and task execution into one place. Access depends on a user's role, assigned responsibilities and geographic scope. Status changes follow defined business rules rather than unrestricted field updates.

## My contribution

I work on the Java backend as part of the development team. My work covers authentication and session management, role-based access, object and contact workflows, document handling, data imports and exports, and task automation. I also maintain API documentation and regression tests for these flows.

## Main capabilities

- **Location records and lifecycle management.** Structured records, searchable lists, map views, ownership changes, guarded status transitions and approval workflows.
- **Contact management.** Contact types, normalized phone numbers, duplicate warnings, record access and controlled deletion.
- **Task workflows.** Templates selected by business context, task-set previews, assignee selection, deadline calculation and scoped reassignment.
- **Data exchange.** CSV and XLSX imports with validation and row-level results, plus exports that respect access rules.
- **Documents and collaboration.** Object attachments, storage access controls, comments, mentions, notifications and change history.
- **Identity and access.** Invitations, password setup and recovery, trusted-device confirmation, rotating sessions and administrative session revocation.

## Architecture

The backend is a modular monolith built with Java 21, Spring Boot and Spring Modulith. Business modules separate API contracts, domain logic and infrastructure. Module boundaries are checked in the build so that cross-module dependencies remain explicit.

PostgreSQL stores business data, with schema changes managed through Liquibase. Redis supports authentication and session state. RabbitMQ supports asynchronous processing, and MinIO stores attachments. The frontend uses Next.js, React and TypeScript.

## Engineering focus

The main challenge is keeping business rules consistent across multiple ways of changing a record: interactive requests, status transitions, imports and generated tasks. Permission checks, validation, transaction boundaries and regression coverage are part of those workflows.

The backend includes unit and integration tests for authorization, lifecycle rules, task generation, data exchange and persistence behavior. Test source is available in `backend/src/test`.

## Project status

Active development. Backend workflows and frontend integration continue to evolve. This repository documents the implemented work; it is not a claim of a completed commercial release.

## Stack

Java 21, Spring Boot, Spring Security, Spring Modulith, PostgreSQL, Liquibase, Redis, RabbitMQ, MinIO, Docker, Maven, Next.js, React and TypeScript.

## Running locally

Docker Compose starts the backend, frontend and local infrastructure. From `backend/`, copy `.env.example` to `.env`, set local credentials, and run:

```bash
docker compose up --build
```

The application is available at `http://localhost:3000` and the backend at `http://localhost:8080`. Email is captured by Mailpit at `http://localhost:8025`. Credentials and external-service settings belong in the untracked environment file.

See the [backend guide](backend/README.md), [frontend guide](client/README.md) and [API documents](backend/docs/) for details.

## Verification

Use Java 21 and Docker for backend checks:

```bash
cd backend
./mvnw verify
./mvnw -Pintegration verify
```

Frontend lint, type checking and build commands are documented in the client guide. The interface is still being integrated with the backend.

## More work

[Portfolio](https://zelya-engineering.pages.dev/) · [GitHub profile](https://github.com/DrBarga)
