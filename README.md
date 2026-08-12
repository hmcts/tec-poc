# TEC API POC

This is a local-only proof of concept. It is not deployed or published to any environment. Docker Compose is the only
supported runtime and supplies the API and its PostgreSQL database.

## Prerequisites

- Java 21
- Docker with Docker Compose V2

Gradle is provided by the checked-in `./gradlew` wrapper.

## Run the local stack

Build the application and start PostgreSQL and the API:

```bash
./bin/run-in-docker.sh
```

The local services are:

- API: http://localhost:8080
- Health: http://localhost:8080/health
- PostgreSQL: `localhost:5432`, database/user/password `tec`

Stop the stack while retaining database data (use `docker-compose` instead if Compose is installed as a standalone
command):

```bash
docker compose down
```

Delete the containers and all local database data:

```bash
docker compose down -v
```

## CCD Config Generator

The project uses `hmcts.ccd.sdk` 6.32.0 with its decentralised PostgreSQL runtime. Elasticsearch runtime indexing is
disabled. Start PostgreSQL before generating CCD configuration:

```bash
docker compose up -d postgres
./gradlew generateCCDConfig
```

Output is written to `build/ccd-definition`. No case-type files are currently produced because this integration slice
does not define a TEC `CCDConfig`, case model, events, states, or roles.

## Tests

Run unit and integration checks (integration tests use a temporary PostgreSQL Testcontainer):

```bash
./gradlew clean check
```

With the Compose stack running, exercise the tests that call the live local API:

```bash
./gradlew functional smoke
```

IDAM is represented by a placeholder local URL and is not called during startup. No Helm chart, Terraform,
Elasticsearch, cloud secrets, image publication, or deployment environment is supported by this repository.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
