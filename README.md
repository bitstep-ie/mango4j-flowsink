# mango4j-instrument

[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-instrument&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-instrument)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-instrument&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-instrument)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-instrument&metric=coverage)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-instrument)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-instrument&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-instrument)


[![CI](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/packages.yml/badge.svg)](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/packages.yml)
[![CodeQL](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/codeql.yml/badge.svg)](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/codeql.yml)
[![Dependabot](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/dependabot/dependabot-updates)

<br/>

<div align="center">
    <a href="https://github.com/bitstep-ie/mango4j-instrument">
    <picture>
        <source srcset="documentation/docs/assets/mango-with-text-black.png" media="(prefers-color-scheme: light)">
        <source srcset="documentation/docs/assets/mango-with-text-white.png" media="(prefers-color-scheme: dark)">
        <img src="documentation/docs/assets/mango-with-text-black.png" alt="mango Logo">
    </picture>
    </a>

  <h3 align="center">mango4j-instrument</h3>

  <p align="center">
    Annotation‑based flow and step instrumentation for Spring applications.
    <br/><br/>
    <a href="https://bitstep-ie.github.io/mango4j-instrument/latest"><strong>View Documentation</strong></a>
    <br/><br/>
    <a href="https://github.com/bitstep-ie/mango4j-examples">Example Application</a>
    &middot;
    <a href="https://github.com/bitstep-ie/mango4j-instrument/issues/new?template=bug_report.md">Report Bug</a>
    &middot;
    <a href="https://github.com/bitstep-ie/mango4j-instrument/issues/new?template=feature_request.md">Request Feature</a>
  </p>
</div>

<br/>

# Introduction

**mango4j‑instrument** provides a simple, annotation‑first programming model for capturing flows, steps, metadata, and execution lifecycle events in Spring applications.

Instrument your code with:

*   `@Flow` – root units of work
*   `@Step` – nested operations
*   `@PushAttribute` / `@PushContextValue` – metadata enrichment
*   `@Kind(SpanKind.*)` – span role
*   `@FlowSink` – declarative lifecycle handlers

The framework is **backend‑agnostic** and integrates cleanly with Spring AOP, giving you structured execution events without needing Micrometer, OpenTelemetry, or any specific telemetry pipeline.

***

# Quick Start

Add either the plain Spring runtime or the Spring Boot integration:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring</artifactId>
    <version>0.1.1</version>
</dependency>
```

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring-boot</artifactId>
    <version>0.1.1</version>
</dependency>
```

Enable instrumentation:

```java
import ie.bitstep.mango.instrument.spring.EnableMangoInstrumentation;

@SpringBootApplication
@EnableMangoInstrumentation
public class DemoApplication {}
```

Instrument a flow:

```java
@RestController
class CheckoutController {

    @Flow(name = "demo.checkout")
    public String checkout(@PushAttribute("user.id") String userId) {
        return "ok";
    }
}
```

Add a nested step:

```java
@Component
class StockService {

    @Step("demo.stock.verify")
    public void verify(@PushAttribute("sku") String sku) {}
}
```

Listen to lifecycle events:

```java
import java.util.Map;

import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("demo.")
public class DemoSink {

    @OnFlowStarted
    public void onStart(FlowEvent event) {}

    @OnFlowCompleted
    public void onCompleted(@PullAllAttributes Map<String, Object> attrs) {}
}
```

Lifecycle and outcome semantics:

* `@OnFlowLifecycle(...)`, `@OnFlowSuccess`, and `@OnFlowFailure` are event-lifecycle filters. They match the lifecycle of the emitted `FlowEvent`.
* `@OnFlowSuccess` is the success-specific hook.
* `@OnFlowFailure` is the failure-specific hook.
* `@OnFlowCompleted` is for handlers that should run when a flow reaches completion without needing to name success or failure in the annotation.

***

# Learn More

*   📘 Docs: <https://bitstep-ie.github.io/mango4j-instrument/latest>
*   🔎 Examples: <https://github.com/bitstep-ie/mango4j-examples>

***
