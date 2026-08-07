# Event Model

`mango4j-flowsink` models a root flow as a `FlowEvent` and nested work items as `StepEvent` entries inside that flow.

## `FlowEvent`

`FlowEvent` is the root container for one unit of work.

It stores:

- the flow name
- start and end timestamps
- attributes
- event context
- nested step events
- trace identifiers
- status and throwable information
- return value, when the runtime captures one

The Spring runtime creates a `FlowEvent` when a `@Flow` method starts and updates it as the flow completes or fails.

## `StepEvent`

`StepEvent` represents a nested operation recorded inside an active flow.

It stores:

- the step name
- the step start and end timestamps
- elapsed time in nanoseconds
- attributes attached to that step
- the step kind

`StepEvent` entries are held on the parent `FlowEvent` in `events()`.

Example:

```java
import java.util.Map;

import ie.bitstep.mango.flowsink.model.FlowEvent;
import ie.bitstep.mango.flowsink.model.FlowAttributes;
import ie.bitstep.mango.flowsink.model.StepEvent;

FlowEvent flow = FlowEvent.builder().name("checkout.submit").build();
flow.beginStepEvent("checkout.stock.reserve", 10L, 100L, new FlowAttributes(), "CLIENT");
flow.endStepEvent(20L, 175L, Map.of("sku", "SKU-1"));

StepEvent step = flow.events().get(0);
```

## `FlowMeta`

`FlowMeta` is the small metadata carrier used internally when the processor moves information from the aspect or web interceptor into the root event.

It can carry:

- span kind
- status code and message
- trace identifiers
- tracestate

Think of it as runtime metadata, not as a user-facing event object.

## Attributes And Status

The current implementation uses the `FlowAttributes` and `FlowStatus` value objects inside the model.

- `FlowAttributes` is a mutable ordered map wrapper for attributes.
- `FlowStatus` pairs an OpenTelemetry status code with an optional message.

These are support types used by `FlowEvent` and `StepEvent`, not separate event hierarchies.

## Practical Reading Model

If you are reading the event stream from the outside:

- `FlowEvent` is the thing to think about first
- `StepEvent` is the nested timeline underneath it
- `FlowMeta` is a transient assembly input used while the event is being built
