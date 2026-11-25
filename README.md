# PDF Signature Inspector

Inspect and analyze digitally signed PDF documents (PAdES / LibreSign compatible).

A Quarkus (Java 21) backend powered by Apache PDFBox and Bouncy Castle, with a
Vue 3 + Vite frontend served by the same HTTP server.

## Features

- Extract and validate PDF digital signatures
- Inspect certificate chains, signer info, CMS content info, revisions
- Analyze ASN.1 structures, basic constraints, trust status
- Interactive web UI (Vue 3, PrimeVue, Monaco editor, pdf.js)

## Requirements

- Java 21
- Maven 3.9+
- Node.js 22 (only needed to build the frontend; Maven installs it automatically)

## Build

```bash
# Backend + frontend (frontend built during prepare-package)
mvn clean package

# Skip frontend build
mvn clean package -Dskip.frontend=true
```

## Run (dev mode)

```bash
mvn quarkus:dev
```

The dev UI / Swagger UI is available at `http://localhost:8080/q/swagger-ui`.

## Frontend dev (optional)

```bash
cd ui
npm install
npm run dev
```

## Project layout

```
src/main/java/...   Quarkus application (resources, services, DTOs)
ui/                 Vue 3 frontend (Vite)
pom.xml             Maven build
```

## License

MIT — see [LICENSE](./LICENSE).
