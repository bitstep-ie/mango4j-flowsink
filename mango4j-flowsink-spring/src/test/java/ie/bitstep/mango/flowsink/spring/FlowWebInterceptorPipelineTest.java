package ie.bitstep.mango.flowsink.spring;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.PushContextValue;
import ie.bitstep.mango.flowsink.annotations.Step;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.core.processor.FlowMeta;
import ie.bitstep.mango.flowsink.core.processor.FlowProcessor;
import ie.bitstep.mango.flowsink.model.FlowEvent;
import ie.bitstep.mango.flowsink.spring.aspect.FlowAspect;
import ie.bitstep.mango.flowsink.spring.web.FlowWebInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the cooperation between {@link FlowWebInterceptor} and {@link FlowAspect} when the interceptor is the root
 * entry point.
 *
 * <p>The interceptor calls {@code onFlowStarted} in {@code preHandle} and sets the web-flow marker so that the aspect
 * skips its own lifecycle calls for the controller method. Nested {@code @Step} and {@code @Flow} invocations inside
 * the controller body must still fire normally.
 */
class FlowWebInterceptorPipelineTest {

	private final TrackingSupport support = new TrackingSupport();
	private final CapturingProcessor processor = new CapturingProcessor(support);
	private final FlowWebInterceptor interceptor = new FlowWebInterceptor(processor, support);
	private final FlowAspect aspect = new FlowAspect(processor, support);

	@AfterEach
	void cleanup() {
		support.cleanupThreadLocals();
	}

	// -------------------------------------------------------------------------
	// @Step inside controller body
	// -------------------------------------------------------------------------

	@Test
	void step_inside_web_flow_body_is_embedded_in_completed_event() throws Throwable {
		MockHttpServletRequest request = requestFor("GET", "/orders");
		MockHttpServletResponse response = responseFor(200);
		HandlerMethod handler = handlerMethod("getOrders"); // @Flow("orders.get")

		interceptor.preHandle(request, response, handler);

		Method controllerMethod = SampleController.class.getDeclaredMethod("getOrders");
		Method stepMethod = SampleService.class.getDeclaredMethod("validateOrder", String.class);
		aspect.aroundFlow(joinPoint(controllerMethod, new Object[0], () -> {
			aspect.aroundStep(joinPoint(stepMethod, new Object[] {"ORD-42"}, () -> null));
			return null;
		}));
		FlowEvent rootFlow = support.currentContext();

		interceptor.afterCompletion(request, response, handler, null);

		assertThat(processor.started)
				.hasSize(1)
				.first()
				.extracting(FlowEvent::name)
				.isEqualTo("orders.get");
		assertThat(processor.completed)
				.hasSize(1)
				.first()
				.extracting(FlowEvent::name)
				.isEqualTo("orders.get");
		assertThat(processor.failed).isEmpty();
		assertThat(rootFlow.events()).hasSize(1);
		assertThat(rootFlow.events().get(0).name()).isEqualTo("validate-order");
		assertThat(rootFlow.events().get(0).attributes().map()).containsEntry("order.id", "ORD-42");
	}

	@Test
	void controller_pushattribute_params_are_merged_into_root_flow() throws Throwable {
		MockHttpServletRequest request = requestFor("POST", "/checkout");
		MockHttpServletResponse response = responseFor(200);
		HandlerMethod handler = handlerMethod("checkout"); // @Flow("checkout.submit")

		interceptor.preHandle(request, response, handler);

		Method controllerMethod = SampleController.class.getDeclaredMethod("checkout", String.class, int.class);
		aspect.aroundFlow(joinPoint(controllerMethod, new Object[] {"alice", 3}, () -> null));
		FlowEvent rootFlow = support.currentContext();

		interceptor.afterCompletion(request, response, handler, null);

		assertThat(rootFlow.attributes().map()).containsEntry("user.id", "alice");
		assertThat(rootFlow.eventContext()).containsEntry("cart.size", 3);
		assertThat(processor.started).hasSize(1);
		assertThat(processor.completed).hasSize(1);
	}

	// -------------------------------------------------------------------------
	// Nested @Flow inside controller body
	// -------------------------------------------------------------------------

	@Test
	void nested_flow_inside_web_flow_body_dispatches_its_own_lifecycle_events() throws Throwable {
		MockHttpServletRequest request = requestFor("POST", "/checkout");
		MockHttpServletResponse response = responseFor(200);
		HandlerMethod handler = handlerMethod("checkout");

		interceptor.preHandle(request, response, handler);

		Method controllerMethod = SampleController.class.getDeclaredMethod("checkout", String.class, int.class);
		Method nestedFlowMethod = SampleService.class.getDeclaredMethod("processPayment", String.class);
		aspect.aroundFlow(joinPoint(controllerMethod, new Object[] {"bob", 1}, () -> {
			// nested @Flow — marker already consumed, so aspect runs full lifecycle
			aspect.aroundFlow(joinPoint(nestedFlowMethod, new Object[] {"card-1"}, () -> null));
			return null;
		}));

		interceptor.afterCompletion(request, response, handler, null);

		// root: 1 started (interceptor) + 1 completed (interceptor)
		// nested: 1 started (aspect) + 1 completed (aspect)
		assertThat(processor.started).hasSize(2);
		assertThat(processor.completed).hasSize(2);
		assertThat(processor.failed).isEmpty();
		List<String> startedNames =
				processor.started.stream().map(FlowEvent::name).toList();
		assertThat(startedNames).containsExactlyInAnyOrder("checkout.submit", "payment.process");
		List<String> completedNames =
				processor.completed.stream().map(FlowEvent::name).toList();
		assertThat(completedNames).containsExactlyInAnyOrder("checkout.submit", "payment.process");
	}

	@Test
	void nested_flow_stack_is_restored_to_root_after_nested_completes() throws Throwable {
		MockHttpServletRequest request = requestFor("POST", "/checkout");
		MockHttpServletResponse response = responseFor(200);
		HandlerMethod handler = handlerMethod("checkout");

		interceptor.preHandle(request, response, handler);
		FlowEvent rootFlow = support.currentContext();

		Method controllerMethod = SampleController.class.getDeclaredMethod("checkout", String.class, int.class);
		Method nestedFlowMethod = SampleService.class.getDeclaredMethod("processPayment", String.class);
		aspect.aroundFlow(joinPoint(controllerMethod, new Object[] {"bob", 1}, () -> {
			aspect.aroundFlow(joinPoint(nestedFlowMethod, new Object[] {"card-1"}, () -> null));
			// after nested flow completes, current context must be root again
			assertThat(support.currentContext()).isSameAs(rootFlow);
			return null;
		}));

		interceptor.afterCompletion(request, response, handler, null);
		assertThat(support.currentContext()).isNull();
	}

	// -------------------------------------------------------------------------
	// Failure path
	// -------------------------------------------------------------------------

	@Test
	void controller_exception_results_in_single_failed_event_from_interceptor() throws Throwable {
		MockHttpServletRequest request = requestFor("GET", "/orders");
		MockHttpServletResponse response = responseFor(500);
		HandlerMethod handler = handlerMethod("getOrders");
		RuntimeException boom = new RuntimeException("handler exploded");

		interceptor.preHandle(request, response, handler);

		Method controllerMethod = SampleController.class.getDeclaredMethod("getOrders");
		assertThatThrownBy(() -> aspect.aroundFlow(joinPoint(controllerMethod, new Object[0], () -> {
					throw boom;
				})))
				.isSameAs(boom);

		interceptor.afterCompletion(request, response, handler, boom);

		assertThat(processor.started).hasSize(1);
		assertThat(processor.completed).isEmpty();
		assertThat(processor.failed)
				.hasSize(1)
				.first()
				.extracting(FlowEvent::name)
				.isEqualTo("orders.get");
	}

	@Test
	void step_failure_propagates_to_interceptor_without_extra_lifecycle_events() throws Throwable {
		MockHttpServletRequest request = requestFor("GET", "/orders");
		MockHttpServletResponse response = responseFor(500);
		HandlerMethod handler = handlerMethod("getOrders");
		RuntimeException stepBoom = new RuntimeException("step failed");

		interceptor.preHandle(request, response, handler);
		FlowEvent rootFlow = support.currentContext();

		Method controllerMethod = SampleController.class.getDeclaredMethod("getOrders");
		Method stepMethod = SampleService.class.getDeclaredMethod("validateOrder", String.class);
		assertThatThrownBy(() -> aspect.aroundFlow(joinPoint(controllerMethod, new Object[0], () -> {
					assertThatThrownBy(() -> aspect.aroundStep(joinPoint(stepMethod, new Object[] {"ORD-0"}, () -> {
								throw stepBoom;
							})))
							.isSameAs(stepBoom);
					throw stepBoom; // controller propagates step failure
				})))
				.isSameAs(stepBoom);

		interceptor.afterCompletion(request, response, handler, stepBoom);

		assertThat(processor.started).hasSize(1);
		assertThat(processor.completed).isEmpty();
		assertThat(processor.failed).hasSize(1);
		assertThat(rootFlow.events()).hasSize(1);
		assertThat(rootFlow.events().get(0).attributes().map()).containsKey("error");
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static MockHttpServletRequest requestFor(String method, String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod(method);
		request.setRequestURI(uri);
		return request;
	}

	private static MockHttpServletResponse responseFor(int status) {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(status);
		return response;
	}

	private static HandlerMethod handlerMethod(String methodName) throws Exception {
		SampleController controller = new SampleController();
		for (Method method : SampleController.class.getDeclaredMethods()) {
			if (method.getName().equals(methodName)) {
				return new HandlerMethod(controller, method);
			}
		}
		throw new NoSuchMethodException("No method '" + methodName + "' in SampleController");
	}

	private static ProceedingJoinPoint joinPoint(Method method, Object[] args, ProceedCallback callback) {
		MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
				MethodSignature.class.getClassLoader(),
				new Class<?>[] {MethodSignature.class},
				methodSignatureHandler(method));
		return (ProceedingJoinPoint) Proxy.newProxyInstance(
				ProceedingJoinPoint.class.getClassLoader(),
				new Class<?>[] {ProceedingJoinPoint.class},
				joinPointHandler(signature, args, callback));
	}

	private static InvocationHandler methodSignatureHandler(Method method) {
		return (proxy, invoked, args) -> switch (invoked.getName()) {
			case "getMethod" -> method;
			case "getReturnType" -> method.getReturnType();
			case "toShortString" -> method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(..)";
			case "toLongString", "toString" -> method.toString();
			case "getName" -> method.getName();
			case "getDeclaringType" -> method.getDeclaringClass();
			case "getDeclaringTypeName" -> method.getDeclaringClass().getName();
			case "getParameterTypes" -> method.getParameterTypes();
			case "getParameterNames" -> new String[method.getParameterCount()];
			case "getExceptionTypes" -> method.getExceptionTypes();
			case "getModifiers" -> method.getModifiers();
			default -> defaultValue(invoked.getReturnType());
		};
	}

	private static InvocationHandler joinPointHandler(
			MethodSignature signature, Object[] args, ProceedCallback callback) {
		return (proxy, invoked, ignored) -> switch (invoked.getName()) {
			case "getArgs" -> args;
			case "getSignature" -> signature;
			case "toShortString" -> signature.toShortString();
			case "toLongString", "toString" -> signature.toString();
			case "getKind" -> "method-execution";
			case "proceed" -> callback.proceed();
			default -> defaultValue(invoked.getReturnType());
		};
	}

	private static Object defaultValue(Class<?> returnType) {
		if (!returnType.isPrimitive()) return null;
		if (returnType == boolean.class) return false;
		if (returnType == char.class) return '\0';
		return 0;
	}

	@FunctionalInterface
	interface ProceedCallback {
		Object proceed() throws Throwable;
	}

	// -------------------------------------------------------------------------
	// Supporting types
	// -------------------------------------------------------------------------

	static class SampleController {
		@Flow(name = "orders.get")
		void getOrders() {}

		@Flow(name = "checkout.submit")
		void checkout(@PushAttribute("user.id") String userId, @PushContextValue("cart.size") int items) {}
	}

	static class SampleService {
		@Step(name = "validate-order")
		void validateOrder(@PushAttribute("order.id") String orderId) {}

		@Flow(name = "payment.process")
		void processPayment(@PushAttribute("card.id") String cardId) {}
	}

	/**
	 * Processor stub that captures the live {@link FlowEvent} objects (not just call metadata) so tests can inspect
	 * embedded step events after the flow completes.
	 */
	static class CapturingProcessor implements FlowProcessor {
		private final FlowProcessorSupport support;
		final List<FlowEvent> started = new ArrayList<>();
		final List<FlowEvent> completed = new ArrayList<>();
		final List<FlowEvent> failed = new ArrayList<>();

		CapturingProcessor(FlowProcessorSupport support) {
			this.support = support;
		}

		@Override
		public void onFlowStarted(String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
			FlowEvent event = FlowEvent.builder().name(name).build();
			event.attributes().map().putAll(attrs);
			event.eventContext().putAll(ctx);
			support.push(event);
			started.add(event);
		}

		@Override
		public void onFlowCompleted(String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
			FlowEvent event = support.currentContext();
			if (event != null) {
				event.attributes().map().putAll(attrs);
				event.eventContext().putAll(ctx);
				support.pop(event);
				completed.add(event);
			}
		}

		@Override
		public void onFlowFailed(
				String name, Throwable error, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
			FlowEvent event = support.currentContext();
			if (event != null) {
				event.attributes().map().putAll(attrs);
				event.eventContext().putAll(ctx);
				event.setThrowable(error);
				support.pop(event);
				failed.add(event);
			}
		}
	}

	static class TrackingSupport extends FlowProcessorSupport {
		int cleanupCalls;

		@Override
		public void cleanupThreadLocals() {
			cleanupCalls++;
			super.cleanupThreadLocals();
		}
	}
}
