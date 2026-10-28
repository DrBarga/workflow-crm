# Workflow CRM Client

Next.js application for the CRM's authentication and operational interface. Built with React, TypeScript and Redux Toolkit.

## Development

Use Node.js 22. From this directory:

```bash
npm ci
cp .env.example .env.local
npm run dev
```

Configure the backend address through `NEXT_PUBLIC_API_BASE_URL`. The backend must be running for authenticated workflows.

## Checks

```bash
npm run lint
npm run lint:fsd
npm run typecheck
npm run build
```

The source follows a feature-oriented structure with application, page, widget, feature, entity and shared layers. ESLint and `scripts/check-fsd.mjs` check import boundaries and folder conventions.

The interface is under active development. Backend capability does not imply that every workflow already has a complete frontend screen.
