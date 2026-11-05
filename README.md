# Fyreplace API

> Next-gen API for [Fyreplace](https://fyreplace.net)

A reactive Quarkus (Java 25) + gRPC re-implementation of the legacy
[Django API](https://github.com/fyreplace/fyreplace-api-django), which remains the specification for
behavior being ported. API contracts live in
[fyreplace-protos](https://github.com/fyreplace/fyreplace-protos), vendored here as a submodule at
`src/main/proto/protos`. See [`AGENTS.md`](AGENTS.md) for the architecture map and conventions
(humans welcome too).

## Requirements

- JDK 25
- PostgreSQL 18
- Node/NPM (to compile the email templates with `./gradlew emails`)
- Docker (for development with Dev Services)

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

Quarkus Dev Services will start a PostgreSQL container for you via Docker. If a local `.env` file
points `QUARKUS_DATASOURCE_*` at your own database, that takes precedence.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/fyreplace-api-*-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## License

Fyreplace is made available under the AGPLv3+ license (see [COPYING.txt](COPYING.txt)).
