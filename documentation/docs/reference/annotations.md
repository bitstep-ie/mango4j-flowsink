# Annotations

## Flow Definition

- `@Flow`: marks a root unit of work
- `@Step`: marks nested work inside a flow
- `@Kind`: sets the span kind metadata for an event

## Metadata Push

- `@PushAttribute`
- `@PushContextValue`

These annotate method parameters and push values into the active event.

## Sink Definition

- `@FlowSink`
- `@OnFlowStarted`
- `@OnFlowCompleted`
- `@OnFlowFailure`
- `@OnFlowSuccess`
- `@OnFlowLifecycle`
- `@OnFlowLifecycles`
- `@OnFlowNotMatched`
- `@OnFlowScope`
- `@OnFlowScopes`
- `@OnAllLifecycles`
- `@RequiredAttributes`
- `@RequiredEventContext`
- `@OrphanAlert`

## Sink Parameter Pull

- `@PullAttribute`
- `@PullContextValue`
- `@PullAllAttributes`
- `@PullAllContextValues`
- `@FlowException`

These are resolved by the Spring scanner when it compiles sink handlers.

## Semantics At A Glance

- `@Flow` and `@Step` both support `value` or `name`
- Do not place `@Flow` and `@Step` on the same method — the conflict detector throws `IllegalStateException` at application startup if this is found
- `@OnFlowScope` is repeatable and works on types or methods
- `@PushAttribute` and `@PushContextValue` forward values verbatim, so do not use them for secrets
- `@OnFlowFailure` fires only when the flow failed
- `@OnFlowSuccess` fires only when the flow completed successfully
- `@OnFlowCompleted` fires for any terminal outcome (both success and failure)
- `@OrphanAlert` controls the log level used when a step is auto-promoted to a flow
- `@OnFlowLifecycle` is repeatable and works on types or methods — on a type it restricts all handlers in that sink to the named lifecycle
- `@OnAllLifecycles` works on types or methods — on a method it opts that handler into all three lifecycle events

## Small Examples

```java
@Flow("checkout.submit")
public String submit(@PushAttribute("user.id") String userId) {
    return "ok";
}
```

```java
import java.util.Map;

import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutSink {

    @OnFlowCompleted
    void onCompleted(@PullAllAttributes Map<String, Object> attributes) {
    }
}
```
