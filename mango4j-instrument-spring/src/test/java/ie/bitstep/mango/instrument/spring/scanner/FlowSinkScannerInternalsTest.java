package ie.bitstep.mango.instrument.spring.scanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnAllLifecycles;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycle;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowScopes;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.annotations.PullAllContextValues;
import ie.bitstep.mango.instrument.annotations.PullAttribute;
import ie.bitstep.mango.instrument.annotations.PullContextValue;
import ie.bitstep.mango.instrument.model.FlowEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowSinkScannerInternalsTest {

	private static Class<?> compiledHandlerClass() {
		return Arrays.stream(FlowSinkScanner.class.getDeclaredClasses())
				.filter(c -> c.getSimpleName().equals("CompiledHandler"))
				.findFirst()
				.orElseThrow();
	}

	@Test
	void helper_methods_cover_matching_and_coercion_edges() throws Exception {
		Class<?> handlerClass = compiledHandlerClass();
		Method scopeMatches = handlerClass.getDeclaredMethod("scopeMatches", List.class, String.class);
		Method attributesPresent = handlerClass.getDeclaredMethod("attributesPresent", FlowEvent.class, String[].class);
		Method contextPresent = handlerClass.getDeclaredMethod("contextPresent", FlowEvent.class, String[].class);
		Method chooseThrowable =
				FlowSinkScanner.class.getDeclaredMethod("chooseThrowable", FlowEvent.class, boolean.class);
		Method coerce = FlowSinkScanner.class.getDeclaredMethod("coerce", Object.class, Class.class);
		Method nullToEmpty = FlowSinkScanner.class.getDeclaredMethod("nullToEmpty", String.class);
		Method extractScopes = FlowSinkScanner.class.getDeclaredMethod("extractScopes", AnnotatedElement.class);
		Method extractLifecycles = FlowSinkScanner.class.getDeclaredMethod("extractLifecycles", AnnotatedElement.class);
		Method buildParamBinding = FlowSinkScanner.class.getDeclaredMethod(
				"buildParamBinding", Parameter.class, Annotation[].class, boolean.class);

		scopeMatches.setAccessible(true);
		attributesPresent.setAccessible(true);
		contextPresent.setAccessible(true);
		chooseThrowable.setAccessible(true);
		coerce.setAccessible(true);
		nullToEmpty.setAccessible(true);
		extractScopes.setAccessible(true);
		extractLifecycles.setAccessible(true);
		buildParamBinding.setAccessible(true);

		FlowEvent event = FlowEvent.builder().name("orders.checkout.pay").build();
		event.attributes().put("order.id", 123);
		event.attributes().put("sku", "SKU-1");
		event.eventContext().put("tenant.id", "bitstep");
		Throwable root = new IllegalArgumentException("root");
		event.setThrowable(new RuntimeException("wrap", root));
		Method annotated = Samples.class.getDeclaredMethod("annotated", String.class, Throwable.class, Throwable.class);
		Method flowEventMethod = Samples.class.getDeclaredMethod("flowEvent", FlowEvent.class);
		Method allAttrsMethod = Samples.class.getDeclaredMethod("allAttributes", Map.class);
		Method allContextMethod = Samples.class.getDeclaredMethod("allContext", Map.class);
		Method contextValueMethod = Samples.class.getDeclaredMethod("contextValue", String.class);
		Method noBindingMethod = Samples.class.getDeclaredMethod("noBinding", String.class);
		Parameter attrParam = annotated.getParameters()[0];
		Parameter throwableParam = annotated.getParameters()[1];
		Parameter rootThrowableParam = annotated.getParameters()[2];
		Parameter flowEventParam = flowEventMethod.getParameters()[0];
		Parameter allAttrsParam = allAttrsMethod.getParameters()[0];
		Parameter allContextParam = allContextMethod.getParameters()[0];
		Parameter contextValueParam = contextValueMethod.getParameters()[0];
		Parameter noBindingParam = noBindingMethod.getParameters()[0];
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> attrBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, attrParam, attrParam.getAnnotations(), false);
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> throwableBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, throwableParam, throwableParam.getAnnotations(), false);
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> rootThrowableBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, rootThrowableParam, rootThrowableParam.getAnnotations(), true);
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> flowEventBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, flowEventParam, flowEventParam.getAnnotations(), false);
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> allAttrsBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, allAttrsParam, allAttrsParam.getAnnotations(), false);
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> allContextBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, allContextParam, allContextParam.getAnnotations(), false);
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> contextValueBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, contextValueParam, contextValueParam.getAnnotations(), false);
		@SuppressWarnings("unchecked")
		Function<FlowEvent, Object> noBinding = (Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, noBindingParam, noBindingParam.getAnnotations(), false);
		@SuppressWarnings("unchecked")
		List<String> scopes = (List<String>) extractScopes.invoke(null, ScopedType.class);
		@SuppressWarnings("unchecked")
		Set<OnFlowLifecycle.Lifecycle> lifecycles =
				(Set<OnFlowLifecycle.Lifecycle>) extractLifecycles.invoke(null, LifecycleType.class);
		Method directLifecycleMethod = DirectLifecycleType.class.getDeclaredMethod("started");
		@SuppressWarnings("unchecked")
		Set<OnFlowLifecycle.Lifecycle> directLifecycles =
				(Set<OnFlowLifecycle.Lifecycle>) extractLifecycles.invoke(null, directLifecycleMethod);

		assertThat(scopeMatches.invoke(null, List.of("orders.checkout"), "orders.checkout.pay"))
				.isEqualTo(true);
		assertThat(scopeMatches.invoke(null, Arrays.asList(null, "orders.checkout."), "orders.checkout.pay"))
				.isEqualTo(true);
		assertThat(scopeMatches.invoke(null, List.of(), "orders.checkout.pay")).isEqualTo(true);
		assertThat(scopeMatches.invoke(null, List.of("orders.checkout"), "orders.refund"))
				.isEqualTo(false);
		assertThat(scopeMatches.invoke(null, List.of("orders.checkout"), null)).isEqualTo(false);
		assertThat(attributesPresent.invoke(null, event, (Object) new String[] {null}))
				.isEqualTo(false);
		assertThat(attributesPresent.invoke(null, event, (Object) null)).isEqualTo(true);
		assertThat(contextPresent.invoke(null, event, (Object) new String[] {" "}))
				.isEqualTo(false);
		assertThat(contextPresent.invoke(null, event, (Object) null)).isEqualTo(true);
		assertThat(chooseThrowable.invoke(null, event, true)).isSameAs(root);
		assertThat(chooseThrowable.invoke(null, event, false)).isSameAs(event.throwable());
		assertThat(chooseThrowable.invoke(
						null, FlowEvent.builder().name("empty").build(), true))
				.isNull();
		assertThat(coerce.invoke(null, 123, String.class)).isEqualTo("123");
		assertThat(coerce.invoke(null, 123, Long.class)).isEqualTo(123);
		assertThat(coerce.invoke(null, null, String.class)).isNull();
		assertThat(nullToEmpty.invoke(null, new Object[] {null})).isEqualTo("");
		assertThat(scopes).containsExactly("", "orders.");
		assertThat(lifecycles)
				.containsExactlyInAnyOrder(
						OnFlowLifecycle.Lifecycle.STARTED,
						OnFlowLifecycle.Lifecycle.COMPLETED,
						OnFlowLifecycle.Lifecycle.FAILED);
		assertThat(directLifecycles).containsExactly(OnFlowLifecycle.Lifecycle.STARTED);
		assertThat(attrBinding.apply(event)).isEqualTo("SKU-1");
		assertThat(throwableBinding).isNull();
		assertThat(rootThrowableBinding.apply(event)).isSameAs(root);
		assertThat(flowEventBinding.apply(event)).isSameAs(event);
		assertThat(allAttrsBinding.apply(event)).isEqualTo(event.attributes().map());
		assertThat(allContextBinding.apply(event)).isEqualTo(event.eventContext());
		assertThat(contextValueBinding.apply(event)).isEqualTo("bitstep");
		assertThat(noBinding).isNull();
	}

	@Test
	void compile_handler_covers_direct_lifecycle_and_binding_variants() throws Exception {
		FlowSinkScanner scanner = new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
		Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
				"compileHandler", Object.class, Method.class, List.class, Set.class);
		compileHandler.setAccessible(true);

		SampleSink sink = new SampleSink();
		Method lifecycleMethod = SampleSink.class.getDeclaredMethod(
				"onFailedLifecycle", Throwable.class, String.class, java.util.Map.class, java.util.Map.class);
		Object compiled = compileHandler.invoke(
				scanner,
				sink,
				lifecycleMethod,
				List.of("orders"),
				Set.of(OnFlowLifecycle.Lifecycle.FAILED, OnFlowLifecycle.Lifecycle.COMPLETED));

		assertThat(compiled).isNotNull();

		Method matches = compiled.getClass().getDeclaredMethod("matches", FlowEvent.class);
		Method invoke = compiled.getClass().getDeclaredMethod("invoke", FlowEvent.class);
		matches.setAccessible(true);
		invoke.setAccessible(true);

		FlowEvent failed = FlowEvent.builder().name("orders.checkout").build();
		failed.eventContext().put("lifecycle", "FAILED");
		failed.eventContext().put("trace.id", "trace-1");
		failed.attributes().put("sku", "SKU-1");
		IllegalArgumentException root = new IllegalArgumentException("root");
		failed.setThrowable(new RuntimeException("wrap", root));

		assertThat(matches.invoke(compiled, failed)).isEqualTo(true);
		invoke.invoke(compiled, failed);

		assertThat(sink.throwable).isSameAs(root);
		assertThat(sink.contextValue).isEqualTo("trace-1");
		assertThat(sink.attributes).containsEntry("sku", "SKU-1");
		assertThat(sink.context).containsEntry("lifecycle", "FAILED");

		FlowEvent completed = FlowEvent.builder().name("orders.checkout").build();
		completed.eventContext().put("lifecycle", "COMPLETED");
		completed.eventContext().put("trace.id", "trace-2");
		assertThat(matches.invoke(compiled, completed)).isEqualTo(false);

		Method noLifecycleMethod = SampleSink.class.getDeclaredMethod("withoutLifecycle", String.class);
		assertThat(compileHandler.invoke(scanner, sink, noLifecycleMethod, List.of(), Set.of()))
				.isNull();

		Method invalidBindingMethod = SampleSink.class.getDeclaredMethod("invalidBinding", Integer.class);
		assertThatThrownBy(() -> compileHandler.invoke(scanner, sink, invalidBindingMethod, List.of(), Set.of()))
				.cause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cannot be bound");

		Object mismatchedProxy = Proxy.newProxyInstance(
				Runnable.class.getClassLoader(), new Class<?>[] {Runnable.class}, (proxy, method, args) -> null);
		Method startedHandler = SampleSink.class.getDeclaredMethod("startedHandler");
		assertThat(compileHandler.invoke(scanner, mismatchedProxy, startedHandler, List.of(), Set.of()))
				.isNull();
	}

	@Test
	void compile_handler_applies_class_lifecycle_filter_and_matches_fail_when_requirements_are_missing()
			throws Exception {
		FlowSinkScanner scanner = new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
		Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
				"compileHandler", Object.class, Method.class, List.class, Set.class);
		compileHandler.setAccessible(true);

		SampleSink sink = new SampleSink();
		Method startedMethod = SampleSink.class.getDeclaredMethod("startedWithRequiredContext", String.class);
		Object filtered = compileHandler.invoke(
				scanner, sink, startedMethod, List.of("orders"), Set.of(OnFlowLifecycle.Lifecycle.FAILED));

		assertThat(filtered).isNotNull();
		Method matches = filtered.getClass().getDeclaredMethod("matches", FlowEvent.class);
		matches.setAccessible(true);

		FlowEvent started = FlowEvent.builder().name("orders.checkout").build();
		started.eventContext().put("lifecycle", "STARTED");
		started.eventContext().put("trace.id", "trace-1");
		assertThat(matches.invoke(filtered, started)).isEqualTo(false);

		Object allowed = compileHandler.invoke(
				scanner, sink, startedMethod, List.of("orders"), Set.of(OnFlowLifecycle.Lifecycle.STARTED));
		Method allowedMatches = allowed.getClass().getDeclaredMethod("matches", FlowEvent.class);
		allowedMatches.setAccessible(true);
		FlowEvent missingContext = FlowEvent.builder().name("orders.checkout").build();
		missingContext.eventContext().put("lifecycle", "STARTED");
		assertThat(allowedMatches.invoke(allowed, missingContext)).isEqualTo(false);

		missingContext.eventContext().put("trace.id", "trace-2");
		assertThat(allowedMatches.invoke(allowed, missingContext)).isEqualTo(true);
	}

	@Test
	void compile_handler_returns_null_without_lifecycle_even_when_binding_would_otherwise_work() throws Exception {
		FlowSinkScanner scanner = new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
		Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
				"compileHandler", Object.class, Method.class, List.class, Set.class);
		compileHandler.setAccessible(true);

		SampleSink sink = new SampleSink();
		Method method = SampleSink.class.getDeclaredMethod("withoutLifecycle", String.class);

		assertThat(compileHandler.invoke(scanner, sink, method, List.of(), Set.of()))
				.isNull();
	}

	@Test
	void compile_handler_with_direct_lifecycle_non_failed_covers_allow_throwable_false_branch() throws Exception {
		FlowSinkScanner scanner = new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
		Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
				"compileHandler", Object.class, Method.class, List.class, Set.class);
		compileHandler.setAccessible(true);

		SampleSink sink = new SampleSink();
		// @OnFlowLifecycle(STARTED): lifecycle != null but lifecycle.value() != FAILED → allowThrowable = false
		Method directStarted = SampleSink.class.getDeclaredMethod("directStartedLifecycle");
		Object compiled = compileHandler.invoke(scanner, sink, directStarted, List.of(), Set.of());
		assertThat(compiled).isNotNull();

		Method matches = compiled.getClass().getDeclaredMethod("matches", FlowEvent.class);
		matches.setAccessible(true);
		FlowEvent started = FlowEvent.builder().name("demo").build();
		started.eventContext().put("lifecycle", "STARTED");
		assertThat(matches.invoke(compiled, started)).isEqualTo(true);
	}

	@Test
	void compile_handler_logs_proxy_mismatch_at_debug_level() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(FlowSinkScanner.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			FlowSinkScanner scanner =
					new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
			Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
					"compileHandler", Object.class, Method.class, List.class, Set.class);
			compileHandler.setAccessible(true);

			Object mismatchedProxy = Proxy.newProxyInstance(
					Runnable.class.getClassLoader(), new Class<?>[] {Runnable.class}, (proxy, method, args) -> null);
			Method startedHandler = SampleSink.class.getDeclaredMethod("startedHandler");

			assertThat(compileHandler.invoke(scanner, mismatchedProxy, startedHandler, List.of(), Set.of()))
					.isNull();
			assertThat(appender.list)
					.anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("Skipping FlowSink method"))
					.anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("proxy mismatch"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void compile_handler_supports_failure_only_methods_and_rejects_completed_events() throws Exception {
		FlowSinkScanner scanner = new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
		Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
				"compileHandler", Object.class, Method.class, List.class, Set.class);
		compileHandler.setAccessible(true);

		SampleSink sink = new SampleSink();
		Method failureOnlyMethod = SampleSink.class.getDeclaredMethod("failureOnly", Throwable.class);
		Object compiled = compileHandler.invoke(scanner, sink, failureOnlyMethod, List.of("orders"), Set.of());
		assertThat(compiled).isNotNull();

		Method matches = compiled.getClass().getDeclaredMethod("matches", FlowEvent.class);
		Method invoke = compiled.getClass().getDeclaredMethod("invoke", FlowEvent.class);
		matches.setAccessible(true);
		invoke.setAccessible(true);

		FlowEvent failed = FlowEvent.builder().name("orders.checkout").build();
		failed.eventContext().put("lifecycle", "FAILED");
		RuntimeException failure = new RuntimeException("boom");
		failed.setThrowable(failure);
		assertThat(matches.invoke(compiled, failed)).isEqualTo(true);
		invoke.invoke(compiled, failed);
		assertThat(sink.failureOnlyThrowable).isSameAs(failure);

		FlowEvent completed = FlowEvent.builder().name("orders.checkout").build();
		completed.eventContext().put("lifecycle", "COMPLETED");
		assertThat(matches.invoke(compiled, completed)).isEqualTo(false);
	}

	@Test
	void helper_methods_cover_edge_cases_for_attributes_context_and_scope() throws Exception {
		Class<?> handlerClass = compiledHandlerClass();
		Method scopeMatches = handlerClass.getDeclaredMethod("scopeMatches", List.class, String.class);
		Method attributesPresent = handlerClass.getDeclaredMethod("attributesPresent", FlowEvent.class, String[].class);
		Method contextPresent = handlerClass.getDeclaredMethod("contextPresent", FlowEvent.class, String[].class);
		scopeMatches.setAccessible(true);
		attributesPresent.setAccessible(true);
		contextPresent.setAccessible(true);

		FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
		event.attributes().put("order.id", "123");
		event.eventContext().put("tenant.id", "bitstep");

		// scopeMatches: null scopes → always true
		assertThat(scopeMatches.invoke(null, null, "orders.checkout")).isEqualTo(true);
		// scopeMatches: scope list with empty-string entry → matches anything (trimmed.isEmpty() = true)
		assertThat(scopeMatches.invoke(null, java.util.Arrays.asList(""), "orders.checkout"))
				.isEqualTo(true);
		// scopeMatches: exact match (candidate.equals(trimmed) = true)
		assertThat(scopeMatches.invoke(null, java.util.Arrays.asList("orders.checkout"), "orders.checkout"))
				.isEqualTo(true);
		// scopeMatches: list containing only null → false (null scope entry returns false)
		assertThat(scopeMatches.invoke(null, java.util.Arrays.asList((String) null), "orders.checkout"))
				.isEqualTo(false);

		// attributesPresent: empty required array → true
		assertThat(attributesPresent.invoke(null, event, (Object) new String[0]))
				.isEqualTo(true);
		// attributesPresent: blank key in required → false
		assertThat(attributesPresent.invoke(null, event, (Object) new String[] {" "}))
				.isEqualTo(false);

		// contextPresent: empty required array → true
		assertThat(contextPresent.invoke(null, event, (Object) new String[0])).isEqualTo(true);
		// contextPresent: null key in required → false
		assertThat(contextPresent.invoke(null, event, (Object) new String[] {null}))
				.isEqualTo(false);
	}

	@Test
	void build_param_binding_handles_non_root_flow_exception_and_non_flow_exception_annotations() throws Exception {
		Method buildParamBinding = FlowSinkScanner.class.getDeclaredMethod(
				"buildParamBinding", java.lang.reflect.Parameter.class, Annotation[].class, boolean.class);
		buildParamBinding.setAccessible(true);

		// Method with a Throwable param annotated with @FlowException(CAUSE) and another non-FlowException annotation
		Method withWrappedAndOther =
				FlowExceptionSamples.class.getDeclaredMethod("wrappedAndOtherAnnotation", Throwable.class);
		java.lang.reflect.Parameter param = withWrappedAndOther.getParameters()[0];

		// allowThrowable=true, @FlowException(CAUSE) → root=false (not ROOT), @Deprecated is not FlowException
		@SuppressWarnings("unchecked")
		java.util.function.Function<FlowEvent, Object> binding = (java.util.function.Function<FlowEvent, Object>)
				buildParamBinding.invoke(null, param, param.getAnnotations(), true);
		assertThat(binding).isNotNull();
		FlowEvent event = FlowEvent.builder().name("demo").build();
		event.setThrowable(new RuntimeException("outer", new IllegalArgumentException("cause")));
		// root=false → chooseThrowable returns outer, not root cause
		assertThat(binding.apply(event)).isSameAs(event.throwable());
	}

	@Test
	void post_process_logs_registration_for_real_sink() {
		Logger logger = (Logger) LoggerFactory.getLogger(FlowSinkScanner.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			FlowSinkScanner scanner =
					new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
			scanner.postProcessAfterInitialization(new LoggingSink(), "loggingSink");

			assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
					.contains("Registered FlowSink: LoggingSink"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@OnFlowScopes({@OnFlowScope(""), @OnFlowScope("orders.")})
	static class ScopedType {}

	@OnAllLifecycles
	static class LifecycleType {}

	static class DirectLifecycleType {
		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)
		void started() {}
	}

	static class Samples {
		void annotated(
				@PullAttribute("sku") String sku,
				Throwable ignored,
				@FlowException(FlowException.Source.ROOT_CAUSE) Throwable root) {}

		void flowEvent(FlowEvent event) {}

		void allAttributes(@PullAllAttributes Map<String, Object> attributes) {}

		void allContext(@PullAllContextValues Map<String, Object> context) {}

		void contextValue(@PullContextValue("tenant.id") String tenantId) {}

		void noBinding(@Deprecated String ignored) {}
	}

	static class SampleSink {
		Throwable failureOnlyThrowable;
		Throwable throwable;
		String contextValue;
		java.util.Map<String, Object> attributes;
		java.util.Map<String, Object> context;

		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.FAILED)
		void onFailedLifecycle(
				@FlowException(FlowException.Source.ROOT_CAUSE) Throwable throwable,
				@PullContextValue("trace.id") String traceId,
				@PullAllAttributes java.util.Map<String, Object> attributes,
				@PullAllContextValues java.util.Map<String, Object> context) {
			this.throwable = throwable;
			this.contextValue = traceId;
			this.attributes = attributes;
			this.context = context;
		}

		void withoutLifecycle(@PullAttribute("sku") String sku) {}

		@OnFlowCompleted
		void invalidBinding(Integer count) {}

		@ie.bitstep.mango.instrument.annotations.OnFlowFailure
		void failureOnly(Throwable throwable) {
			this.failureOnlyThrowable = throwable;
		}

		@OnFlowStarted
		void startedHandler() {}

		@OnFlowStarted
		@ie.bitstep.mango.instrument.annotations.RequiredEventContext("trace.id")
		void startedWithRequiredContext(@PullContextValue("trace.id") String traceId) {
			this.contextValue = traceId;
		}

		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)
		void directStartedLifecycle() {}
	}

	@Test
	void compile_handler_throws_for_unresolvable_parameter() throws Exception {
		FlowSinkScanner scanner = new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
		Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
				"compileHandler", Object.class, Method.class, List.class, Set.class);
		compileHandler.setAccessible(true);

		SampleSink sink = new SampleSink();
		Method invalidBindingMethod = SampleSink.class.getDeclaredMethod("invalidBinding", Integer.class);

		assertThatThrownBy(() -> compileHandler.invoke(scanner, sink, invalidBindingMethod, List.of(), Set.of()))
				.cause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cannot be bound")
				.hasMessageContaining("invalidBinding");
	}

	@Test
	void post_process_logs_warn_for_no_handler_sink() {
		Logger logger = (Logger) LoggerFactory.getLogger(FlowSinkScanner.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry localRegistry =
					new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry();
			FlowSinkScanner scanner = new FlowSinkScanner(localRegistry);
			scanner.postProcessAfterInitialization(new NoHandlerSink(), "noHandlerSink");

			assertThat(localRegistry.handlers()).isEmpty();
			assertThat(appender.list)
					.anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("no invocable handler"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@ie.bitstep.mango.instrument.spring.annotations.FlowSink
	static class LoggingSink {
		@OnFlowStarted
		void onStart() {}
	}

	@ie.bitstep.mango.instrument.spring.annotations.FlowSink
	static class NoHandlerSink {
		public void helperOnly() {}
	}

	static class FlowExceptionSamples {
		void wrappedAndOtherAnnotation(@FlowException(FlowException.Source.THROWN) @Deprecated Throwable throwable) {}
	}
}
