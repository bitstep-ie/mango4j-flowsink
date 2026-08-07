package ie.bitstep.mango.instrument.spring.aspect;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.OrphanAlert;
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.PushContextValue;
import ie.bitstep.mango.instrument.annotations.Step;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowMeta;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.model.StepEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowAspectUnitTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	/**
	 * This test demonstrates the intended semantic distinction: a nested flow can complete successfully inside an
	 * already-active parent flow, and the parent context remains active. See FlowAspectStepBehaviorTest for the
	 * converse case where a nested step fails but the root flow recovers and completes.
	 */
	@Test
	void nested_flow_keeps_parent_context_and_records_meta() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		FlowEvent parent = FlowEvent.builder().name("parent").build();
		support.push(parent);
		Method method = Samples.class.getDeclaredMethod("nestedFlow", String.class, int.class);

		Object result = aspect.aroundFlow(joinPoint(method, new Object[] {"alice", 2}, () -> "ok"));

		assertThat(result).isEqualTo("ok");
		assertThat(processor.started).hasSize(1);
		assertThat(processor.completed).hasSize(1);
		assertThat(processor.started.get(0).meta.kind()).isNull();
		assertThat(processor.completed.get(0).meta.statusCode()).isEqualTo("OK");
		assertThat(processor.completed.get(0).meta.statusMessage()).isNull();
		assertThat(processor.completed.get(0).attrs).containsEntry("user.id", "alice");
		assertThat(processor.completed.get(0).context).containsEntry("cart.size", 2);
		assertThat(support.currentContext()).isSameAs(parent);
		assertThat(support.cleanupCalls).isZero();
	}

	@Test
	void started_flow_meta_includes_trace_fields_from_mdc() throws Throwable {
		assertTraceMetaField("traceId", "trace-123");
	}

	@Test
	void started_flow_meta_includes_span_id_from_mdc() throws Throwable {
		assertTraceMetaField("spanId", "span-456");
	}

	@Test
	void started_flow_meta_includes_parent_span_id_from_mdc() throws Throwable {
		assertTraceMetaField("parentSpanId", "parent-789");
	}

	@Test
	void started_flow_meta_includes_tracestate_from_mdc() throws Throwable {
		assertTraceMetaField("tracestate", "state-abc");
	}

	@Test
	void orphan_step_failure_preserves_existing_error_attribute() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		Method method = Samples.class.getDeclaredMethod("orphanWithErrorAttr", String.class);

		assertThatThrownBy(() -> aspect.aroundStep(joinPoint(method, new Object[] {"manual"}, () -> {
					throw new IllegalStateException("boom");
				})))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("boom");

		assertThat(processor.failed).hasSize(1);
		RecordedCall failed = processor.failed.get(0);
		assertThat(failed.attrs).containsEntry("error", "manual");
		assertThat(failed.meta.statusCode()).isEqualTo("ERROR");
		assertThat(failed.meta.statusMessage()).isNull();
		assertThat(support.currentContext()).isNull();
		assertThat(support.cleanupCalls).isEqualTo(1);
		assertThat(support.orphanStepName).isEqualTo("Samples.orphanWithErrorAttr(..)");
		assertThat(support.orphanAlertLevel).isEqualTo(OrphanAlert.Level.NONE);
	}

	@Test
	void in_flow_step_failure_records_internal_kind_and_error_update() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		FlowEvent active = FlowEvent.builder().name("root").build();
		support.push(active);
		Method method = Samples.class.getDeclaredMethod("internalStep", String.class, String.class);

		assertThatThrownBy(() -> aspect.aroundStep(joinPoint(method, new Object[] {"SKU-1", "ie"}, () -> {
					throw new IllegalArgumentException("bad");
				})))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("bad");

		assertThat(active.attributes().map()).containsEntry("sku", "SKU-1");
		assertThat(active.eventContext()).containsEntry("country", "ie");
		assertThat(active.events()).hasSize(1);
		StepEvent event = active.events().get(0);
		assertThat(event.kind()).isEqualTo("INTERNAL");
		assertThat(event.attributes().map())
				.containsEntry("sku", "SKU-1")
				.containsEntry("error", "IllegalArgumentException");
		assertThat(support.currentContext()).isSameAs(active);
	}

	@Test
	void orphan_step_uses_explicit_step_name_when_present() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		Method method = Samples.class.getDeclaredMethod("namedStep");

		Object result = aspect.aroundStep(joinPoint(method, new Object[0], () -> "done"));

		assertThat(result).isEqualTo("done");
		assertThat(processor.started).singleElement().satisfies(call -> assertThat(call.name())
				.isEqualTo("explicit-step"));
		assertThat(processor.completed).singleElement().satisfies(call -> {
			assertThat(call.name()).isEqualTo("explicit-step");
			assertThat(call.meta.statusCode()).isEqualTo("OK");
		});
		assertThat(support.currentContext()).isNull();
		assertThat(support.cleanupCalls).isEqualTo(1);
	}

	@Test
	void root_flow_failure_clears_return_value_and_cleans_up_thread_locals() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support, event -> event.setReturnValue("stale"));
		FlowAspect aspect = new FlowAspect(processor, support);
		Method method = Samples.class.getDeclaredMethod("failingFlow");

		assertThatThrownBy(() -> aspect.aroundFlow(joinPoint(method, new Object[0], () -> {
					throw new IllegalStateException("boom");
				})))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("boom");

		assertThat(processor.failed).hasSize(1);
		assertThat(processor.failedSnapshots).singleElement().satisfies(event -> assertThat(event.returnValue())
				.isNull());
		assertThat(processor.failed).singleElement().satisfies(call -> assertThat(call.attrs())
				.containsEntry("error", "IllegalStateException"));
		assertThat(support.cleanupCalls).isEqualTo(1);
		assertThat(support.currentContext()).isNull();
	}

	@Test
	void void_flow_skips_return_value_and_cleans_up_as_root_flow() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		Method method = Samples.class.getDeclaredMethod("voidFlow");

		Object result = aspect.aroundFlow(joinPoint(method, new Object[0], () -> null));

		assertThat(result).isNull();
		assertThat(processor.started).hasSize(1);
		assertThat(processor.completed).hasSize(1);
		assertThat(support.cleanupCalls).isEqualTo(1);
	}

	@Test
	void null_current_context_is_tolerated_in_both_success_and_failure_paths() throws Throwable {
		FlowProcessorSupport support = new FlowProcessorSupport();
		FlowProcessor nonPushingProcessor = new FlowProcessor() {
			@Override
			public void onFlowStarted(String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {}

			@Override
			public void onFlowCompleted(
					String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {}

			@Override
			public void onFlowFailed(
					String name, Throwable error, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {}
		};
		FlowAspect aspect = new FlowAspect(nonPushingProcessor, support);
		Method method = Samples.class.getDeclaredMethod("nestedFlow", String.class, int.class);

		// success path: support.currentContext() stays null → if (current != null) false
		Object result = aspect.aroundFlow(joinPoint(method, new Object[] {"alice", 1}, () -> "ok"));
		assertThat(result).isEqualTo("ok");

		// failure path: support.currentContext() stays null → if (current != null) false in catch
		assertThatThrownBy(() -> aspect.aroundFlow(joinPoint(method, new Object[] {"bob", 2}, () -> {
					throw new RuntimeException("fail");
				})))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("fail");
	}

	@Test
	void in_flow_step_success_returns_result_and_closes_step_event() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		FlowEvent active = FlowEvent.builder().name("root").build();
		support.push(active);
		Method method = Samples.class.getDeclaredMethod("returningStep");

		Object result = aspect.aroundStep(joinPoint(method, new Object[0], () -> "done"));

		assertThat(result).isEqualTo("done");
		assertThat(active.events()).singleElement().satisfies(event -> {
			assertThat(event.name()).isEqualTo("Samples.returningStep(..)");
			assertThat(event.endTimeUnixNano()).isPositive();
			assertThat(event.elapsedNanos()).isGreaterThanOrEqualTo(0L);
		});
		assertThat(support.cleanupCalls).isZero();
	}

	@Test
	void merges_attributes_into_existing_web_flow_without_lifecycle_calls() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		support.markWebFlow();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		FlowEvent webFlow = FlowEvent.builder().name("checkout.submit").build();
		support.push(webFlow);
		Method method = Samples.class.getDeclaredMethod("nestedFlow", String.class, int.class);

		Object result = aspect.aroundFlow(joinPoint(method, new Object[] {"alice", 3}, () -> "ok"));

		assertThat(result).isEqualTo("ok");
		assertThat(processor.started).isEmpty();
		assertThat(processor.completed).isEmpty();
		assertThat(processor.failed).isEmpty();
		assertThat(support.currentContext()).isSameAs(webFlow);
		assertThat(webFlow.attributes().map()).containsEntry("user.id", "alice");
		assertThat(webFlow.eventContext()).containsEntry("cart.size", 3);
		assertThat(webFlow.returnValue()).isEqualTo("ok");
		assertThat(support.cleanupCalls).isZero();
		assertThat(support.consumeWebFlowMarker()).isFalse();
	}

	@Test
	void web_flow_marker_path_propagates_exception_without_lifecycle_calls() throws Throwable {
		TrackingSupport support = new TrackingSupport();
		support.markWebFlow();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		FlowEvent webFlow = FlowEvent.builder().name("checkout.submit").build();
		webFlow.setReturnValue("prior");
		support.push(webFlow);
		Method method = Samples.class.getDeclaredMethod("nestedFlow", String.class, int.class);

		assertThatThrownBy(() -> aspect.aroundFlow(joinPoint(method, new Object[] {"alice", 3}, () -> {
					throw new IllegalStateException("boom");
				})))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("boom");

		assertThat(processor.started).isEmpty();
		assertThat(processor.failed).isEmpty();
		assertThat(support.currentContext()).isSameAs(webFlow);
		assertThat(webFlow.returnValue()).isNull();
		assertThat(support.cleanupCalls).isZero();
	}

	@Test
	void resolve_flow_and_step_name_fall_back_to_signature_when_annotation_is_absent() throws Exception {
		Method resolveFlowName = FlowAspect.class.getDeclaredMethod("resolveFlowName", ProceedingJoinPoint.class);
		Method resolveStepName = FlowAspect.class.getDeclaredMethod("resolveStepName", ProceedingJoinPoint.class);
		resolveFlowName.setAccessible(true);
		resolveStepName.setAccessible(true);

		// @Step method has no @Flow annotation — resolveFlowName returns the signature
		Method stepMethod = Samples.class.getDeclaredMethod("internalStep", String.class, String.class);
		assertThat(resolveFlowName.invoke(null, joinPoint(stepMethod, new Object[0], () -> null)))
				.isEqualTo("Samples.internalStep(..)");

		// @Flow method has no @Step annotation — resolveStepName returns the signature
		Method flowMethod = Samples.class.getDeclaredMethod("voidFlow");
		assertThat(resolveStepName.invoke(null, joinPoint(flowMethod, new Object[0], () -> null)))
				.isEqualTo("Samples.voidFlow(..)");
	}

	private static void assertTraceMetaField(String key, String value) throws Throwable {
		TrackingSupport support = new TrackingSupport();
		RecordingProcessor processor = new RecordingProcessor(support);
		FlowAspect aspect = new FlowAspect(processor, support);
		Method method = Samples.class.getDeclaredMethod("nestedFlow", String.class, int.class);

		MDC.put(key, value);
		Object result = aspect.aroundFlow(joinPoint(method, new Object[] {"alice", 2}, () -> "ok"));

		assertThat(result).isEqualTo("ok");
		assertThat(processor.started).singleElement().satisfies(call -> {
			assertThat(call.meta.kind()).isNull();
			switch (key) {
				case "traceId" -> {
					assertThat(call.meta.traceId()).isEqualTo(value);
					assertThat(call.meta.spanId()).isNull();
					assertThat(call.meta.parentSpanId()).isNull();
					assertThat(call.meta.tracestate()).isNull();
				}
				case "spanId" -> {
					assertThat(call.meta.traceId()).isNull();
					assertThat(call.meta.spanId()).isEqualTo(value);
					assertThat(call.meta.parentSpanId()).isNull();
					assertThat(call.meta.tracestate()).isNull();
				}
				case "parentSpanId" -> {
					assertThat(call.meta.traceId()).isNull();
					assertThat(call.meta.spanId()).isNull();
					assertThat(call.meta.parentSpanId()).isEqualTo(value);
					assertThat(call.meta.tracestate()).isNull();
				}
				case "tracestate" -> {
					assertThat(call.meta.traceId()).isNull();
					assertThat(call.meta.spanId()).isNull();
					assertThat(call.meta.parentSpanId()).isNull();
					assertThat(call.meta.tracestate()).isEqualTo(value);
				}
				default -> throw new IllegalArgumentException("Unexpected key: " + key);
			}
		});
	}

	private static ProceedingJoinPoint joinPoint(Method method, Object[] args, ProceedCallback callback) {
		MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
				MethodSignature.class.getClassLoader(),
				new Class<?>[] {MethodSignature.class},
				methodSignatureHandler(method));
		return (ProceedingJoinPoint) Proxy.newProxyInstance(
				ProceedingJoinPoint.class.getClassLoader(),
				new Class<?>[] {ProceedingJoinPoint.class},
				joinPointHandler(signature, args, callback));
	}

	private static InvocationHandler methodSignatureHandler(Method method) {
		return (proxy, invoked, args) -> switch (invoked.getName()) {
			case "getMethod" -> method;
			case "getReturnType" -> method.getReturnType();
			case "toShortString" -> method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(..)";
			case "toLongString", "toString" -> method.toString();
			case "getName" -> method.getName();
			case "getDeclaringType" -> method.getDeclaringClass();
			case "getDeclaringTypeName" -> method.getDeclaringClass().getName();
			case "getParameterTypes" -> method.getParameterTypes();
			case "getParameterNames" -> new String[method.getParameterCount()];
			case "getExceptionTypes" -> method.getExceptionTypes();
			case "getModifiers" -> method.getModifiers();
			default -> defaultValue(invoked.getReturnType());
		};
	}

	private static InvocationHandler joinPointHandler(
			MethodSignature signature, Object[] args, ProceedCallback callback) {
		return (proxy, invoked, ignored) -> switch (invoked.getName()) {
			case "getArgs" -> args;
			case "getSignature" -> signature;
			case "toShortString" -> signature.toShortString();
			case "toLongString", "toString" -> signature.toString();
			case "getKind" -> "method-execution";
			case "proceed" -> callback.proceed();
			default -> defaultValue(invoked.getReturnType());
		};
	}

	private static Object defaultValue(Class<?> returnType) {
		if (!returnType.isPrimitive()) {
			return null;
		}
		if (returnType == boolean.class) {
			return false;
		}
		if (returnType == char.class) {
			return '\0';
		}
		return 0;
	}

	@FunctionalInterface
	interface ProceedCallback {
		Object proceed() throws Throwable;
	}

	static class RecordingProcessor implements FlowProcessor {
		private final FlowProcessorSupport support;
		private final java.util.function.Consumer<FlowEvent> startedInitializer;
		private final List<FlowEvent> stack = new ArrayList<>();
		final List<RecordedCall> started = new ArrayList<>();
		final List<RecordedCall> completed = new ArrayList<>();
		final List<RecordedCall> failed = new ArrayList<>();
		final List<FlowEvent> failedSnapshots = new ArrayList<>();

		RecordingProcessor(FlowProcessorSupport support) {
			this(support, event -> {});
		}

		RecordingProcessor(FlowProcessorSupport support, java.util.function.Consumer<FlowEvent> startedInitializer) {
			this.support = support;
			this.startedInitializer = startedInitializer;
		}

		@Override
		public void onFlowStarted(
				String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
			started.add(new RecordedCall(name, extraAttrs, extraContext, meta, null));
			FlowEvent event = FlowEvent.builder().name(name).build();
			event.attributes().map().putAll(extraAttrs);
			event.eventContext().putAll(extraContext);
			startedInitializer.accept(event);
			stack.add(event);
			support.push(event);
		}

		@Override
		public void onFlowCompleted(
				String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
			completed.add(new RecordedCall(name, extraAttrs, extraContext, meta, null));
			FlowEvent current = support.currentContext();
			if (current != null) {
				support.pop(current);
			}
			if (!stack.isEmpty()) {
				stack.remove(stack.size() - 1);
			}
		}

		@Override
		public void onFlowFailed(
				String name,
				Throwable error,
				Map<String, Object> extraAttrs,
				Map<String, Object> extraContext,
				FlowMeta meta) {
			failed.add(new RecordedCall(name, extraAttrs, extraContext, meta, error));
			FlowEvent current = support.currentContext();
			if (current != null) {
				failedSnapshots.add(current.snapshot());
				support.pop(current);
			}
			if (!stack.isEmpty()) {
				stack.remove(stack.size() - 1);
			}
		}
	}

	static class TrackingSupport extends FlowProcessorSupport {
		int cleanupCalls;
		String orphanStepName;
		OrphanAlert.Level orphanAlertLevel;

		@Override
		public void cleanupThreadLocals() {
			cleanupCalls++;
			super.cleanupThreadLocals();
		}

		@Override
		public void logOrphanStep(String stepName, OrphanAlert.Level level) {
			orphanStepName = stepName;
			orphanAlertLevel = level;
		}
	}

	record RecordedCall(
			String name, Map<String, Object> attrs, Map<String, Object> context, FlowMeta meta, Throwable error) {}

	static class Samples {
		@Flow
		String nestedFlow(@PushAttribute("user.id") String userId, @PushContextValue("cart.size") int size) {
			return "ignored";
		}

		@Step
		@OrphanAlert(OrphanAlert.Level.NONE)
		void orphanWithErrorAttr(@PushAttribute("error") String error) {}

		@Step
		void internalStep(@PushAttribute("sku") String sku, @PushContextValue("country") String country) {}

		@Step(name = "explicit-step")
		String namedStep() {
			return "ignored";
		}

		@Flow
		void voidFlow() {}

		@Flow
		String failingFlow() {
			return "ignored";
		}

		@Step
		String returningStep() {
			return "ignored";
		}
	}
}
