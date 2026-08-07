# Spring Boot

The repo keeps plain Spring and Boot support separate on purpose.

## Modules

- `mango4j-flowsink-spring`: core Spring runtime and `@EnableMangoFlowSink`
- `mango4j-flowsink-spring-boot`: Boot auto-configuration layer

## Why Split Them

- applications can use annotations without dragging in Boot
- runtime code stays reusable in non-Boot Spring applications
- Boot wiring remains thin and replaceable

## Recommended Usage

For Boot applications, depend on `mango4j-flowsink-spring-boot`. It builds on the same Spring runtime module and keeps the enable-style programming model available.

For library code, depend on the annotations module only.

## What Boot Adds

The Boot module contributes auto-configuration for:

- the core instrumentation runtime
- servlet trace filters when servlet APIs are present
- WebFlux trace filters when reactive web classes are present

That keeps the application-side entry point small:

```java
@SpringBootApplication
@EnableMangoFlowSink
public class DemoApplication {
}
```

If you already have an application configuration class, `@EnableMangoFlowSink` can live there instead of on the main application class.
