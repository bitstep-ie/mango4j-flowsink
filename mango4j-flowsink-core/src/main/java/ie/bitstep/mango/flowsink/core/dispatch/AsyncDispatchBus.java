package ie.bitstep.mango.flowsink.core.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ie.bitstep.mango.flowsink.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.flowsink.core.sinks.FlowSinkHandler;
import ie.bitstep.mango.flowsink.model.FlowEvent;

public final class AsyncDispatchBus implements AutoCloseable {
	private static final Logger log = LoggerFactory.getLogger(AsyncDispatchBus.class);

	private final FlowHandlerRegistry registry;
	private final Map<FlowSinkHandler, Worker> workers = new ConcurrentHashMap<>();
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public AsyncDispatchBus(FlowHandlerRegistry registry) {
		this.registry = Objects.requireNonNull(registry, "registry");
	}

	public void dispatch(FlowEvent event) {
		if (event == null || closed.get()) {
			return;
		}
		List<FlowSinkHandler> handlers = registry.handlers();
		for (FlowSinkHandler handler : handlers) {
			workers.computeIfAbsent(handler, Worker::new).offer(event.snapshot());
		}
	}

	static Throwable unwrap(Throwable throwable) {
		Throwable current = throwable;
		Throwable next = Worker.unwrapOne(current);
		while (next != null && next != current) {
			current = next;
			next = Worker.unwrapOne(current);
		}
		return current;
	}

	@Override
	public void close() {
		closed.set(true);
		List<Thread> threads = new ArrayList<>();
		workers.values().forEach(w -> {
			threads.add(w.thread);
			w.shutdown();
		});
		workers.clear();
		boolean interrupted = false;
		long deadline = System.currentTimeMillis() + 5_000;
		for (Thread t : threads) {
			try {
				long remaining = deadline - System.currentTimeMillis();
				if (remaining > 0) {
					t.join(remaining);
				}
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	static final int MAX_QUEUE_DEPTH = 10_000;

	private static final class Worker implements Runnable {
		private final FlowSinkHandler sink;
		private final LinkedBlockingDeque<FlowEvent> queue = new LinkedBlockingDeque<>(MAX_QUEUE_DEPTH);
		private final AtomicBoolean running = new AtomicBoolean(true);
		private final Thread thread;

		private Worker(FlowSinkHandler sink) {
			this.sink = sink;
			this.thread = new Thread(this, "mango4j-flowsink-" + sink.sinkName());
			this.thread.setDaemon(true);
			this.thread.start();
		}

		private void offer(FlowEvent event) {
			if (!queue.offer(event)) {
				log.warn("Event dropped for sink {}: queue rejected offer", sink.sinkName());
			}
		}

		private void shutdown() {
			running.set(false);
			thread.interrupt();
		}

		@Override
		public void run() {
			while (running.get()) {
				FlowEvent event = null;
				try {
					event = queue.poll(250, TimeUnit.MILLISECONDS);
					if (event != null) {
						sink.handle(event);
					}
				} catch (InterruptedException interruptedException) {
					Thread.currentThread().interrupt();
				} catch (
						Throwable throwable) { // NOSONAR - must survive sink Errors so the worker queue is not orphaned
					Throwable root = AsyncDispatchBus.unwrap(throwable);
					String eventName = event != null ? event.name() : "<poll>";
					log.warn(
							"Flow sink {} failed to handle event {} due to {}",
							sink.sinkName(),
							eventName,
							root.getMessage(),
							root);
				}
			}
			// Drain any events still queued at shutdown so nothing is silently lost
			FlowEvent pending;
			while ((pending = queue.poll()) != null) {
				try {
					sink.handle(pending);
				} catch (Exception e) {
					log.warn("Flow sink {} failed during shutdown drain", sink.sinkName(), e);
				}
			}
		}

		private static Throwable unwrapOne(Throwable t) {
			if (t instanceof InvocationTargetException ite) {
				return ite.getTargetException();
			}
			if (t instanceof UndeclaredThrowableException ute) {
				return ute.getUndeclaredThrowable();
			}
			return null;
		}
	}
}
