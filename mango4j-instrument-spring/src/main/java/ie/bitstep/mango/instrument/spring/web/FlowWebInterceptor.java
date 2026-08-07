package ie.bitstep.mango.instrument.spring.web;

import java.lang.reflect.Method;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.Kind;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowMeta;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;

/**
 * A Spring MVC {@link HandlerInterceptor} that integrates {@link Flow}-annotated controller methods with the
 * mango4j-instrument flow lifecycle.
 *
 * <p>When a request arrives at a controller method that carries {@code @Flow}:
 *
 * <ol>
 *   <li>{@code preHandle} — captures the resolved flow name and HTTP metadata (method, URI, URL mapping), stores them
 *       for the aspect, and marks the request as a web-root flow.
 *   <li>{@code afterCompletion} — calls {@link FlowProcessor#onFlowCompleted} (or {@link FlowProcessor#onFlowFailed}
 *       when an exception was raised or the response carries a 4xx/5xx status) and records the HTTP status code in the
 *       event context.
 * </ol>
 *
 * <p>Non-{@code @Flow} handler methods pass through without any instrumentation.
 *
 * <p>Because this interceptor calls {@link FlowProcessor#onFlowStarted} before the AOP {@code FlowAspect} intercepts
 * the controller method, it sets a web-flow marker on {@link ie.bitstep.mango.instrument.core.FlowProcessorSupport} so
 * the aspect can detect the already-started flow. The aspect consumes the marker, merges any {@code @PushAttribute} /
 * {@code @PushContextValue} parameters into the existing event, and proceeds without emitting its own lifecycle calls.
 * Nested {@code @Flow} and {@code @Step} service calls within the controller body run normally.
 *
 * @see FlowProcessor
 * @see ie.bitstep.mango.instrument.spring.aspect.FlowAspect
 */
public class FlowWebInterceptor implements HandlerInterceptor {

	static final String CTX_HTTP_KEY = "http";
	static final String CTX_HTTP_METHOD = "method";
	static final String CTX_HTTP_URI = "uri";
	static final String CTX_HTTP_MAPPING = "mapping";
	static final String CTX_HTTP_STATUS = "status";
	static final int MAX_HTTP_VALUE_LENGTH = 2048;

	private static final Logger log = LoggerFactory.getLogger(FlowWebInterceptor.class);

	/**
	 * Request attribute used to pass the resolved {@link Flow} name from {@code preHandle} through to
	 * {@code afterCompletion} without extra state.
	 */
	private static final String ATTR_FLOW_NAME = FlowWebInterceptor.class.getName() + ".flowName";

	private final FlowProcessor processor;
	private final FlowProcessorSupport support;

	public FlowWebInterceptor(FlowProcessor processor, FlowProcessorSupport support) {
		this.processor = processor;
		this.support = support;
	}

	// -------------------------------------------------------------------------
	// HandlerInterceptor
	// -------------------------------------------------------------------------

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		support.cleanupThreadLocals(); // ensure a clean slate for every inbound request

		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}

		Method method = handlerMethod.getMethod();
		Flow flow = AnnotatedElementUtils.findMergedAnnotation(method, Flow.class);
		if (flow == null) {
			return true;
		}

		String flowName = resolveFlowName(flow, method);
		request.setAttribute(ATTR_FLOW_NAME, flowName);

		FlowMeta meta = buildStartMeta(method);
		Map<String, Object> httpCtx = buildHttpContext(request);
		processor.onFlowStarted(flowName, Map.of(), httpCtx, meta);
		support.markWebFlow();

		return true;
	}

	@Override
	public void afterCompletion(
			HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {

		String flowName = (String) request.getAttribute(ATTR_FLOW_NAME);
		if (flowName == null) {
			return; // handler was not @Flow-annotated
		}

		if (support.currentContext() == null) {
			log.warn(
					"FlowWebInterceptor: no active flow context on this thread for '{}' — "
							+ "completed/failed events will not be dispatched. "
							+ "Async controller methods are not supported; use @Flow on service-layer methods instead.",
					flowName);
			support.cleanupThreadLocals();
			request.removeAttribute(ATTR_FLOW_NAME);
			return;
		}

		try {
			int statusCode = response.getStatus();
			boolean clientOrServerError = statusCode >= 400;

			Map<String, Object> httpCtx = Map.of(CTX_HTTP_STATUS, String.valueOf(statusCode));

			if (ex != null || clientOrServerError) {
				FlowMeta meta = FlowMeta.builder()
						.status("ERROR", ex != null ? null : String.valueOf(statusCode))
						.build();
				processor.onFlowFailed(flowName, ex, Map.of(), httpCtx, meta);
			} else {
				FlowMeta meta = FlowMeta.builder().status("OK", null).build();
				processor.onFlowCompleted(flowName, Map.of(), httpCtx, meta);
			}
		} catch (Exception completionEx) {
			log.error("FlowWebInterceptor.afterCompletion failed for flow '{}'", flowName, completionEx);
		} finally {
			support.cleanupThreadLocals();
			request.removeAttribute(ATTR_FLOW_NAME);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static String resolveFlowName(Flow flow, Method method) {
		if (!flow.name().isBlank()) {
			return flow.name();
		}
		if (!flow.value().isBlank()) {
			return flow.value();
		}
		return method.getDeclaringClass().getSimpleName() + "." + method.getName();
	}

	private static FlowMeta buildStartMeta(Method method) {
		Kind kindAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, Kind.class);
		FlowMeta.Builder builder = FlowMeta.builder();
		if (kindAnnotation != null) {
			builder.kind(kindAnnotation.value().name());
		} else {
			builder.kind("SERVER");
		}
		return builder.build();
	}

	private static Map<String, Object> buildHttpContext(HttpServletRequest request) {
		String mapping = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		String requestUri = truncate(request.getRequestURI(), MAX_HTTP_VALUE_LENGTH);
		String resolvedMapping = truncate(mapping != null ? mapping : request.getRequestURI(), MAX_HTTP_VALUE_LENGTH);
		return Map.of(
				CTX_HTTP_KEY,
				Map.of(
						CTX_HTTP_METHOD, request.getMethod(),
						CTX_HTTP_URI, requestUri,
						CTX_HTTP_MAPPING, resolvedMapping));
	}

	private static String truncate(String value, int maxLength) {
		return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
	}
}
