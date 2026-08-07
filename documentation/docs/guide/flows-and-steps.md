# Flows And Steps

## `@Flow`

`@Flow` marks a root operation. The runtime emits:

- a started event when execution begins
- a completed event when the method returns normally
- a failed event when the method exits by throwing

Use either `@Flow("checkout.submit")` or `@Flow(name = "checkout.submit")`. The same applies to `@Step`.

Flow names should be stable and descriptive. Dot-separated names work well because scope matching understands prefixes like `checkout.`.

## `@Step`

`@Step` is for nested work within an active flow. A step behaves like a smaller lifecycle event inside the current root flow.

Example:

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

    @Flow(name = "checkout.submit")
    public void checkout(String sku) {
        stockService.reserve(sku);
    }
}
```

```java
import org.springframework.stereotype.Service;

import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.Step;

@Service
class StockService {

    @Step("checkout.stock.reserve")
    void reserve(@PushAttribute("sku") String sku) {
    }
}
```

## Important Spring AOP Constraint

Self-invocation does not pass through Spring proxies. If a method annotated with `@Step` is called from another method on the same bean, the aspect will not run.

Use cross-bean calls for nested steps if you need the runtime behavior.

## Metadata

You can enrich emitted events with:

- `@PushAttribute`
- `@PushContextValue`
- `@Kind`

Attributes are usually business metadata. Context values are usually runtime metadata that sinks may need for routing or correlation.

These annotations only work on method parameters. For values you want to push from inside a method body — computed
conditionally, inside a loop, or from code that doesn't own the annotated method's signature — see
[Flow Context](flow-context.md) for the programmatic equivalent.

### Example

```java
import io.opentelemetry.api.trace.SpanKind;
import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.annotations.Kind;
import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.PushContextValue;

@Service
class PaymentService {

    @Kind(SpanKind.SERVER)
    @Flow("payment.authorize")
    public String authorize(@PushAttribute("payment.id") String paymentId,
                            @PushContextValue("tenant.id") String tenantId) {
        return "authorized";
    }
}
```

## Flow Event Content

The emitted `FlowEvent` stores:

- the flow name
- timestamps
- attributes
- event context
- nested step events
- trace identifiers when present
- status and throwable details for completion or failure

Each nested step is represented as a `StepEvent` inside `FlowEvent.events()`.
