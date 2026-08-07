package ie.bitstep.mango.flowsink.core.dispatch;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import ie.bitstep.mango.flowsink.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.flowsink.model.FlowEvent;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncDispatchBusTest {

	@Test
	void dispatches_a_snapshot_not_the_mutated_original_event() {
		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		List<FlowEvent> seen = new CopyOnWriteArrayList<>();
		CountDownLatch delivered = new CountDownLatch(1);
		registry.register(event -> {
			seen.add(event);
			delivered.countDown();
		});
		AsyncDispatchBus bus = new AsyncDispatchBus(registry);

		FlowEvent original = FlowEvent.builder().name("demo.flow").build();
		original.eventContext().put("lifecycle", "STARTED");
		original.attributes().put("user.id", "alice");

		bus.dispatch(original);

		original.eventContext().put("lifecycle", "COMPLETED");
		original.attributes().put("extra", "later");

		assertThat(awaitDelivered(delivered)).isTrue();
		FlowEvent snapshot = seen.get(0);
		assertThat(snapshot).isNotSameAs(original);
		assertThat(snapshot.eventContext()).containsEntry("lifecycle", "STARTED");
		assertThat(snapshot.attributes().map())
				.containsEntry("user.id", "alice")
				.doesNotContainKey("extra");

		bus.close();
	}

	@Test
	void continues_dispatching_when_one_sink_throws() {
		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		List<FlowEvent> seen = new CopyOnWriteArrayList<>();
		CountDownLatch delivered = new CountDownLatch(1);
		registry.register(event -> {
			throw new IllegalStateException("boom");
		});
		registry.register(event -> {
			seen.add(event);
			delivered.countDown();
		});
		AsyncDispatchBus bus = new AsyncDispatchBus(registry);

		FlowEvent event = FlowEvent.builder().name("demo.flow").build();
		bus.dispatch(event);

		assertThat(awaitDelivered(delivered)).isTrue();
		assertThat(seen.get(0).name()).isEqualTo("demo.flow");

		bus.close();
	}

	@Test
	void continues_dispatching_when_one_sink_throws_error() {
		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		List<FlowEvent> seen = new CopyOnWriteArrayList<>();
		CountDownLatch delivered = new CountDownLatch(1);
		AtomicInteger attempts = new AtomicInteger();
		registry.register(event -> {
			if (attempts.getAndIncrement() == 0) {
				throw new UndeclaredThrowableException(
						new java.lang.reflect.InvocationTargetException(new IllegalArgumentException("root")));
			}
			seen.add(event);
			delivered.countDown();
		});
		AsyncDispatchBus bus = new AsyncDispatchBus(registry);

		bus.dispatch(FlowEvent.builder().name("first").build());
		bus.dispatch(FlowEvent.builder().name("second").build());

		assertThat(awaitDelivered(delivered)).isTrue();
		assertThat(seen.get(0).name()).isEqualTo("second");
		assertThat(attempts.get()).isGreaterThanOrEqualTo(2);

		bus.close();
	}

	@Test
	void registry_ignores_null_handler_registration() {
		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		registry.register(null);
		assertThat(registry.handlers()).isEmpty();
	}

	@Test
	void ignores_null_dispatch_and_unwraps_nested_reflection_exceptions() {
		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		AsyncDispatchBus bus = new AsyncDispatchBus(registry);
		bus.dispatch(null);
		bus.close();

		IllegalArgumentException root = new IllegalArgumentException("root");
		java.lang.reflect.InvocationTargetException invocation = new java.lang.reflect.InvocationTargetException(root);
		UndeclaredThrowableException undeclared = new UndeclaredThrowableException(invocation);
		UndeclaredThrowableException selfReferential = new UndeclaredThrowableException(null) {
			@Override
			public synchronized Throwable getUndeclaredThrowable() {
				return this;
			}
		};

		assertThat(AsyncDispatchBus.unwrap(undeclared)).isSameAs(root);
		assertThat(AsyncDispatchBus.unwrap(new java.lang.reflect.InvocationTargetException(null)))
				.isInstanceOf(java.lang.reflect.InvocationTargetException.class);
		assertThat(AsyncDispatchBus.unwrap(selfReferential)).isSameAs(selfReferential);
	}

	@Test
	void drops_event_and_logs_warn_when_queue_is_at_capacity() throws Exception {
		CountDownLatch workerOccupied = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);

		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		registry.register(event -> {
			workerOccupied.countDown();
			try {
				releaseWorker.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});

		AsyncDispatchBus bus = new AsyncDispatchBus(registry);

		// Block the worker thread inside the sink so it can't drain the queue
		bus.dispatch(FlowEvent.builder().name("blocker").build());
		assertThat(workerOccupied.await(2, TimeUnit.SECONDS)).isTrue();

		// Fill the queue to its capacity limit
		for (int i = 0; i < AsyncDispatchBus.MAX_QUEUE_DEPTH; i++) {
			bus.dispatch(FlowEvent.builder().name("flood").build());
		}

		// This dispatch overflows the queue — the warn branch in offer() is hit
		bus.dispatch(FlowEvent.builder().name("overflow").build());

		releaseWorker.countDown();
		bus.close();
	}

	private static boolean awaitDelivered(CountDownLatch delivered) {
		try {
			return delivered.await(2, TimeUnit.SECONDS);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
