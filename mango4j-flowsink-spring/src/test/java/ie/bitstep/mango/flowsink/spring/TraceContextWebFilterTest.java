package ie.bitstep.mango.flowsink.spring;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.spring.webflux.TraceContextWebFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapted from obsinity:
 * /home/jallen/git/obsinity/obsinity-collection-spring/src/test/java/com/obsinity/collection/spring/TraceContextWebFilterTest.java
 */
class TraceContextWebFilterTest {

	static class TrackingSupport extends FlowProcessorSupport {
		int cleanupCalls;

		@Override
		public void cleanupThreadLocals() {
			cleanupCalls++;
		}
	}

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void extracts_b3_headers_and_restores_mdc() {
		TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("b3", "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1-a2fb4a1d1a96d312")
				.build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get()).containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
		assertThat(seen.get()).containsEntry("spanId", "00f067aa0ba902b7");
		assertThat(seen.get()).containsEntry("parentSpanId", "a2fb4a1d1a96d312");
		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
	}

	@Test
	void extracts_traceparent_and_restores_previous_mdc() {
		MDC.put("traceId", "previous");
		TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
				.build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get())
				.containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736")
				.containsEntry("spanId", "00f067aa0ba902b7");
		assertThat(MDC.get("traceId")).isEqualTo("previous");
		assertThat(MDC.get("spanId")).isNull();
	}

	@Test
	void ignores_invalid_traceparent_and_restores_previous_context() {
		MDC.put("traceId", "previous-trace");
		MDC.put("spanId", "previous-span");
		MDC.put("parentSpanId", "previous-parent");
		MDC.put("tracestate", "previous-state");
		MDC.put("traceparent", "previous-header");

		TraceContextWebFilter filter = new TraceContextWebFilter(null);
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("traceparent", "broken")
				.header("tracestate", "next-state")
				.build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get())
				.containsEntry("traceId", "previous-trace")
				.containsEntry("spanId", "previous-span")
				.containsEntry("parentSpanId", "previous-parent")
				.containsEntry("traceparent", "broken")
				.containsEntry("tracestate", "next-state");
		assertThat(MDC.getCopyOfContextMap())
				.containsEntry("traceId", "previous-trace")
				.containsEntry("spanId", "previous-span")
				.containsEntry("parentSpanId", "previous-parent")
				.containsEntry("tracestate", "previous-state")
				.containsEntry("traceparent", "previous-header");
	}

	@Test
	void extracts_b3_multi_headers_when_single_header_is_absent() {
		TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("X-B3-TraceId", "trace-123")
				.header("X-B3-SpanId", "span-456")
				.header("X-B3-ParentSpanId", "parent-789")
				.build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get())
				.containsEntry("traceId", "trace-123")
				.containsEntry("spanId", "span-456")
				.containsEntry("parentSpanId", "parent-789");
		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
	}

	@Test
	void extracts_b3_single_without_parent_span() {
		TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("b3", "4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7")
				.build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get())
				.containsEntry("traceId", "4BF92F3577B34DA6A3CE929D0E0E4736")
				.containsEntry("spanId", "00F067AA0BA902B7")
				.doesNotContainKey("parentSpanId");
		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
	}

	@Test
	void ignores_malformed_traceparent_and_b3_single_headers() {
		TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("traceparent", "00-short-span-01")
				.build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get()).containsEntry("traceparent", "00-short-span-01");
		assertThat(seen.get()).doesNotContainKeys("traceId", "spanId", "parentSpanId");
		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
	}

	@Test
	void ignores_malformed_b3_single_header() {
		TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/test").header("b3", "traceonly").build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(MDC.getCopyOfContextMap());
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get()).isNull();
		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
	}

	@Test
	void truncates_oversized_b3_trace_id_before_storing_in_mdc() {
		TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("X-B3-TraceId", "t".repeat(600))
				.header("X-B3-SpanId", "s".repeat(600))
				.build());
		AtomicReference<Map<String, String>> seen = new AtomicReference<>();

		WebFilterChain chain = webExchange -> {
			seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
			return Mono.empty();
		};

		filter.filter(exchange, chain).block();

		assertThat(seen.get().get("traceId")).hasSize(AbstractTraceContextFilter.MAX_TRACE_ID_LENGTH);
		assertThat(seen.get().get("spanId")).hasSize(AbstractTraceContextFilter.MAX_SPAN_ID_LENGTH);
	}

	@Test
	void restores_previous_context_when_chain_errors() {
		MDC.put("traceId", "previous");
		TrackingSupport support = new TrackingSupport();
		TraceContextWebFilter filter = new TraceContextWebFilter(support);
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
				.header("X-B3-TraceId", "trace-123")
				.header("X-B3-SpanId", "span-456")
				.build());

		assertThatThrownBy(() -> filter.filter(exchange, webExchange -> Mono.error(new IllegalStateException("boom")))
						.block())
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("boom");

		assertThat(MDC.get("traceId")).isEqualTo("previous");
		assertThat(MDC.get("spanId")).isNull();
		assertThat(support.cleanupCalls).isEqualTo(1);
	}

	@Test
	void parse_helpers_return_empty_array_for_unparseable_inputs() throws Exception {
		Method parseTraceparent = AbstractTraceContextFilter.class.getDeclaredMethod("parseTraceparent", String.class);
		Method parseB3Single = AbstractTraceContextFilter.class.getDeclaredMethod("parseB3Single", String.class);
		parseTraceparent.setAccessible(true);
		parseB3Single.setAccessible(true);

		assertThat((String[]) parseTraceparent.invoke(null, new Object[] {null}))
				.isEmpty();
		assertThat((String[]) parseB3Single.invoke(null, new Object[] {null})).isEmpty();
		assertThat((String[]) parseTraceparent.invoke(null, "00-12345678901234567890123456789012-short-span-01"))
				.isEmpty();
	}
}
