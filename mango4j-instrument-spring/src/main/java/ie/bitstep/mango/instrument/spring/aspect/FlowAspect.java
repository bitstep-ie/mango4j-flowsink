package ie.bitstep.mango.instrument.spring.aspect;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.opentelemetry.api.trace.SpanKind;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.Kind;
import ie.bitstep.mango.instrument.annotations.OrphanAlert;
import ie.bitstep.mango.instrument.annotations.Step;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowMeta;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.model.FlowAttributes;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.processor.AttributeParamExtractor;
import ie.bitstep.mango.instrument.spring.processor.AttributeParamExtractor.AttrCtx;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class FlowAspect {
	private static final String ERROR = "error";
	private final FlowProcessor processor;
	private final FlowProcessorSupport support;

	public FlowAspect(FlowProcessor processor, FlowProcessorSupport support) {
		this.processor = processor;
		this.support = support;
	}

	@Around("@annotation(ie.bitstep.mango.instrument.annotations.Flow) && execution(* *(..))")
	public Object aroundFlow(ProceedingJoinPoint joinPoint) throws Throwable {
		if (support.consumeWebFlowMarker()) {
			return runWithinInterceptorFlow(joinPoint);
		}
		String name = resolveFlowName(joinPoint);
		AttrCtx attrCtx = AttributeParamExtractor.extract(joinPoint);
		FlowMeta meta = buildMeta(joinPoint, null);
		boolean returnsVoid = ((MethodSignature) joinPoint.getSignature()).getReturnType() == Void.TYPE;
		boolean rootFlow = support.currentContext() == null;

		processor.onFlowStarted(name, attrCtx.attributes(), attrCtx.context(), meta);
		try {
			Object result = joinPoint.proceed();
			if (!returnsVoid) {
				FlowEvent current = support.currentContext();
				if (current != null) {
					current.setReturnValue(result);
				}
			}
			processor.onFlowCompleted(
					name, attrCtx.attributes(), attrCtx.context(), buildMeta(joinPoint, new StatusHint("OK", null)));
			return result;
		} catch (Throwable throwable) {
			FlowEvent current = support.currentContext();
			if (current != null) {
				current.clearReturnValue();
			}
			Map<String, Object> attrs = new LinkedHashMap<>(attrCtx.attributes());
			attrs.put(ERROR, throwable.getClass().getSimpleName());
			processor.onFlowFailed(
					name, throwable, attrs, attrCtx.context(), buildMeta(joinPoint, new StatusHint("ERROR", null)));
			throw throwable;
		} finally {
			if (rootFlow) {
				support.cleanupThreadLocals();
			}
		}
	}

	@Around("@annotation(ie.bitstep.mango.instrument.annotations.Step) && execution(* *(..))")
	public Object aroundStep(ProceedingJoinPoint joinPoint) throws Throwable {
		String name = resolveStepName(joinPoint);
		AttrCtx attrCtx = AttributeParamExtractor.extract(joinPoint);
		FlowEvent context = support.currentContext();
		if (context == null) {
			return handleOrphanStep(joinPoint, name, attrCtx);
		}
		return handleInFlowStep(joinPoint, name, attrCtx, context);
	}

	private Object runWithinInterceptorFlow(ProceedingJoinPoint joinPoint) throws Throwable {
		AttrCtx attrCtx = AttributeParamExtractor.extract(joinPoint);
		boolean returnsVoid = ((MethodSignature) joinPoint.getSignature()).getReturnType() == Void.TYPE;
		FlowEvent context = support.currentContext();
		if (context != null) {
			context.attributes().map().putAll(attrCtx.attributes());
			context.eventContext().putAll(attrCtx.context());
		}
		try {
			Object result = joinPoint.proceed();
			if (!returnsVoid && context != null) {
				context.setReturnValue(result);
			}
			return result;
		} catch (Throwable throwable) {
			if (context != null) {
				context.clearReturnValue();
			}
			throw throwable;
		}
	}

	private Object handleOrphanStep(ProceedingJoinPoint joinPoint, String name, AttrCtx attrCtx) throws Throwable {
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
		OrphanAlert orphanAlert = method.getAnnotation(OrphanAlert.class);
		support.logOrphanStep(name, orphanAlert != null ? orphanAlert.value() : OrphanAlert.Level.ERROR);

		try {
			processor.onFlowStarted(name, attrCtx.attributes(), attrCtx.context(), buildMeta(joinPoint, null));
			Object result = joinPoint.proceed();
			processor.onFlowCompleted(
					name, attrCtx.attributes(), attrCtx.context(), buildMeta(joinPoint, new StatusHint("OK", null)));
			return result;
		} catch (Throwable throwable) {
			Map<String, Object> attrs = new LinkedHashMap<>(attrCtx.attributes());
			attrs.putIfAbsent(ERROR, throwable.getClass().getSimpleName());
			processor.onFlowFailed(
					name, throwable, attrs, attrCtx.context(), buildMeta(joinPoint, new StatusHint("ERROR", null)));
			throw throwable;
		} finally {
			support.cleanupThreadLocals();
		}
	}

	private Object handleInFlowStep(ProceedingJoinPoint joinPoint, String name, AttrCtx attrCtx, FlowEvent context)
			throws Throwable {
		context.attributes().map().putAll(attrCtx.attributes());
		context.eventContext().putAll(attrCtx.context());
		long startNano = System.nanoTime();
		long startEpochNanos = support.unixNanos(Instant.now());
		context.beginStepEvent(
				name,
				startEpochNanos,
				startNano,
				new FlowAttributes(new LinkedHashMap<>(attrCtx.attributes())),
				extractStepKind(joinPoint));
		try {
			Object result = joinPoint.proceed();
			context.endStepEvent(support.unixNanos(Instant.now()), System.nanoTime(), null);
			return result;
		} catch (Throwable throwable) {
			Map<String, Object> updates = new LinkedHashMap<>();
			updates.put(ERROR, throwable.getClass().getSimpleName());
			context.endStepEvent(support.unixNanos(Instant.now()), System.nanoTime(), updates);
			throw throwable;
		}
	}

	private static String resolveFlowName(ProceedingJoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		Flow annotation = AnnotatedElementUtils.findMergedAnnotation(method, Flow.class);
		if (annotation != null) {
			if (!annotation.name().isBlank()) {
				return annotation.name();
			}
			if (!annotation.value().isBlank()) {
				return annotation.value();
			}
		}
		return signature.toShortString();
	}

	private static String resolveStepName(ProceedingJoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		Step annotation = AnnotatedElementUtils.findMergedAnnotation(method, Step.class);
		if (annotation != null) {
			if (!annotation.name().isBlank()) {
				return annotation.name();
			}
			if (!annotation.value().isBlank()) {
				return annotation.value();
			}
		}
		return signature.toShortString();
	}

	private static String extractStepKind(ProceedingJoinPoint joinPoint) {
		Kind annotation = AnnotatedElementUtils.findMergedAnnotation(
				((MethodSignature) joinPoint.getSignature()).getMethod(), Kind.class);
		return annotation != null ? annotation.value().name() : SpanKind.INTERNAL.name();
	}

	private static FlowMeta buildMeta(ProceedingJoinPoint joinPoint, StatusHint statusHint) {
		Kind annotation = AnnotatedElementUtils.findMergedAnnotation(
				((MethodSignature) joinPoint.getSignature()).getMethod(), Kind.class);
		FlowMeta.Builder builder = FlowMeta.builder();
		if (annotation != null) {
			builder.kind(annotation.value().name());
		}
		applyTraceMeta(builder);
		if (statusHint != null) {
			builder.status(statusHint.code(), statusHint.message());
		}
		return builder.build();
	}

	private static void applyTraceMeta(FlowMeta.Builder builder) {
		String traceId = MDC.get("traceId");
		String spanId = MDC.get("spanId");
		String parentSpanId = MDC.get("parentSpanId");
		String tracestate = MDC.get("tracestate");
		if (traceId != null || spanId != null || parentSpanId != null || tracestate != null) {
			builder.trace(traceId, spanId, parentSpanId, tracestate);
		}
	}

	private record StatusHint(String code, String message) {}
}
