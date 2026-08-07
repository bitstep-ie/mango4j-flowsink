# Flow Context

`FlowContext` is the programmatic alternative to `@PushAttribute` and `@PushContextValue`. Use it when the value you
want to attach isn't a method parameter — inside a loop, behind a conditional, computed partway through a method body,
or from code that doesn't own the `@Flow`/`@Step` method signature.

## Getting An Instance

`FlowContext` is registered as a Spring bean by the runtime configuration. Inject it like any other bean:

```java
import org.springframework.stereotype.Service;

import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.context.FlowContext;

@Service
class CheckoutService {

    private final FlowContext flowContext;

    CheckoutService(FlowContext flowContext) {
        this.flowContext = flowContext;
    }

    @Flow("checkout.submit")
    public String checkout(String sku) {
        for (String warehouse : candidateWarehouses(sku)) {
            if (hasStock(warehouse, sku)) {
                flowContext.put("warehouse.id", warehouse);
                break;
            }
        }
        return "ok";
    }
}
```

## API

| Method | Adds to | Notes |
|---|---|---|
| `put(key, value)` / `putAttr(key, value)` | attributes | identical — `put` is the short form |
| `putAll(map)` / `putAllAttrs(map)` | attributes | identical — `putAll` is the short form |
| `putContext(key, value)` | event context | |
| `putAllContext(map)` | event context | |

`put`/`putAttr`/`putContext` return the value you passed in, so you can push and use a value in one expression:

```java
String warehouseId = flowContext.put("warehouse.id", resolveWarehouse(sku));
```

## Requires An Active Flow

Every method checks `FlowProcessorSupport.currentContext()` first. If there's no active flow on the current thread —
called outside any `@Flow`/`@Step` method, or after the flow has already completed — the call is a **silent no-op**:
the value is simply not recorded, and a `DEBUG`-level log line is written (nothing is thrown). Don't rely on
`FlowContext` calls outside the extent of an active flow.

## Validation

Values pushed through `FlowContext` go through the same `FlowAttributeValidator` as values pushed via
`@PushAttribute`/`@PushContextValue` — both paths converge on the flow processor's validation step. There's no
separate, weaker validation path for the programmatic API.

## When To Use This vs. The Annotations

- Prefer `@PushAttribute`/`@PushContextValue` when the value is already available as a method parameter — it's
  declarative and colocated with the method signature.
- Reach for `FlowContext` when the value isn't a parameter: it's computed conditionally, derived partway through the
  method, or produced by code several calls away from the annotated `@Flow`/`@Step` method itself.
