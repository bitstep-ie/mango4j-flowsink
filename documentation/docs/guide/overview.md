# Overview

`mango4j-flowsink` gives you a structured event stream around application work:

- `@Flow` marks a root unit of work.
- `@Step` marks nested work within a flow.
- `@PushAttribute` and `@PushContextValue` enrich the emitted event.
- `@FlowSink` lets you react to lifecycle events declaratively.
- servlet and WebFlux tracing can populate flow context from inbound trace headers.

The library is intentionally split so application code can depend on annotations without taking on Spring runtime dependencies.

## Typical Flow

1. Annotate a service or controller method with `@Flow`.
2. Annotate nested operations with `@Step`.
3. Add attributes or context values from parameters.
4. Register one or more `@FlowSink` handlers.
5. Enable the runtime with Spring or Spring Boot support.

## Runtime Shape

At runtime the Spring module:

- intercepts `@Flow` and `@Step` methods with AOP
- constructs `FlowEvent` objects in the core module
- records nested `StepEvent` entries inside the active flow
- dispatches them to compiled sink handlers
- tracks the current flow context in thread-local support
- optionally imports trace headers into the current context

The result is a backend-agnostic event model that can be adapted to logging, metrics, tracing, audit sinks, or test assertions.

## Example Event Stream

```java
import org.springframework.stereotype.Service;

import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.Step;

@Service
class CheckoutService {

    private final StockService stockService;

    CheckoutService(StockService stockService) {
        this.stockService = stockService;
    }

    @Flow("checkout.submit")
    public String checkout(@PushAttribute("user.id") String userId) {
        stockService.reserve("SKU-1");
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

That single method can emit:

- a started event when execution begins
- a completed event when the method returns normally
- a failed event if the method exits by throwing
