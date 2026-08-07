package ie.bitstep.mango.flowsink.spring.webflux;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.spring.AbstractTraceContextFilter;

/**
 * Reactive {@link WebFilter} that extracts incoming trace context headers (W3C {@code traceparent}, B3 single, or B3
 * multi) and populates SLF4J MDC keys ({@code traceId}, {@code spanId}, {@code parentSpanId}) for the duration of the
 * request.
 */
public final class TraceContextWebFilter extends AbstractTraceContextFilter implements WebFilter {

	/** @param support used to clean up thread-local flow state after each request completes */
	public TraceContextWebFilter(FlowProcessorSupport support) {
		super(support);
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String[] previous = saveMdcState();
		applyTraceHeaders(exchange.getRequest().getHeaders()::getFirst);
		return chain.filter(exchange).doFinally(signalType -> cleanupAndRestore(previous));
	}
}
