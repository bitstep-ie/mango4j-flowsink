# mango4j-flowsink

[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-flowsink&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-flowsink)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-flowsink&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-flowsink)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-flowsink&metric=coverage)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-flowsink)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=bitstep-ie_mango4j-flowsink&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=bitstep-ie_mango4j-flowsink)


[![CI](https://github.com/bitstep-ie/mango4j-flowsink/actions/workflows/packages.yml/badge.svg)](https://github.com/bitstep-ie/mango4j-flowsink/actions/workflows/packages.yml)
[![CodeQL](https://github.com/bitstep-ie/mango4j-flowsink/actions/workflows/codeql.yml/badge.svg)](https://github.com/bitstep-ie/mango4j-flowsink/actions/workflows/codeql.yml)
[![Dependabot](https://github.com/bitstep-ie/mango4j-flowsink/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/bitstep-ie/mango4j-flowsink/actions/workflows/dependabot/dependabot-updates)

<br/>

<div align="center">
    <a href="https://github.com/bitstep-ie/mango4j-flowsink">
    <picture>
        <source srcset="documentation/docs/assets/mango-with-text-black.png" media="(prefers-color-scheme: light)">
        <source srcset="documentation/docs/assets/mango-with-text-white.png" media="(prefers-color-scheme: dark)">
        <img src="documentation/docs/assets/mango-with-text-black.png" alt="mango Logo">
    </picture>
    </a>

  <h3 align="center">mango4j-flowsink</h3>

  <p align="center">
    Annotation‑based flow and step instrumentation for Spring applications.
    <br/><br/>
    <a href="https://bitstep-ie.github.io/mango4j-flowsink/latest"><strong>View Documentation</strong></a>
    <br/><br/>
    <a href="https://github.com/bitstep-ie/mango4j-examples">Example Application</a>
    &middot;
    <a href="https://github.com/bitstep-ie/mango4j-flowsink/issues/new?template=bug_report.md">Report Bug</a>
    &middot;
    <a href="https://github.com/bitstep-ie/mango4j-flowsink/issues/new?template=feature_request.md">Request Feature</a>
  </p>
</div>

<br/>

# Introduction

**mango4j‑flowsink** provides a simple, annotation‑first programming model for capturing flows, steps, metadata, and execution lifecycle events in Spring applications.

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
    <artifactId>mango4j-flowsink-spring</artifactId>
    <version>0.1.1</version>
</dependency>
```

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-flowsink-spring-boot</artifactId>
    <version>0.1.1</version>
</dependency>
```

Enable instrumentation:

```java
import ie.bitstep.mango.flowsink.spring.EnableMangoFlowSink;

@SpringBootApplication
@EnableMangoFlowSink
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

import ie.bitstep.mango.flowsink.model.FlowEvent;
import ie.bitstep.mango.flowsink.annotations.OnFlowCompleted;
import ie.bitstep.mango.flowsink.annotations.OnFlowScope;
import ie.bitstep.mango.flowsink.annotations.OnFlowStarted;
import ie.bitstep.mango.flowsink.annotations.PullAllAttributes;
import ie.bitstep.mango.flowsink.spring.annotations.FlowSink;

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

*   📘 Docs: <https://bitstep-ie.github.io/mango4j-flowsink/latest>
*   🔎 Examples: <https://github.com/bitstep-ie/mango4j-examples>

***
