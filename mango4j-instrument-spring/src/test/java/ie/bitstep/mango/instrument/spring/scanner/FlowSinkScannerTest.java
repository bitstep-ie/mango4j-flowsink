package ie.bitstep.mango.instrument.spring.scanner;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.TargetClassAware;
import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycle;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycles;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.RequiredAttributes;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapted from obsinity:
 * /home/jallen/git/obsinity/obsinity-collection-spring/src/test/java/com/obsinity/collection/spring/scanner/FlowSinkScannerTest.java
 */
class FlowSinkScannerTest {

	@FlowSink
	static class MySinks {
		static final AtomicInteger failureAny = new AtomicInteger();
		static final AtomicInteger failureWithAttrs = new AtomicInteger();
		static final AtomicInteger failureWithThrowableOnly = new AtomicInteger();
		static volatile Throwable lastThrowable;
		static final AtomicInteger completed = new AtomicInteger();

		@OnFlowFailure
		public void onFailureAny(FlowEvent event) {
			failureAny.incrementAndGet();
		}

		@OnFlowFailure
		@RequiredAttributes({"order.id", "error.code"})
		public void onFailureWithAttrs(FlowEvent event) {
			failureWithAttrs.incrementAndGet();
		}

		@OnFlowFailure
		public void onFailureThrowable(Throwable throwable) {
			lastThrowable = throwable;
			failureWithThrowableOnly.incrementAndGet();
		}

		@OnFlowFailure
		public void onFailureThrowableRoot(@FlowException(FlowException.Source.ROOT_CAUSE) Throwable throwable) {}

		@OnFlowCompleted
		public void onCompleted(FlowEvent event) {
			completed.incrementAndGet();
		}
	}

	@FlowSink
	static class NullTargetClassAwareSink implements TargetClassAware {
		@Override
		public Class<?> getTargetClass() {
			return null;
		}

		@OnFlowFailure
		public void onFailure(FlowEvent event) {}
	}

	static class PlainBean {}

	@FlowSink
	static class NoLifecycleSink {
		public void helperOnly() {}
	}

	@FlowSink
	static class FallbackOnlySink {
		static final java.util.concurrent.atomic.AtomicInteger fallbackCalls =
				new java.util.concurrent.atomic.AtomicInteger();

		@ie.bitstep.mango.instrument.annotations.OnFlowNotMatched
		public void onFallback() {
			fallbackCalls.incrementAndGet();
		}
	}

	private FlowHandlerRegistry registry;
	private FlowSinkScanner scanner;

	@BeforeEach
	void setUp() {
		registry = new FlowHandlerRegistry();
		scanner = new FlowSinkScanner(registry);
		scanner.postProcessAfterInitialization(new MySinks(), "mySinks");
		MySinks.failureAny.set(0);
		MySinks.failureWithAttrs.set(0);
		MySinks.failureWithThrowableOnly.set(0);
		MySinks.lastThrowable = null;
		MySinks.completed.set(0);
	}

	@Test
	void failed_lifecycle_dispatches_failure_and_completed_handlers() throws Exception {
		FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
		event.eventContext().put("lifecycle", "FAILED");
		event.attributes().put("order.id", "123");
		event.attributes().put("error.code", "PAYMENT");
		event.setThrowable(new IllegalArgumentException("bad payment"));

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(MySinks.failureAny.get()).isEqualTo(1);
		assertThat(MySinks.failureWithAttrs.get()).isEqualTo(1);
		assertThat(MySinks.failureWithThrowableOnly.get()).isEqualTo(1);
		assertThat(MySinks.lastThrowable).isInstanceOf(IllegalArgumentException.class);
		// @OnFlowCompleted fires on both COMPLETED and FAILED events
		assertThat(MySinks.completed.get()).isEqualTo(1);
	}

	@Test
	void skips_required_attribute_handler_when_attributes_are_missing() throws Exception {
		FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
		event.eventContext().put("lifecycle", "FAILED");
		event.setThrowable(new IllegalStateException("failed"));

		registry.handlers().get(0).handle(event);

		assertThat(MySinks.failureAny.get()).isEqualTo(1);
		assertThat(MySinks.failureWithAttrs.get()).isZero();
	}

	@Test
	void success_completion_matches_completed_handlers_only() throws Exception {
		FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
		event.eventContext().put("lifecycle", "COMPLETED");

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(MySinks.completed.get()).isEqualTo(1);
		assertThat(MySinks.failureAny.get()).isZero();
		assertThat(MySinks.failureWithThrowableOnly.get()).isZero();
	}

	@Test
	void target_class_aware_sink_with_null_target_class_is_still_registered() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);

		Object bean = new NullTargetClassAwareSink();
		Object processed = localScanner.postProcessAfterInitialization(bean, "nullTargetClassAwareSink");

		assertThat(processed).isSameAs(bean);
		assertThat(localRegistry.handlers()).hasSize(1);
	}

	@Test
	void fallback_only_sink_is_registered_when_handlers_empty_but_fallbacks_present() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		FallbackOnlySink.fallbackCalls.set(0);

		Object bean = new FallbackOnlySink();
		Object processed = localScanner.postProcessAfterInitialization(bean, "fallbackOnlySink");

		assertThat(processed).isSameAs(bean);
		assertThat(localRegistry.handlers()).hasSize(1);
	}

	@FlowSink
	static class LifecycleScopedFallbackSink {
		static final AtomicInteger fallbackCalls = new AtomicInteger();

		@ie.bitstep.mango.instrument.annotations.OnFlowNotMatched
		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)
		public void onUnmatchedStart() {
			fallbackCalls.incrementAndGet();
		}
	}

	@Test
	void lifecycle_restricted_fallback_fires_only_for_matching_lifecycle() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		LifecycleScopedFallbackSink.fallbackCalls.set(0);
		localScanner.postProcessAfterInitialization(new LifecycleScopedFallbackSink(), "sink");

		FlowEvent started = FlowEvent.builder().name("orders.pay").build();
		started.eventContext().put("lifecycle", "STARTED");
		FlowEvent completed = FlowEvent.builder().name("orders.pay").build();
		completed.eventContext().put("lifecycle", "COMPLETED");
		FlowEvent failed = FlowEvent.builder().name("orders.pay").build();
		failed.eventContext().put("lifecycle", "FAILED");

		for (var handler : localRegistry.handlers()) {
			handler.handle(started);
			handler.handle(completed);
			handler.handle(failed);
		}

		assertThat(LifecycleScopedFallbackSink.fallbackCalls.get()).isEqualTo(1);
	}

	@FlowSink
	static class MethodMultiLifecycleSink {
		static final AtomicInteger startedCount = new AtomicInteger();
		static final AtomicInteger completedCount = new AtomicInteger();

		@OnFlowLifecycles({
			@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED),
			@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.COMPLETED)
		})
		void onStartedOrCompleted(FlowEvent event) {
			if ("STARTED".equals(event.eventContext().get("lifecycle"))) {
				startedCount.incrementAndGet();
			} else {
				completedCount.incrementAndGet();
			}
		}
	}

	@FlowSink
	static class RepeatableLifecycleSink {
		static final AtomicInteger count = new AtomicInteger();

		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)
		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.COMPLETED)
		void onStartedOrCompleted(FlowEvent event) {
			count.incrementAndGet();
		}
	}

	@Test
	void method_level_at_OnFlowLifecycles_fires_for_each_declared_lifecycle() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		MethodMultiLifecycleSink.startedCount.set(0);
		MethodMultiLifecycleSink.completedCount.set(0);
		localScanner.postProcessAfterInitialization(new MethodMultiLifecycleSink(), "sink");

		FlowEvent started = FlowEvent.builder().name("orders.pay").build();
		started.eventContext().put("lifecycle", "STARTED");
		FlowEvent completed = FlowEvent.builder().name("orders.pay").build();
		completed.eventContext().put("lifecycle", "COMPLETED");
		FlowEvent failed = FlowEvent.builder().name("orders.pay").build();
		failed.eventContext().put("lifecycle", "FAILED");

		for (var handler : localRegistry.handlers()) {
			handler.handle(started);
			handler.handle(completed);
			handler.handle(failed);
		}

		assertThat(MethodMultiLifecycleSink.startedCount.get()).isEqualTo(1);
		assertThat(MethodMultiLifecycleSink.completedCount.get()).isEqualTo(1);
	}

	@Test
	void repeatable_at_OnFlowLifecycle_is_equivalent_to_at_OnFlowLifecycles() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		RepeatableLifecycleSink.count.set(0);
		localScanner.postProcessAfterInitialization(new RepeatableLifecycleSink(), "sink");

		FlowEvent started = FlowEvent.builder().name("orders.pay").build();
		started.eventContext().put("lifecycle", "STARTED");
		FlowEvent completed = FlowEvent.builder().name("orders.pay").build();
		completed.eventContext().put("lifecycle", "COMPLETED");
		FlowEvent failed = FlowEvent.builder().name("orders.pay").build();
		failed.eventContext().put("lifecycle", "FAILED");

		for (var handler : localRegistry.handlers()) {
			handler.handle(started);
			handler.handle(completed);
			handler.handle(failed);
		}

		assertThat(RepeatableLifecycleSink.count.get()).isEqualTo(2); // STARTED + COMPLETED, not FAILED
	}

	interface EventConsumer<E> {
		void consume(E event);
	}

	@FlowSink
	static class GenericInterfaceSink implements EventConsumer<FlowEvent> {
		static final AtomicInteger invocations = new AtomicInteger();

		@OnFlowCompleted
		@Override
		public void consume(FlowEvent event) {
			invocations.incrementAndGet();
		}
	}

	@Test
	void generic_interface_sink_handler_fires_exactly_once_per_event() {
		// Regression: without the bridge-method filter, doWithMethods yields both the concrete
		// consume(FlowEvent) and the bridge consume(Object). With @bridged annotation lookups, the
		// bridge path also compiles a handler pointing to the same invocable → double invocation.
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		GenericInterfaceSink.invocations.set(0);
		localScanner.postProcessAfterInitialization(new GenericInterfaceSink(), "genericSink");

		assertThat(localRegistry.handlers()).hasSize(1);

		FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
		event.eventContext().put("lifecycle", "COMPLETED");
		localRegistry.handlers().get(0).handle(event);

		assertThat(GenericInterfaceSink.invocations.get()).isEqualTo(1);
	}

	@Test
	void returns_original_bean_for_non_sink_and_no_lifecycle_sink() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		PlainBean plainBean = new PlainBean();
		NoLifecycleSink noLifecycleSink = new NoLifecycleSink();

		Object plainProcessed = localScanner.postProcessAfterInitialization(plainBean, "plainBean");
		Object noLifecycleProcessed = localScanner.postProcessAfterInitialization(noLifecycleSink, "noLifecycleSink");

		assertThat(plainProcessed).isSameAs(plainBean);
		assertThat(noLifecycleProcessed).isSameAs(noLifecycleSink);
		assertThat(localRegistry.handlers()).isEmpty();
	}

	@FlowSink
	static class FailedLifecycleThrowableSink {
		static volatile Throwable received;

		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.FAILED)
		public void onFailed(Throwable t) {
			received = t;
		}
	}

	@FlowSink
	static class StartedLifecycleThrowableSink {
		@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)
		public void onStarted(Throwable t) {}
	}

	@Test
	void failed_lifecycle_throwable_parameter_is_bound_via_third_allows_throwable_condition() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		FailedLifecycleThrowableSink.received = null;
		localScanner.postProcessAfterInitialization(new FailedLifecycleThrowableSink(), "sink");

		assertThat(localRegistry.handlers()).hasSize(1);

		FlowEvent event = FlowEvent.builder().name("orders.pay").build();
		event.eventContext().put("lifecycle", "FAILED");
		RuntimeException ex = new RuntimeException("boom");
		event.setThrowable(ex);
		localRegistry.handlers().get(0).handle(event);

		assertThat(FailedLifecycleThrowableSink.received).isSameAs(ex);
	}

	@Test
	void started_lifecycle_with_only_throwable_param_is_rejected_when_allows_throwable_is_false() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);

		assertThatThrownBy(
						() -> localScanner.postProcessAfterInitialization(new StartedLifecycleThrowableSink(), "sink"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("cannot be bound");
	}

	@FlowSink
	static class FailureAndFallbackSink {
		static final AtomicInteger failureCalls = new AtomicInteger();
		static final AtomicInteger fallbackCalls = new AtomicInteger();

		@OnFlowFailure
		public void onFailure(FlowEvent event) {
			failureCalls.incrementAndGet();
		}

		@ie.bitstep.mango.instrument.annotations.OnFlowNotMatched
		public void onFallback() {
			fallbackCalls.incrementAndGet();
		}
	}

	@Test
	void failure_handler_fires_and_fallback_is_not_invoked() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		FailureAndFallbackSink.failureCalls.set(0);
		FailureAndFallbackSink.fallbackCalls.set(0);
		localScanner.postProcessAfterInitialization(new FailureAndFallbackSink(), "sink");

		FlowEvent event = FlowEvent.builder().name("orders.pay").build();
		event.eventContext().put("lifecycle", "FAILED");
		event.setThrowable(new RuntimeException("boom"));
		localRegistry.handlers().get(0).handle(event);

		assertThat(FailureAndFallbackSink.failureCalls.get()).isEqualTo(1);
		assertThat(FailureAndFallbackSink.fallbackCalls.get()).isZero();
	}

	@Test
	void sink_name_matches_the_registered_class_name() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		localScanner.postProcessAfterInitialization(new MySinks(), "mySinks");

		assertThat(localRegistry.handlers().get(0).sinkName()).isEqualTo("MySinks");
	}

	@FlowSink
	static class AllLifecycleSink {
		static final AtomicInteger calls = new AtomicInteger();

		@OnFlowCompleted
		public void onCompleted() {
			calls.incrementAndGet();
		}
	}

	@OnFlowStarted
	@OnFlowScope("audit")
	@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
	@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@interface AuditStarted {}

	@FlowSink
	static class MetaAnnotatedSink {
		static final AtomicInteger calls = new AtomicInteger();

		@AuditStarted
		public void onAuditStarted(FlowEvent event) {
			calls.incrementAndGet();
		}
	}

	@Test
	void meta_annotation_combining_lifecycle_and_scope_is_resolved() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		MetaAnnotatedSink.calls.set(0);
		localScanner.postProcessAfterInitialization(new MetaAnnotatedSink(), "sink");

		assertThat(localRegistry.handlers()).hasSize(1);

		FlowEvent inScope = FlowEvent.builder().name("audit.login").build();
		inScope.eventContext().put("lifecycle", "STARTED");
		FlowEvent outOfScope = FlowEvent.builder().name("orders.pay").build();
		outOfScope.eventContext().put("lifecycle", "STARTED");
		FlowEvent wrongLifecycle = FlowEvent.builder().name("audit.login").build();
		wrongLifecycle.eventContext().put("lifecycle", "COMPLETED");

		for (var handler : localRegistry.handlers()) {
			handler.handle(inScope);
			handler.handle(outOfScope);
			handler.handle(wrongLifecycle);
		}

		assertThat(MetaAnnotatedSink.calls.get()).isEqualTo(1);
	}

	@Test
	void unknown_or_absent_lifecycle_value_does_not_dispatch_any_handler() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		AllLifecycleSink.calls.set(0);
		localScanner.postProcessAfterInitialization(new AllLifecycleSink(), "sink");

		FlowEvent noLifecycle = FlowEvent.builder().name("orders.pay").build();
		FlowEvent unknownLifecycle = FlowEvent.builder().name("orders.pay").build();
		unknownLifecycle.eventContext().put("lifecycle", "PAUSED");

		for (var handler : localRegistry.handlers()) {
			handler.handle(noLifecycle);
			handler.handle(unknownLifecycle);
		}

		assertThat(AllLifecycleSink.calls.get()).isZero();
	}
}
