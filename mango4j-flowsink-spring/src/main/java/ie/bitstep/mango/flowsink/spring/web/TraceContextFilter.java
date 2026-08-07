package ie.bitstep.mango.flowsink.spring.web;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.spring.AbstractTraceContextFilter;

/**
 * Servlet {@link Filter} that extracts incoming trace context headers (W3C {@code traceparent}, B3 single, or B3 multi)
 * and populates SLF4J MDC keys ({@code traceId}, {@code spanId}, {@code parentSpanId}) for the duration of the request.
 */
public final class TraceContextFilter extends AbstractTraceContextFilter implements Filter {

	/** @param support used to clean up thread-local flow state after each request */
	public TraceContextFilter(FlowProcessorSupport support) {
		super(support);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String[] previous = saveMdcState();
		try {
			if (request instanceof HttpServletRequest httpRequest) {
				applyTraceHeaders(httpRequest::getHeader);
			}
			chain.doFilter(request, response);
		} finally {
			cleanupAndRestore(previous);
		}
	}
}
