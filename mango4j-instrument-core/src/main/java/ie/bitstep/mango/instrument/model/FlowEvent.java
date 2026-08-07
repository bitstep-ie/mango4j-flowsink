package ie.bitstep.mango.instrument.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.trace.SpanKind;

public class FlowEvent {
	private final String name;
	private final Instant timestamp;
	private Instant endTimestamp;
	private final FlowAttributes attributes;
	private final Map<String, Object> eventContext;
	private final List<StepEvent> events;
	private final Deque<OpenStep> openSteps;
	private SpanKind kind = SpanKind.INTERNAL;
	private FlowStatus status;
	private Throwable throwable;
	private Object returnValue;
	private String traceId;
	private String spanId;
	private String parentSpanId;

	private FlowEvent(Builder builder) {
		this.name = builder.name;
		this.timestamp = builder.timestamp == null ? Instant.now() : builder.timestamp;
		this.attributes = new FlowAttributes(builder.attributes);
		this.eventContext = new LinkedHashMap<>(builder.eventContext);
		this.events = new ArrayList<>();
		this.openSteps = new ArrayDeque<>();
	}

	public static Builder builder() {
		return new Builder();
	}

	public String name() {
		return name;
	}

	public Instant timestamp() {
		return timestamp;
	}

	public Instant endTimestamp() {
		return endTimestamp;
	}

	public void markEnd(Instant ts) {
		this.endTimestamp = ts == null ? Instant.now() : ts;
	}

	public FlowAttributes attributes() {
		return attributes;
	}

	public Map<String, Object> eventContext() {
		return eventContext;
	}

	public List<StepEvent> events() {
		return List.copyOf(events);
	}

	public Throwable throwable() {
		return throwable;
	}

	public void setThrowable(Throwable throwable) {
		this.throwable = throwable;
	}

	public Object returnValue() {
		return returnValue;
	}

	public void setReturnValue(Object returnValue) {
		this.returnValue = returnValue;
	}

	public void clearReturnValue() {
		this.returnValue = null;
	}

	public SpanKind kind() {
		return kind;
	}

	public void setKind(SpanKind kind) {
		this.kind = kind == null ? SpanKind.INTERNAL : kind;
	}

	public FlowStatus status() {
		return status;
	}

	public void setStatus(FlowStatus status) {
		this.status = status;
	}

	public String traceId() {
		return traceId;
	}

	public String spanId() {
		return spanId;
	}

	public String parentSpanId() {
		return parentSpanId;
	}

	public void trace(String traceId, String spanId, String parentSpanId) {
		this.traceId = traceId;
		this.spanId = spanId;
		this.parentSpanId = parentSpanId;
	}

	public void beginStepEvent(
			String name, long startEpochNanos, long startNanoTime, FlowAttributes initialAttributes, String kindValue) {
		StepEvent event = new StepEvent(name, startEpochNanos, initialAttributes, kindValue);
		events.add(event);
		openSteps.addLast(new OpenStep(event, startNanoTime));
	}

	public void endStepEvent(long endEpochNanos, long endNanoTime, Map<String, Object> updates) {
		OpenStep openStep = openSteps.pollLast();
		if (openStep == null) {
			return;
		}
		if (updates != null && !updates.isEmpty()) {
			openStep.event.attributes().map().putAll(updates);
		}
		openStep.event.setEndTimeUnixNano(endEpochNanos);
		openStep.event.setElapsedNanos(Math.max(0L, endNanoTime - openStep.startNanoTime()));
	}

	public FlowEvent snapshot() {
		FlowEvent copy = FlowEvent.builder().name(name).timestamp(timestamp).build();
		copy.endTimestamp = endTimestamp;
		copy.attributes().map().putAll(attributes.map());
		copy.eventContext().putAll(eventContext);
		for (StepEvent event : events) {
			copy.events.add(event.snapshot());
		}
		copy.setKind(kind);
		copy.setStatus(status);
		copy.setThrowable(throwable);
		copy.setReturnValue(returnValue);
		copy.trace(traceId, spanId, parentSpanId);
		return copy;
	}

	public static final class Builder {
		private String name;
		private Instant timestamp;
		private final Map<String, Object> attributes = new LinkedHashMap<>();
		private final Map<String, Object> eventContext = new LinkedHashMap<>();

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder timestamp(Instant timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		public Builder putAttribute(String key, Object value) {
			this.attributes.put(key, value);
			return this;
		}

		public Builder putEventContext(String key, Object value) {
			this.eventContext.put(key, value);
			return this;
		}

		public FlowEvent build() {
			return new FlowEvent(this);
		}
	}

	private record OpenStep(StepEvent event, long startNanoTime) {}
}
