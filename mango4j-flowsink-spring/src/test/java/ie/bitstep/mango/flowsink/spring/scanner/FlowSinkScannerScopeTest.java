package ie.bitstep.mango.flowsink.spring.scanner;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ie.bitstep.mango.flowsink.annotations.OnAllLifecycles;
import ie.bitstep.mango.flowsink.annotations.OnFlowCompleted;
import ie.bitstep.mango.flowsink.annotations.OnFlowFailure;
import ie.bitstep.mango.flowsink.annotations.OnFlowLifecycle;
import ie.bitstep.mango.flowsink.annotations.OnFlowLifecycles;
import ie.bitstep.mango.flowsink.annotations.OnFlowNotMatched;
import ie.bitstep.mango.flowsink.annotations.OnFlowScope;
import ie.bitstep.mango.flowsink.annotations.OnFlowStarted;
import ie.bitstep.mango.flowsink.annotations.OnFlowSuccess;
import ie.bitstep.mango.flowsink.annotations.PullAllAttributes;
import ie.bitstep.mango.flowsink.annotations.PullAllContextValues;
import ie.bitstep.mango.flowsink.annotations.PullAttribute;
import ie.bitstep.mango.flowsink.annotations.PullContextValue;
import ie.bitstep.mango.flowsink.annotations.RequiredEventContext;
import ie.bitstep.mango.flowsink.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.flowsink.model.FlowEvent;
import ie.bitstep.mango.flowsink.spring.annotations.FlowSink;

import static org.assertj.core.api.Assertions.assertThat;

class FlowSinkScannerScopeTest {

	@FlowSink
	@OnFlowScope("demo.")
	static class ScopedSink {
		static final AtomicInteger started = new AtomicInteger();
		static final AtomicInteger success = new AtomicInteger();
		static final AtomicInteger fallback = new AtomicInteger();
		static final AtomicReference<String> pulledUser = new AtomicReference<>();

		@OnFlowStarted
		public void onStarted(@PullAttribute("user.id") String userId) {
			started.incrementAndGet();
			pulledUser.set(userId);
		}

		@OnFlowSuccess
		public void onSuccess() {
			success.incrementAndGet();
		}

		@OnFlowNotMatched
		public void onFallback() {
			fallback.incrementAndGet();
		}
	}

	@FlowSink
	@OnFlowScope("checkout")
	@OnAllLifecycles
	static class CheckoutSink {
		static final AtomicInteger started = new AtomicInteger();
		static final AtomicInteger success = new AtomicInteger();
		static final AtomicInteger failure = new AtomicInteger();
		static final AtomicInteger fallback = new AtomicInteger();
		static final AtomicInteger dotChop = new AtomicInteger();
		static final AtomicReference<String> userId = new AtomicReference<>();
		static final AtomicReference<Integer> cartSize = new AtomicReference<>();
		static volatile Map<String, Object> attributes;
		static volatile Map<String, Object> context;
		static volatile Throwable rootFailure;

		@OnFlowStarted
		public void onStarted(
				@PullAttribute("user.id") String pulledUserId,
				@PullAllAttributes Map<String, Object> pulledAttributes,
				@PullContextValue("cart.size") Integer pulledCartSize,
				@PullAllContextValues Map<String, Object> pulledContext) {
			started.incrementAndGet();
			userId.set(pulledUserId);
			cartSize.set(pulledCartSize);
			attributes = pulledAttributes;
			context = pulledContext;
		}

		@OnFlowSuccess
		public void onSuccess() {
			success.incrementAndGet();
		}

		@OnFlowFailure
		public void onFailure(
				@ie.bitstep.mango.flowsink.annotations.FlowException(
								ie.bitstep.mango.flowsink.annotations.FlowException.Source.ROOT_CAUSE)
						Throwable throwable) {
			failure.incrementAndGet();
			rootFailure = throwable;
		}

		@OnFlowNotMatched
		public void onFallback() {
			fallback.incrementAndGet();
		}
	}

	@FlowSink
	static class DotChopSink {
		static final AtomicInteger called = new AtomicInteger();

		@OnFlowStarted
		@OnFlowScope("a.b")
		public void onScopedStart() {
			called.incrementAndGet();
		}
	}

	@FlowSink
	@OnFlowScope("special")
	@OnFlowLifecycles({@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)})
	static class ContextualSink {
		static final AtomicInteger lifecycleMatch = new AtomicInteger();
		static final AtomicInteger fallback = new AtomicInteger();
		static final AtomicReference<String> pulledTrace = new AtomicReference<>();
		static final AtomicReference<String> pulledCount = new AtomicReference<>();

		@OnFlowStarted
		@RequiredEventContext({"trace.id"})
		public void onStarted(@PullContextValue("trace.id") String traceId, @PullAttribute("count") String count) {
			lifecycleMatch.incrementAndGet();
			pulledTrace.set(traceId);
			pulledCount.set(count);
		}

		@OnFlowCompleted
		public void onCompletedShouldBeFilteredOut() {
			lifecycleMatch.addAndGet(100);
		}

		@OnFlowNotMatched
		public void onFallback() {
			fallback.incrementAndGet();
		}
	}

	@FlowSink
	static class NoHandlerSink {
		static final AtomicInteger called = new AtomicInteger();

		public void notAHandler() {
			called.incrementAndGet();
		}
	}

	@FlowSink
	static class BlankScopeSink {
		static final AtomicInteger started = new AtomicInteger();
		static final AtomicInteger fallback = new AtomicInteger();

		@OnFlowStarted
		@OnFlowScope(" ")
		public void onStarted() {
			started.incrementAndGet();
		}

		@OnFlowNotMatched
		public void onFallback() {
			fallback.incrementAndGet();
		}
	}

	private FlowHandlerRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new FlowHandlerRegistry();
		FlowSinkScanner scanner = new FlowSinkScanner(registry);
		scanner.postProcessAfterInitialization(new ScopedSink(), "scopedSink");
		scanner.postProcessAfterInitialization(new CheckoutSink(), "checkoutSink");
		scanner.postProcessAfterInitialization(new DotChopSink(), "dotChopSink");
		scanner.postProcessAfterInitialization(new ContextualSink(), "contextualSink");
		scanner.postProcessAfterInitialization(new NoHandlerSink(), "noHandlerSink");
		scanner.postProcessAfterInitialization(new BlankScopeSink(), "blankScopeSink");
		ScopedSink.started.set(0);
		ScopedSink.success.set(0);
		ScopedSink.fallback.set(0);
		ScopedSink.pulledUser.set(null);
		CheckoutSink.started.set(0);
		CheckoutSink.success.set(0);
		CheckoutSink.failure.set(0);
		CheckoutSink.fallback.set(0);
		CheckoutSink.dotChop.set(0);
		CheckoutSink.userId.set(null);
		CheckoutSink.cartSize.set(null);
		CheckoutSink.attributes = null;
		CheckoutSink.context = null;
		CheckoutSink.rootFailure = null;
		DotChopSink.called.set(0);
		ContextualSink.lifecycleMatch.set(0);
		ContextualSink.fallback.set(0);
		ContextualSink.pulledTrace.set(null);
		ContextualSink.pulledCount.set(null);
		NoHandlerSink.called.set(0);
		BlankScopeSink.started.set(0);
		BlankScopeSink.fallback.set(0);
	}

	@Test
	void matches_scope_and_success_handlers() throws Exception {
		FlowEvent started = FlowEvent.builder().name("demo.checkout").build();
		started.eventContext().put("lifecycle", "STARTED");
		started.attributes().put("user.id", "alice");
		FlowEvent completed = FlowEvent.builder().name("demo.checkout").build();
		completed.eventContext().put("lifecycle", "COMPLETED");

		registry.handlers().get(0).handle(started);
		registry.handlers().get(0).handle(completed);

		assertThat(ScopedSink.started.get()).isEqualTo(1);
		assertThat(ScopedSink.pulledUser.get()).isEqualTo("alice");
		assertThat(ScopedSink.success.get()).isEqualTo(1);
		assertThat(ScopedSink.fallback.get()).isZero();
	}

	@Test
	void invokes_fallback_when_scope_does_not_match() throws Exception {
		FlowEvent event = FlowEvent.builder().name("other.checkout").build();
		event.eventContext().put("lifecycle", "COMPLETED");

		registry.handlers().get(0).handle(event);

		assertThat(ScopedSink.started.get()).isZero();
		assertThat(ScopedSink.success.get()).isZero();
		assertThat(ScopedSink.fallback.get()).isEqualTo(1);
	}

	@Test
	void binds_started_parameters_and_filters_by_checkout_scope() throws Exception {
		FlowEvent event = FlowEvent.builder().name("checkout.payment").build();
		event.eventContext().put("lifecycle", "STARTED");
		event.eventContext().put("cart.size", 3);
		event.attributes().put("user.id", "alice");
		event.attributes().put("amount", 42);

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(CheckoutSink.started.get()).isEqualTo(1);
		assertThat(CheckoutSink.userId.get()).isEqualTo("alice");
		assertThat(CheckoutSink.cartSize.get()).isEqualTo(3);
		assertThat(CheckoutSink.attributes).containsEntry("amount", 42);
		assertThat(CheckoutSink.context).containsEntry("cart.size", 3);
	}

	@Test
	void invokes_failure_handler_with_root_cause_and_fallback_for_non_matching_sink() throws Exception {
		FlowEvent event = FlowEvent.builder().name("checkout.reserve").build();
		event.eventContext().put("lifecycle", "FAILED");
		Throwable root = new IllegalArgumentException("bad");
		event.setThrowable(new RuntimeException("wrap", root));

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(CheckoutSink.failure.get()).isEqualTo(1);
		assertThat(CheckoutSink.success.get()).isZero();
		assertThat(ScopedSink.success.get()).isZero();
		assertThat(CheckoutSink.rootFailure).isSameAs(root);
		assertThat(ScopedSink.fallback.get()).isEqualTo(1);
	}

	@Test
	void dot_chop_scope_matches_nested_name() throws Exception {
		FlowEvent event = FlowEvent.builder().name("a.b.c.d").build();
		event.eventContext().put("lifecycle", "STARTED");

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(DotChopSink.called.get()).isEqualTo(1);
	}

	@Test
	void requires_event_context_and_coerces_values_for_started_handlers() throws Exception {
		FlowEvent event = FlowEvent.builder().name("special.payment").build();
		event.eventContext().put("lifecycle", "STARTED");
		event.eventContext().put("trace.id", "trace-1");
		event.attributes().put("count", 42);

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(ContextualSink.lifecycleMatch.get()).isEqualTo(1);
		assertThat(ContextualSink.pulledTrace.get()).isEqualTo("trace-1");
		assertThat(ContextualSink.pulledCount.get()).isEqualTo("42");
		assertThat(ContextualSink.fallback.get()).isZero();
	}

	@Test
	void invokes_fallback_when_required_context_is_missing() throws Exception {
		FlowEvent event = FlowEvent.builder().name("special.payment").build();
		event.eventContext().put("lifecycle", "STARTED");

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(ContextualSink.lifecycleMatch.get()).isZero();
		assertThat(ContextualSink.fallback.get()).isEqualTo(1);
	}

	@Test
	void required_context_handler_does_not_match_when_context_is_missing_even_if_scope_and_lifecycle_match()
			throws Exception {
		FlowEvent event = FlowEvent.builder().name("special.payment").build();
		event.eventContext().put("lifecycle", "STARTED");
		event.attributes().put("count", 7);

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(ContextualSink.lifecycleMatch.get()).isZero();
		assertThat(ContextualSink.fallback.get()).isEqualTo(1);
		assertThat(ContextualSink.pulledTrace.get()).isNull();
	}

	@Test
	void class_level_lifecycle_filter_blocks_completed_handler() throws Exception {
		FlowEvent event = FlowEvent.builder().name("special.payment").build();
		event.eventContext().put("lifecycle", "COMPLETED");
		event.eventContext().put("trace.id", "trace-2");
		event.attributes().put("count", 7);

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(ContextualSink.lifecycleMatch.get()).isZero();
		assertThat(ContextualSink.fallback.get()).isEqualTo(1);
	}

	@Test
	void beans_without_flow_handlers_are_not_registered() {
		assertThat(registry.handlers()).hasSize(5);
		assertThat(NoHandlerSink.called.get()).isZero();
	}

	@Test
	void blank_method_scope_matches_any_started_flow() throws Exception {
		FlowEvent event = FlowEvent.builder().name(null).build();
		event.eventContext().put("lifecycle", "STARTED");

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(BlankScopeSink.started.get()).isEqualTo(1);
		assertThat(BlankScopeSink.fallback.get()).isZero();
	}
}
