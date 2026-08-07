# Flow Sinks

`@FlowSink` beans are scanned by the Spring runtime and compiled into event handlers.

## Basic Sink

```java
import java.util.Map;

import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutSink {

    @OnFlowStarted
    void onStarted(FlowEvent event) {
    }

    @OnFlowCompleted
    void onCompleted(@PullAllAttributes Map<String, Object> attributes) {
    }
}
```

## Scope Matching

Use `@OnFlowScope` at class or method level to limit which flow names match.

- `"checkout."` matches `checkout.submit` and `checkout.stock.reserve`
- `"checkout"` matches `checkout` and nested names under `checkout.`
- blank scope matches anything
- `@OnFlowScope` is repeatable; multiple scopes on the same element are combined with **OR** — the flow name need only match one of them

**Class + method scopes are intersective, not override.** When `@OnFlowScope` appears on both the class and a method, the flow name must satisfy the class scope **and** the method scope. A method-level scope narrows the class-level scope; it does not replace it.

```java
@FlowSink
@OnFlowScope("checkout.")        // class filter: checkout.* only
class CheckoutSink {

    @OnFlowScope("checkout.payment.")  // method filter: checkout.payment.* only (AND'd with class)
    @OnFlowCompleted
    void onPaymentCompleted(FlowEvent event) { }

    @OnFlowCompleted               // no method filter — class filter applies: checkout.*
    void onAnyCheckoutCompleted(FlowEvent event) { }
}
```

## Parameter Binding

Sink methods can bind:

- the full `FlowEvent`
- pulled attributes via `@PullAttribute`
- pulled context values via `@PullContextValue`
- all attributes via `@PullAllAttributes`
- all context via `@PullAllContextValues`
- failures via a plain `Throwable` parameter (receives the exception as thrown)
- root-cause extraction via `@FlowException(Source.ROOT_CAUSE)` on a `Throwable` parameter

## Success And Failure Handlers

Use `@OnFlowSuccess` for success-specific callbacks and `@OnFlowFailure` for failure-specific callbacks.

```java
import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowSuccess;
import ie.bitstep.mango.instrument.annotations.PullAttribute;
import ie.bitstep.mango.instrument.annotations.PullContextValue;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutStatusSink {

    @OnFlowSuccess
    void onSuccess() {
    }

    @OnFlowFailure
    void onFailure(
            Throwable error,
            @PullAttribute("user.id") String userId,
            @PullContextValue("tenant.id") String tenantId) {
    }
}
```

`@OnFlowCompleted` fires for any terminal outcome — both success and failure. Use it when you want to run after the flow ends regardless of outcome. It can declare a `Throwable` parameter to inspect the error when the flow failed.

### `@FlowException` and root-cause extraction

A plain `Throwable` parameter receives the exception exactly as thrown. `@FlowException` without a value (`Source.THROWN` is the default) is equivalent — it adds nothing and can be omitted.

The annotation is only meaningful when you want the ultimate root cause instead:

```java
@OnFlowFailure
void onFailure(
        @FlowException(FlowException.Source.ROOT_CAUSE) Throwable root,
        @PullAttribute("user.id") String userId) {
    // root is the result of walking getCause() to the end of the cause chain
}
```

## Fallbacks

`@OnFlowNotMatched` methods run when a sink is in play but none of its normal handlers matched the event.

That is useful for assertions, diagnostics, or missed-route bookkeeping.
