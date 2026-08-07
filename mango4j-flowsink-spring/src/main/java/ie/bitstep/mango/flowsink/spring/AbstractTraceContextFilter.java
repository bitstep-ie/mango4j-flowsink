package ie.bitstep.mango.flowsink.spring;

import java.util.Locale;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;

public abstract class AbstractTraceContextFilter {
	private static final Logger log = LoggerFactory.getLogger(AbstractTraceContextFilter.class);

	protected static final String TRACE_ID = "traceId";
	protected static final String SPAN_ID = "spanId";
	protected static final String PARENT_SPAN_ID = "parentSpanId";
	protected static final String TRACESTATE = "tracestate";
	protected static final String TRACEPARENT = "traceparent";

	/**
	 * W3C recommends tracestate stay within 512 bytes; enforced here to stop a single header flooding every log line.
	 */
	static final int MAX_TRACESTATE_LENGTH = 512;

	static final int MAX_TRACE_HEADER_LENGTH = 512;
	static final int MAX_TRACE_ID_LENGTH = 128;
	static final int MAX_SPAN_ID_LENGTH = 64;
	static final int MAX_PARENT_SPAN_ID_LENGTH = 64;

	protected final FlowProcessorSupport support;

	protected AbstractTraceContextFilter(FlowProcessorSupport support) {
		this.support = support;
	}

	/**
	 * Snapshots the current MDC values for the five trace keys. Pass the returned array to
	 * {@link #cleanupAndRestore(String[])} in a finally/doFinally block.
	 */
	protected String[] saveMdcState() {
		return new String[] {
			MDC.get(TRACE_ID), MDC.get(SPAN_ID), MDC.get(PARENT_SPAN_ID), MDC.get(TRACESTATE), MDC.get(TRACEPARENT)
		};
	}

	/**
	 * Resolves trace headers via {@code headerLookup} and populates the MDC. The lookup function receives a header name
	 * and returns the value (or {@code null}); blank values are treated as absent. Supports W3C
	 * {@code traceparent}/{@code tracestate}, B3 single-header, and B3 multi-header formats.
	 */
	protected void applyTraceHeaders(UnaryOperator<String> headerLookup) {
		TraceContextHeaders headers = resolveTraceHeaders(headerLookup);
		putIfPresent(TRACEPARENT, headers.traceparent());
		putIfPresent(TRACESTATE, headers.tracestate());
		putIfPresent(TRACE_ID, headers.traceId());
		putIfPresent(SPAN_ID, headers.spanId());
		putIfPresent(PARENT_SPAN_ID, headers.parentSpanId());
	}

	protected TraceContextHeaders resolveTraceHeaders(UnaryOperator<String> headerLookup) {
		String traceparent = truncate(normalize(headerLookup.apply(TRACEPARENT)), MAX_TRACE_HEADER_LENGTH);
		String tracestate = truncate(normalize(headerLookup.apply(TRACESTATE)), MAX_TRACESTATE_LENGTH);
		String b3 = truncate(normalize(headerLookup.apply("b3")), MAX_TRACE_HEADER_LENGTH);
		String traceId = null;
		String spanId = null;
		String parentSpanId = null;

		if (traceparent != null) {
			String[] parsed = parseTraceparent(traceparent);
			if (parsed.length > 0) {
				traceId = parsed[0];
				spanId = parsed[1];
			}
		} else if (b3 != null) {
			String[] parsed = parseB3Single(b3);
			if (parsed.length > 0) {
				traceId = parsed[0];
				spanId = parsed[1];
				parentSpanId = parsed[2];
			}
		} else {
			traceId = truncate(normalize(headerLookup.apply("X-B3-TraceId")), MAX_TRACE_ID_LENGTH);
			spanId = truncate(normalize(headerLookup.apply("X-B3-SpanId")), MAX_SPAN_ID_LENGTH);
			parentSpanId = truncate(normalize(headerLookup.apply("X-B3-ParentSpanId")), MAX_PARENT_SPAN_ID_LENGTH);
		}

		return new TraceContextHeaders(traceparent, tracestate, traceId, spanId, parentSpanId);
	}

	/** Cleans up thread-local flow state and restores the MDC to the snapshot returned by {@link #saveMdcState()}. */
	protected void cleanupAndRestore(String[] previous) {
		if (support != null) {
			support.cleanupThreadLocals();
		}
		restore(TRACE_ID, previous[0]);
		restore(SPAN_ID, previous[1]);
		restore(PARENT_SPAN_ID, previous[2]);
		restore(TRACESTATE, previous[3]);
		restore(TRACEPARENT, previous[4]);
	}

	/**
	 * Blank-checks {@code value} and strips control characters (including CR/LF) so an attacker-supplied header cannot
	 * forge log lines or inject terminal escape sequences once the value lands in the MDC.
	 */
	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String sanitized = value.replaceAll("\\p{Cntrl}", "");
		return sanitized.isBlank() ? null : sanitized;
	}

	private static String truncate(String value, int maxLength) {
		return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	private static void putIfPresent(String key, String value) {
		if (value != null) {
			MDC.put(key, value);
		}
	}

	private static void restore(String key, String previous) {
		if (previous == null) {
			MDC.remove(key);
		} else {
			MDC.put(key, previous);
		}
	}

	protected static String[] parseTraceparent(String traceparent) {
		try {
			String[] parts = traceparent.trim().toLowerCase(Locale.ROOT).split("-", -1);
			if (parts.length >= 4 && parts[1].length() == 32 && parts[2].length() == 16) {
				return new String[] {parts[1], parts[2], null};
			}
		} catch (Exception e) {
			log.debug("Failed to parse traceparent header: {}", e.getMessage());
		}
		return new String[0];
	}

	protected static String[] parseB3Single(String b3) {
		try {
			String[] parts = b3.trim().split("-", -1);
			if (parts.length >= 2) {
				return new String[] {parts[0], parts[1], parts.length >= 4 ? parts[3] : null};
			}
		} catch (Exception e) {
			log.debug("Failed to parse B3 single header: {}", e.getMessage());
		}
		return new String[0];
	}

	protected record TraceContextHeaders(
			String traceparent, String tracestate, String traceId, String spanId, String parentSpanId) {}
}
