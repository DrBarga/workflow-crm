# Workflow CRM Backend

Java 21 and Spring Boot backend for location, contact and task workflows. The application uses Spring Modulith to enforce boundaries between business modules.

## Local development

From this directory:

```bash
cp .env.example .env
docker compose up --build
```

Set local credentials in `.env` before starting. Keep that file outside version control. The example config uses local infrastructure and Mailpit for email. External services require your own configuration.

The backend runs on `http://localhost:8080`, the frontend on `http://localhost:3000`, and the local inbox on `http://localhost:8025`. Ports can be changed through the environment file.

To load synthetic local data explicitly:

```bash
docker compose run --rm seed
```

Stop the stack while retaining its volumes:

```bash
docker compose down
```

## Verification

Java 21 is required. The Maven wrapper downloads the configured Maven version.

```bash
./mvnw verify
./mvnw -Pintegration verify
```

The integration profile uses Testcontainers and requires Docker. Test data and credentials used by the tests belong to disposable containers.

## Modules

Business modules separate API contracts, domain behavior and infrastructure adapters. They cover authentication, users, locations, contacts, tasks, files, notifications, audit and integrations. PostgreSQL migrations are managed with Liquibase; Redis holds session state; MinIO stores attachments; RabbitMQ supports asynchronous workflows.

## API documentation

- [Authorization](docs/api-authorization.md)
- [Contacts](docs/contact-crud.md)
- [Object lifecycle and data model](docs/object-data-model.md)
- [Object editing](docs/object-edit.md)
- [Imports](docs/object-import.md)
- [Files](docs/object-files.md)
- [Task template selection](docs/task-template-selection.md)
- [Task preview](docs/task-set-preview.md)
- [Frontend integration APIs](docs/frontend-object-api.md)

Additional OpenAPI documents are in `docs/openapi`.
