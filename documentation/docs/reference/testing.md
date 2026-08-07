# Testing And Mutation Coverage

The runtime modules have:

- strong unit-test coverage across core and Spring runtime behavior
- PIT mutation testing configured in the Maven build

## Commands

Run the normal test suite:

```bash
mvn -q verify
```

Run mutation testing:

```bash
mvn -q -Ppitest verify
```

## Scope

Mutation testing is focused on the runtime-heavy modules:

- `mango4j-flowsink-core`
- `mango4j-flowsink-spring`

The annotations module is intentionally excluded because it is mostly declarative API surface, and the Boot module remains thin configuration glue.

## Local Outputs

The Maven build writes generated reports into each module's `tmp/` directory. Those directories are build output and do not need to be checked in.
