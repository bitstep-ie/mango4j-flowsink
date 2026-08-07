package ie.bitstep.mango.instrument.spring.scanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnAllLifecycles;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycle;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycles;
import ie.bitstep.mango.instrument.annotations.OnFlowNotMatched;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowScopes;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.OnFlowSuccess;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.annotations.PullAllContextValues;
import ie.bitstep.mango.instrument.annotations.PullAttribute;
import ie.bitstep.mango.instrument.annotations.PullContextValue;
import ie.bitstep.mango.instrument.annotations.RequiredAttributes;
import ie.bitstep.mango.instrument.annotations.RequiredEventContext;
import ie.bitstep.mango.instrument.core.processor.DefaultFlowProcessor;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.core.sinks.FlowSinkHandler;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

public class FlowSinkScanner implements BeanPostProcessor {
	private static final Logger log = LoggerFactory.getLogger(FlowSinkScanner.class);

	private final java.util.function.Supplier<FlowHandlerRegistry> registrySupplier;

	public FlowSinkScanner(FlowHandlerRegistry registry) {
		Objects.requireNonNull(registry, "registry");
		this.registrySupplier = () -> registry;
	}

	public FlowSinkScanner(ObjectProvider<FlowHandlerRegistry> registryProvider) {
		Objects.requireNonNull(registryProvider, "registryProvider");
		this.registrySupplier = registryProvider::getObject;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		Class<?> targetType = Objects.requireNonNullElseGet(AopUtils.getTargetClass(bean), bean::getClass);
		if (!AnnotatedElementUtils.hasAnnotation(targetType, FlowSink.class)) {
			return bean;
		}

		CompiledSink compiledSink = compileSink(bean, targetType);
		if (compiledSink.handlers.isEmpty() && compiledSink.fallbacks.isEmpty()) {
			log.warn(
					"FlowSink {} has no invocable handler methods and will not receive events. "
							+ "Check that handler methods are public and not hidden by a proxy.",
					targetType.getSimpleName());
			return bean;
		}

		registrySupplier.get().register(compiledSink);
		log.info(
				"Registered FlowSink: {} (handlers={}, fallbacks={})",
				targetType.getSimpleName(),
				compiledSink.handlers.size(),
				compiledSink.fallbacks.size());
		return bean;
	}

	private CompiledSink compileSink(Object bean, Class<?> type) {
		List<String> classScopes = extractScopes(type);
		Set<OnFlowLifecycle.Lifecycle> classLifecycles = extractLifecycles(type);
		List<CompiledHandler> handlers = new ArrayList<>();
		List<CompiledHandler> fallbacks = new ArrayList<>();

		ReflectionUtils.doWithMethods(
				type,
				method -> {
					CompiledHandler compiledHandler = compileHandler(bean, method, classScopes, classLifecycles);
					if (compiledHandler == null) {
						return;
					}
					if (compiledHandler.fallback) {
						fallbacks.add(compiledHandler);
					} else {
						handlers.add(compiledHandler);
					}
				},
				method -> !method.isBridge());

		return new CompiledSink(handlers, fallbacks, type.getSimpleName());
	}

	private static List<String> extractScopes(AnnotatedElement element) {
		List<String> scopes = new ArrayList<>();
		for (OnFlowScope scope :
				AnnotatedElementUtils.getMergedRepeatableAnnotations(element, OnFlowScope.class, OnFlowScopes.class)) {
			scopes.add(nullToEmpty(scope.value()));
		}
		return scopes;
	}

	private static Set<OnFlowLifecycle.Lifecycle> extractLifecycles(AnnotatedElement element) {
		if (AnnotatedElementUtils.hasAnnotation(element, OnAllLifecycles.class)) {
			return EnumSet.allOf(OnFlowLifecycle.Lifecycle.class);
		}
		EnumSet<OnFlowLifecycle.Lifecycle> lifecycles = EnumSet.noneOf(OnFlowLifecycle.Lifecycle.class);
		for (OnFlowLifecycle lifecycle : AnnotatedElementUtils.getMergedRepeatableAnnotations(
				element, OnFlowLifecycle.class, OnFlowLifecycles.class)) {
			lifecycles.add(lifecycle.value());
		}
		return lifecycles;
	}

	private CompiledHandler compileHandler(
			Object bean, Method method, List<String> classScopes, Set<OnFlowLifecycle.Lifecycle> classLifecycles) {
		Method bridged = BridgeMethodResolver.findBridgedMethod(method);
		Method invocable;
		try {
			invocable = AopUtils.selectInvocableMethod(bridged, bean.getClass());
		} catch (IllegalStateException ex) {
			if (log.isDebugEnabled()) {
				log.debug("Skipping FlowSink method {} due to proxy mismatch: {}", method, ex.getMessage());
			}
			return null;
		}

		boolean started = AnnotatedElementUtils.hasAnnotation(bridged, OnFlowStarted.class);
		boolean completed = AnnotatedElementUtils.hasAnnotation(bridged, OnFlowCompleted.class);
		boolean failure = AnnotatedElementUtils.hasAnnotation(bridged, OnFlowFailure.class);
		boolean success = AnnotatedElementUtils.hasAnnotation(bridged, OnFlowSuccess.class);
		boolean fallback = AnnotatedElementUtils.hasAnnotation(bridged, OnFlowNotMatched.class);
		Set<OnFlowLifecycle.Lifecycle> methodLifecycles = extractLifecycles(bridged);
		boolean lifecycleDeclared =
				started || completed || failure || success || !methodLifecycles.isEmpty() || fallback;
		if (!lifecycleDeclared) {
			return null;
		}

		// Fallback handlers are not subject to class-level scope or lifecycle restrictions —
		// they fire when no regular handler matched, regardless of what filtered those handlers out.
		// A fallback with no method-level lifecycle annotation is lifecycle-agnostic (allOf).
		EnumSet<OnFlowLifecycle.Lifecycle> lifecycles;
		if (fallback) {
			lifecycles = methodLifecycles.isEmpty()
					? EnumSet.allOf(OnFlowLifecycle.Lifecycle.class)
					: EnumSet.copyOf(methodLifecycles);
		} else {
			lifecycles = buildMethodLifecycles(started, completed, success, failure, methodLifecycles, classLifecycles);
		}

		ReflectionUtils.makeAccessible(invocable);
		Parameter[] parameters = invocable.getParameters();
		Annotation[][] parameterAnnotations = invocable.getParameterAnnotations();
		List<Function<FlowEvent, Object>> bindings = new ArrayList<>(parameters.length);
		boolean allowThrowable = allowsThrowable(completed, failure, methodLifecycles);
		for (int index = 0; index < parameters.length; index++) {
			Function<FlowEvent, Object> binding =
					buildParamBinding(parameters[index], parameterAnnotations[index], allowThrowable);
			if (binding == null) {
				throw new IllegalStateException(String.format(
						"FlowSink %s.%s() parameter '%s' (type %s) cannot be bound. "
								+ "Annotate it with @PullAttribute, @PullContextValue, @PullAllAttributes, "
								+ "@PullAllContextValues, or use FlowEvent / Throwable directly.",
						invocable.getDeclaringClass().getSimpleName(),
						invocable.getName(),
						parameters[index].getName(),
						parameters[index].getType().getSimpleName()));
			}
			bindings.add(binding);
		}

		return new CompiledHandler(
				bean,
				invocable,
				fallback ? List.of() : classScopes,
				extractScopes(bridged),
				lifecycles,
				AnnotatedElementUtils.findMergedAnnotation(bridged, RequiredAttributes.class),
				AnnotatedElementUtils.findMergedAnnotation(bridged, RequiredEventContext.class),
				bindings,
				fallback,
				failure);
	}

	private static EnumSet<OnFlowLifecycle.Lifecycle> buildMethodLifecycles(
			boolean started,
			boolean completed,
			boolean success,
			boolean failure,
			Set<OnFlowLifecycle.Lifecycle> methodLifecycles,
			Set<OnFlowLifecycle.Lifecycle> classLifecycles) {
		EnumSet<OnFlowLifecycle.Lifecycle> lifecycles = EnumSet.noneOf(OnFlowLifecycle.Lifecycle.class);
		if (started) {
			lifecycles.add(OnFlowLifecycle.Lifecycle.STARTED);
		}
		if (completed) {
			lifecycles.add(OnFlowLifecycle.Lifecycle.COMPLETED);
			lifecycles.add(OnFlowLifecycle.Lifecycle.FAILED);
		}
		if (success) {
			lifecycles.add(OnFlowLifecycle.Lifecycle.COMPLETED);
		}
		if (failure) {
			lifecycles.add(OnFlowLifecycle.Lifecycle.FAILED);
		}
		lifecycles.addAll(methodLifecycles);
		if (!classLifecycles.isEmpty()) {
			lifecycles.retainAll(classLifecycles);
		}
		return lifecycles;
	}

	private static boolean allowsThrowable(
			boolean completed, boolean failure, Set<OnFlowLifecycle.Lifecycle> methodLifecycles) {
		return completed || failure || methodLifecycles.contains(OnFlowLifecycle.Lifecycle.FAILED);
	}

	private static Function<FlowEvent, Object> buildParamBinding(
			Parameter parameter, Annotation[] annotations, boolean allowThrowable) {
		Class<?> type = parameter.getType();
		if (FlowEvent.class.isAssignableFrom(type)) {
			return event -> event;
		}
		if (allowThrowable && Throwable.class.isAssignableFrom(type)) {
			return bindThrowable(annotations);
		}
		for (Annotation annotation : annotations) {
			Function<FlowEvent, Object> binding = bindAnnotation(annotation, type);
			if (binding != null) {
				return binding;
			}
		}
		return null;
	}

	private static Function<FlowEvent, Object> bindThrowable(Annotation[] annotations) {
		boolean root = false;
		for (Annotation annotation : annotations) {
			if (annotation instanceof FlowException flowException
					&& flowException.value() == FlowException.Source.ROOT_CAUSE) {
				root = true;
			}
		}
		final boolean rootCause = root;
		return event -> chooseThrowable(event, rootCause);
	}

	private static Function<FlowEvent, Object> bindAnnotation(Annotation annotation, Class<?> type) {
		if (annotation instanceof PullAllAttributes) {
			return event -> Collections.unmodifiableMap(
					new LinkedHashMap<>(event.attributes().map()));
		}
		if (annotation instanceof PullAllContextValues) {
			return event -> Collections.unmodifiableMap(new LinkedHashMap<>(event.eventContext()));
		}
		if (annotation instanceof PullAttribute pullAttribute) {
			return event -> coerce(event.attributes().map().get(pullAttribute.value()), type);
		}
		if (annotation instanceof PullContextValue pullContextValue) {
			return event -> coerce(event.eventContext().get(pullContextValue.value()), type);
		}
		return null;
	}

	private record CompiledSink(List<CompiledHandler> handlers, List<CompiledHandler> fallbacks, String typeName)
			implements FlowSinkHandler {
		@Override
		public void handle(FlowEvent event) {
			boolean anyMatched = false;
			boolean failed = DefaultFlowProcessor.FAILED.equals(
					String.valueOf(event.eventContext().get(DefaultFlowProcessor.LIFECYCLE)));

			if (failed) {
				anyMatched = dispatchFailureHandlers(event);
			}

			for (CompiledHandler handler : handlers) {
				if (failed && handler.flowFailure) {
					continue;
				}
				if (handler.matches(event)) {
					handler.invoke(event);
					anyMatched = true;
				}
			}

			if (!anyMatched) {
				for (CompiledHandler fallback : fallbacks) {
					if (fallback.matches(event)) {
						fallback.invoke(event);
					}
				}
			}
		}

		@Override
		public String sinkName() {
			return typeName;
		}

		private boolean dispatchFailureHandlers(FlowEvent event) {
			boolean matched = false;
			for (CompiledHandler handler : handlers) {
				if (handler.flowFailure && handler.matches(event)) {
					handler.invoke(event);
					matched = true;
				}
			}
			return matched;
		}
	}

	@SuppressWarnings("java:S107")
	private static final class CompiledHandler {
		private final Object bean;
		private final Method method;
		private final List<String> classScopes;
		private final List<String> methodScopes;
		private final EnumSet<OnFlowLifecycle.Lifecycle> lifecycles;
		private final RequiredAttributes requiredAttributes;
		private final RequiredEventContext requiredEventContext;
		private final List<Function<FlowEvent, Object>> bindings;
		private final boolean fallback;
		private final boolean flowFailure;

		private CompiledHandler(
				Object bean,
				Method method,
				List<String> classScopes,
				List<String> methodScopes,
				EnumSet<OnFlowLifecycle.Lifecycle> lifecycles,
				RequiredAttributes requiredAttributes,
				RequiredEventContext requiredEventContext,
				List<Function<FlowEvent, Object>> bindings,
				boolean fallback,
				boolean flowFailure) {
			this.bean = bean;
			this.method = method;
			this.classScopes = classScopes;
			this.methodScopes = methodScopes;
			this.lifecycles = lifecycles;
			this.requiredAttributes = requiredAttributes;
			this.requiredEventContext = requiredEventContext;
			this.bindings = bindings;
			this.fallback = fallback;
			this.flowFailure = flowFailure;
		}

		private boolean matches(FlowEvent event) {
			String lifecycle = String.valueOf(event.eventContext().get(DefaultFlowProcessor.LIFECYCLE));
			OnFlowLifecycle.Lifecycle resolved = resolveLifecycle(lifecycle);
			if (resolved == null || !lifecycles.contains(resolved)) {
				return false;
			}
			String name = event.name();
			if (!scopeMatches(classScopes, name) || !scopeMatches(methodScopes, name)) {
				return false;
			}
			if (!attributesPresent(event, requiredAttributes == null ? null : requiredAttributes.value())) {
				return false;
			}
			return contextPresent(event, requiredEventContext == null ? null : requiredEventContext.value());
		}

		private static OnFlowLifecycle.Lifecycle resolveLifecycle(String lifecycle) {
			if (DefaultFlowProcessor.STARTED.equals(lifecycle)) {
				return OnFlowLifecycle.Lifecycle.STARTED;
			}
			if (DefaultFlowProcessor.COMPLETED.equals(lifecycle)) {
				return OnFlowLifecycle.Lifecycle.COMPLETED;
			}
			if (DefaultFlowProcessor.FAILED.equals(lifecycle)) {
				return OnFlowLifecycle.Lifecycle.FAILED;
			}
			return null;
		}

		private void invoke(FlowEvent event) {
			Object[] args = new Object[bindings.size()];
			for (int index = 0; index < bindings.size(); index++) {
				args[index] = bindings.get(index).apply(event);
			}
			ReflectionUtils.invokeMethod(method, bean, args);
		}

		private static boolean attributesPresent(FlowEvent event, String[] required) {
			if (required == null || required.length == 0) {
				return true;
			}
			Map<String, Object> attributes = event.attributes().map();
			for (String key : required) {
				if (key == null || key.isBlank() || !attributes.containsKey(key)) {
					return false;
				}
			}
			return true;
		}

		private static boolean contextPresent(FlowEvent event, String[] required) {
			if (required == null || required.length == 0) {
				return true;
			}
			Map<String, Object> context = event.eventContext();
			for (String key : required) {
				if (key == null || key.isBlank() || !context.containsKey(key)) {
					return false;
				}
			}
			return true;
		}

		private static boolean scopeMatches(List<String> scopes, String name) {
			if (scopes == null || scopes.isEmpty()) {
				return true;
			}
			String candidate = name == null ? "" : name;
			for (String scope : scopes) {
				if (scopeEntryMatches(scope, candidate)) {
					return true;
				}
			}
			return false;
		}

		private static boolean scopeEntryMatches(String scope, String candidate) {
			if (scope == null) {
				return false;
			}
			String trimmed = scope.trim();
			if (trimmed.isEmpty()) {
				return true;
			}
			if (trimmed.endsWith(".")) {
				return candidate.startsWith(trimmed);
			}
			return candidate.equals(trimmed) || candidate.startsWith(trimmed + ".");
		}
	}

	private static Throwable chooseThrowable(FlowEvent event, boolean rootCauseOnly) {
		Throwable throwable = event.throwable();
		if (!rootCauseOnly || throwable == null) {
			return throwable;
		}
		while (throwable.getCause() != null) {
			throwable = throwable.getCause();
		}
		return throwable;
	}

	private static Object coerce(Object value, Class<?> targetType) {
		if (value == null) {
			return null;
		}
		if (targetType.isInstance(value)) {
			return value;
		}
		if (targetType == String.class) {
			return String.valueOf(value);
		}
		return value;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
