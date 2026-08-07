# Lifecycle Semantics

This section describes the lifecycle events emitted by a flow and the annotations used to match them.

## Lifecycle Events

Flow handlers react to one of three lifecycle states:

- `STARTED`
- `COMPLETED`
- `FAILED`

Use these annotations to listen for them:

- `@OnFlowStarted`
- `@OnFlowCompleted`
- `@OnFlowSuccess`
- `@OnFlowFailure`
- `@OnFlowLifecycle(...)`

## When To Use Each One

- `@OnFlowStarted` for setup, logging, and request bookkeeping
- `@OnFlowCompleted` for work that should run when the flow reaches completion
- `@OnFlowSuccess` for success-specific callbacks
- `@OnFlowFailure` for failure-specific callbacks
- `@OnFlowLifecycle(...)` when you want to name the lifecycle value directly in the handler

## Plain English

`@OnFlowSuccess` fires only when the flow finished without throwing — lifecycle is `COMPLETED`.

`@OnFlowCompleted` fires for any terminal outcome — both `COMPLETED` and `FAILED`. Use it when you need to run cleanup or logging regardless of whether the flow succeeded or failed.

`@OnFlowFailure` fires only when the flow failed — lifecycle is `FAILED`. It is dispatched before `@OnFlowCompleted` handlers for the same event.

`@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)` means the handler should run when the started event is emitted.

## Combining `@OnFlowFailure` and `@OnFlowCompleted` on the Same Sink

This is the most common multi-annotation pattern: one method handles the failure specifically, and another runs for any terminal outcome.

```java
@FlowSink
class OrderSink {

    @OnFlowFailure
    void alertOnFailure(Throwable error, @PullAttribute("order.id") String orderId) {
        // runs first, while the failure is still "hot" — good for alerts and rollback
    }

    @OnFlowCompleted
    void recordMetrics(FlowEvent event) {
        // runs after alertOnFailure when the flow failed, and alone when the flow succeeded
        // good for audit logging, metrics, and resource cleanup regardless of outcome
    }
}
```

When a flow fails and this sink is in play, the runtime guarantees `alertOnFailure` runs before `recordMetrics`. That ordering matters when the failure handler sets state the cleanup handler reads, or when an alert must fire before the flow's data is summarised.

## `@OnFlowCompleted` vs `@OnFlowLifecycle(COMPLETED)`

These two are **not** equivalent, despite the similar names.

| Annotation | Fires on `COMPLETED` | Fires on `FAILED` |
|---|:---:|:---:|
| `@OnFlowCompleted` | ✓ | ✓ |
| `@OnFlowLifecycle(COMPLETED)` | ✓ | — |
| `@OnFlowSuccess` | ✓ | — |

`@OnFlowLifecycle(COMPLETED)` and `@OnFlowSuccess` are equivalent to each other. If you replace `@OnFlowCompleted` with `@OnFlowLifecycle(COMPLETED)`, the handler silently stops receiving `FAILED` events.

## `@OnFlowFailure` vs `@OnFlowLifecycle(FAILED)`

Both annotations filter for the `FAILED` lifecycle, but they differ in dispatch position.

`@OnFlowFailure` runs in a dedicated first pass, before any other handler on the same sink. `@OnFlowLifecycle(FAILED)` is treated as an ordinary lifecycle filter and runs in the second pass alongside `@OnFlowCompleted` handlers, in scanner order.

| Annotation | Runs when | Dispatch pass |
|---|---|---|
| `@OnFlowFailure` | flow failed | first — before all others |
| `@OnFlowLifecycle(FAILED)` | flow failed | second — scanner order |
| `@OnFlowCompleted` | flow failed or succeeded | second — scanner order |

**When to use `@OnFlowLifecycle(FAILED)` instead of `@OnFlowFailure`:** when you want a single method to handle specific combinations of lifecycle events using `@OnFlowLifecycles({@OnFlowLifecycle(FAILED), @OnFlowLifecycle(STARTED)})` and the first-pass guarantee is not required. If you only need to react to failure, `@OnFlowFailure` is simpler and runs earlier.

## `@OnAllLifecycles`

`@OnAllLifecycles` is a shorthand that matches every lifecycle — `STARTED`, `COMPLETED`, and `FAILED`. It is equivalent to writing `@OnFlowLifecycles({@OnFlowLifecycle(STARTED), @OnFlowLifecycle(COMPLETED), @OnFlowLifecycle(FAILED)})`, but with one important difference: it is forward-compatible. If a new `Lifecycle` value is added in the future, `@OnAllLifecycles` picks it up automatically, whereas an explicit list would need to be updated in every sink that uses it.

Use it at the class level to allow handler methods to react to any lifecycle event without requiring each method to declare its own lifecycle annotation:

```java
@FlowSink
@OnFlowScope("checkout.")
@OnAllLifecycles
class CheckoutSink {

    @OnFlowStarted
    void onStarted() { ... }

    @OnFlowSuccess
    void onSuccess() { ... }

    @OnFlowFailure
    void onFailure(Throwable error) { ... }
}
```

Use it at the method level when you need a single handler that observes every lifecycle transition for the same flow — for example, a general-purpose audit logger.

```java
@FlowSink
class AuditSink {

    @OnAllLifecycles
    void onAnyLifecycle(FlowEvent event) {
        // fires for STARTED, COMPLETED, and FAILED
    }
}
```

**When to prefer `@OnAllLifecycles` over an explicit list:**

- You want future-proof behaviour without remembering to update every sink if a new lifecycle is introduced.
- The handler truly does not care which lifecycle triggered it — it needs the event regardless.

**When to prefer an explicit `@OnFlowLifecycles` list:**

- The handler should fire only for a specific subset of lifecycles (e.g. `STARTED` and `FAILED` but not `COMPLETED`). `@OnAllLifecycles` cannot express that.

## Failure Without an Exception

When `@Flow` is used on a Spring MVC controller method and that controller returns a 4xx or 5xx response
without throwing, the flow is marked `FAILED` with a null throwable. A sink handler method that declares a
`Throwable` parameter will receive `null` in this case. Null-check before using it.

## Async Controller Limitation

`FlowWebInterceptor` tracks active flows using a `ThreadLocal`. For async Spring MVC controllers
(methods returning `CompletableFuture`, `DeferredResult`, `Callable`, etc.) the servlet thread that
runs `preHandle` is returned to the pool before the response is written. When `afterCompletion` fires
on a different thread the flow context is gone and the `COMPLETED` or `FAILED` event is not dispatched.

The `STARTED` event will have been dispatched; the terminal event will not. A warning is logged when
this is detected.

Place `@Flow` on the service-layer method invoked from the async chain instead, where the full call
stays on one thread.

## Example

```java
import java.util.Map;

import ie.bitstep.mango.flowsink.annotations.OnFlowCompleted;
import ie.bitstep.mango.flowsink.annotations.OnFlowFailure;
import ie.bitstep.mango.flowsink.annotations.OnFlowScope;
import ie.bitstep.mango.flowsink.annotations.PullAllAttributes;
import ie.bitstep.mango.flowsink.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutLifecycleSink {

    @OnFlowCompleted
    void onCheckoutCompleted(@PullAllAttributes Map<String, Object> attributes) {
    }

    @OnFlowFailure
    void onCheckoutFailed(Throwable error) {
    }
}
```
