package ie.bitstep.mango.instrument.core.processor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.dispatch.AsyncDispatchBus;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.model.FlowStatus;
import ie.bitstep.mango.instrument.validation.FlowAttributeValidator;

public class DefaultFlowProcessor implements FlowProcessor {
	public static final String LIFECYCLE = "lifecycle";
	public static final String STARTED = "STARTED";
	public static final String COMPLETED = "COMPLETED";
	public static final String FAILED = "FAILED";

	private final AsyncDispatchBus asyncBus;
	private final FlowProcessorSupport support;
	private final FlowAttributeValidator validator;

	public DefaultFlowProcessor(AsyncDispatchBus asyncBus, FlowProcessorSupport support) {
		this(asyncBus, support, null);
	}

	public DefaultFlowProcessor(
			AsyncDispatchBus asyncBus, FlowProcessorSupport support, FlowAttributeValidator validator) {
		this.asyncBus = asyncBus;
		this.support = Objects.requireNonNull(support, "support");
		this.validator = validator;
	}

	@Override
	public void onFlowStarted(
			String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
		if (!support.isEnabled()) {
			return;
		}
		Map<String, Object> attrs = copy(extraAttrs);
		Map<String, Object> ctx = copy(extraContext);
		validate(attrs, ctx);
		FlowEvent event =
				FlowEvent.builder().name(name).timestamp(Instant.now()).build();
		event.attributes().map().putAll(attrs);
		event.eventContext().putAll(ctx);
		event.eventContext().put(LIFECYCLE, STARTED);
		applyMeta(event, meta);
		support.push(event);
		support.startNewBatch();
		if (asyncBus != null) {
			asyncBus.dispatch(event);
		}
	}

	@Override
	public void onFlowCompleted(
			String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
		if (!support.isEnabled()) {
			return;
		}
		Map<String, Object> attrs = copy(extraAttrs);
		Map<String, Object> ctx = copy(extraContext);
		validate(attrs, ctx);

		FlowEvent event = support.currentContext();
		if (event == null) {
			return;
		}
		event.markEnd(Instant.now());
		event.attributes().map().putAll(attrs);
		event.eventContext().putAll(ctx);
		event.eventContext().put(LIFECYCLE, COMPLETED);
		applyCompletionMeta(event, meta);
		support.clearBatchAfterDispatch();
		support.pop(event);
		if (asyncBus != null) {
			asyncBus.dispatch(event);
		}
	}

	@Override
	public void onFlowFailed(
			String name,
			Throwable error,
			Map<String, Object> extraAttrs,
			Map<String, Object> extraContext,
			FlowMeta meta) {
		if (!support.isEnabled()) {
			return;
		}
		Map<String, Object> attrs = copy(extraAttrs);
		if (error != null) {
			attrs.putIfAbsent("error", error.getClass().getSimpleName());
		}
		Map<String, Object> ctx = copy(extraContext);
		validate(attrs, ctx);

		FlowEvent event = support.currentContext();
		if (event == null) {
			return;
		}
		event.markEnd(Instant.now());
		event.attributes().map().putAll(attrs);
		event.eventContext().putAll(ctx);
		event.eventContext().put(LIFECYCLE, FAILED);
		event.setThrowable(error);
		applyCompletionMeta(event, meta);
		support.clearBatchAfterDispatch();
		support.pop(event);
		if (asyncBus != null) {
			asyncBus.dispatch(event);
		}
	}

	private void validate(Map<String, Object> attrs, Map<String, Object> ctx) {
		if (validator == null) {
			return;
		}
		validator.validateMap(attrs, "attributes");
		validator.validateMap(ctx, "context");
	}

	private static Map<String, Object> copy(Map<String, Object> source) {
		return new LinkedHashMap<>(source == null ? Map.of() : source);
	}

	void applyMeta(FlowEvent event, FlowMeta meta) {
		if (event == null || meta == null) {
			return;
		}
		if (meta.kind() != null) {
			event.setKind(resolveKind(meta.kind()));
		}
		if (meta.traceId() != null || meta.spanId() != null || meta.parentSpanId() != null) {
			event.trace(meta.traceId(), meta.spanId(), meta.parentSpanId());
		}
		if (meta.tracestate() != null && !meta.tracestate().isBlank()) {
			event.attributes().put("trace.tracestate", meta.tracestate());
		}
	}

	void applyCompletionMeta(FlowEvent event, FlowMeta meta) {
		if (event == null || meta == null || meta.statusCode() == null) {
			applyMeta(event, meta);
			return;
		}
		applyMeta(event, meta);
		StatusCode code;
		try {
			code = StatusCode.valueOf(meta.statusCode());
		} catch (IllegalArgumentException ex) {
			code = StatusCode.UNSET;
		}
		event.setStatus(new FlowStatus(code, meta.statusMessage()));
	}

	static SpanKind resolveKind(String value) {
		try {
			return SpanKind.valueOf(value);
		} catch (IllegalArgumentException ex) {
			return SpanKind.INTERNAL;
		}
	}
}
