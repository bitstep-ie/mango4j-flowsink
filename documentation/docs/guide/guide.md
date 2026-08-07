# Guide

## What This Library Does

`mango4j-flowsink` adds annotation-driven flow and step instrumentation to Spring applications.

Use it when you want to capture:

- root flow lifecycles
- nested step lifecycles
- attributes and context values
- sink callbacks for completed or failed work
- inbound servlet or WebFlux trace context

## Start Here

- [Overview](overview.md)
- [Getting Started](getting-started.md)
- [Flows And Steps](flows-and-steps.md)
- [Flow Context](flow-context.md)
- [Flow Sinks](flow-sinks.md)
- [Web Tracing](web-tracing.md)
- [Spring Boot](spring-boot.md)
- [Event Model](../reference/event-model.md)

## How To Read The Docs

- `guide/getting-started.md` shows the shortest path to a working app.
- `guide/flows-and-steps.md` explains the event model and annotations on application methods.
- `guide/flow-context.md` explains the programmatic alternative to `@PushAttribute`/`@PushContextValue`.
- `guide/flow-sinks.md` explains how to receive events.
- `reference/event-model.md` explains `FlowEvent` and `StepEvent`.
- `reference/*.md` documents module boundaries and lifecycle semantics.

## Example

```java
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.Step;

@RestController
class CheckoutController {

    private final StockService stockService;

    CheckoutController(StockService stockService) {
        this.stockService = stockService;
    }

    @Flow("checkout.submit")
    public String checkout(@PushAttribute("user.id") String userId, String sku) {
        stockService.reserve(sku);
        return "ok";
    }
}

@Service
class StockService {

    @Step("checkout.stock.reserve")
    public void reserve(@PushAttribute("sku") String sku) {
    }
}
```
