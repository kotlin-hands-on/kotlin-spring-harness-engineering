# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.2/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.2/gradle-plugin/packaging-oci-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.2/reference/web/servlet.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.1.2/reference/actuator/index.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)


# Database migrations

Schema changes are Flyway scripts in `backend/src/main/resources/db/migration/`; Exposed never creates DDL at runtime
(`spring.exposed.generate-ddl=false`).

To generate a script from the Exposed tables in `com.example.harnessengineeringspring.persistence`:

```shell
./gradlew :backend:generateMigrations
```

* Docker is required: the task replays the committed Flyway history in a `postgres:18-alpine` Testcontainers database
  and diffs it against the Exposed tables.
* The task is **explicit only** - it is never part of `build`, `check`, `assemble` or `processResources`.
* Review the generated file before committing it.
* While no tables exist the task completes with `No migrations found` / `Generated 0 migrations` and writes nothing.

# Local development

Start Postgres (`postgres:18-alpine`, database/user/password `app`, port 5432), then run the backend:

```shell
docker compose up -d
./gradlew :backend:bootRun
```

`GET http://localhost:8080/actuator/health` reports `UP` once the application is connected.
`bootRun` never starts containers implicitly.

The datasource defaults match `compose.yaml`; override them with environment variables:

| Variable      | Default                                 |
|---------------|-----------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/app`  |
| `DB_USER`     | `app`                                   |
| `DB_PASSWORD` | `app`                                   |

Stop the database with `docker compose down` (add `-v` to drop the data volume).
