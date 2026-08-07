package ie.bitstep.mango.flowsink.spring;

import java.util.Objects;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.PushContextValue;
import ie.bitstep.mango.flowsink.annotations.Step;
import ie.bitstep.mango.flowsink.context.FlowContext;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.core.dispatch.AsyncDispatchBus;
import ie.bitstep.mango.flowsink.core.processor.DefaultFlowProcessor;
import ie.bitstep.mango.flowsink.core.processor.FlowProcessor;
import ie.bitstep.mango.flowsink.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.flowsink.core.sinks.FlowSinkHandler;
import ie.bitstep.mango.flowsink.spring.annotations.FlowSink;
import ie.bitstep.mango.flowsink.spring.aspect.FlowAspect;
import ie.bitstep.mango.flowsink.spring.scanner.FlowSinkScanner;
import ie.bitstep.mango.flowsink.spring.validation.HibernateEntityDetector;
import ie.bitstep.mango.flowsink.spring.validation.HibernateEntityLogLevel;
import ie.bitstep.mango.flowsink.validation.FlowAttributeValidator;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class MangoFlowSinkConfiguration {

	/** @return shared thread-local storage for active flow and step state */
	@Bean
	public FlowProcessorSupport flowProcessorSupport() {
		return new FlowProcessorSupport();
	}

	/**
	 * @param support shared thread-local state for active flows
	 * @param validator validates attributes/context values pushed via the programmatic API
	 * @return the flow context exposed to application code
	 */
	@Bean
	public FlowContext flowContext(FlowProcessorSupport support, FlowAttributeValidator validator) {
		return new FlowContext(support, validator);
	}

	/** @return registry that maps event types to their registered sink handlers */
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@Bean
	public FlowHandlerRegistry flowHandlerRegistry() {
		return new FlowHandlerRegistry();
	}

	/**
	 * @param registry the handler registry sink events are dispatched to
	 * @return the async dispatch bus; closed on context shutdown
	 */
	@Bean(destroyMethod = "close")
	public AsyncDispatchBus asyncDispatchBus(FlowHandlerRegistry registry) {
		return new AsyncDispatchBus(registry);
	}

	/** @return validator that detects Hibernate entity misuse on flow attributes */
	@Bean
	public FlowAttributeValidator flowAttributeValidator() {
		return new HibernateEntityDetector(HibernateEntityLogLevel.ERROR);
	}

	/**
	 * @param asyncDispatchBus bus used to fan-out events to registered sink handlers
	 * @param support shared thread-local state for active flows
	 * @param validator validates flow attributes before processing
	 * @return the flow processor
	 */
	@Bean
	public FlowProcessor flowProcessor(
			AsyncDispatchBus asyncDispatchBus, FlowProcessorSupport support, FlowAttributeValidator validator) {
		return new DefaultFlowProcessor(asyncDispatchBus, support, validator);
	}

	/**
	 * @param processor processes flow lifecycle events
	 * @param support shared thread-local state for active flows
	 * @return the AspectJ aspect that intercepts {@code @Flow}-annotated methods
	 */
	@Bean
	public FlowAspect flowAspect(FlowProcessor processor, FlowProcessorSupport support) {
		return new FlowAspect(processor, support);
	}

	/**
	 * {@link BeanPostProcessor} that registers plain {@link FlowSinkHandler} beans with the
	 * {@link FlowHandlerRegistry}.
	 *
	 * <p>Beans annotated with {@link FlowSink} are excluded here because {@link FlowSinkScanner} compiles and registers
	 * them via annotation-driven routing. Registering such a bean through both paths would result in every event being
	 * handled twice.
	 *
	 * @param registryProvider lazy provider for the {@link FlowHandlerRegistry}
	 * @return the post-processor
	 */
	@Bean
	public static BeanPostProcessor flowSinkHandlerRegistrar(ObjectProvider<FlowHandlerRegistry> registryProvider) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				if (bean instanceof FlowSinkHandler sink) {
					Class<?> targetType = Objects.requireNonNullElseGet(AopUtils.getTargetClass(bean), bean::getClass);
					if (!AnnotatedElementUtils.hasAnnotation(targetType, FlowSink.class)) {
						registryProvider.getObject().register(sink);
					}
				}
				return bean;
			}
		};
	}

	/**
	 * Scans the application context for {@link FlowSinkHandler} beans and registers them with the
	 * {@link FlowHandlerRegistry}.
	 *
	 * @param registryProvider lazy provider for the {@link FlowHandlerRegistry}
	 * @return the scanner
	 */
	@Bean
	public static FlowSinkScanner flowSinkScanner(ObjectProvider<FlowHandlerRegistry> registryProvider) {
		return new FlowSinkScanner(registryProvider);
	}

	/**
	 * Detects methods annotated with both {@link Flow} and {@link Step} at startup and throws
	 * {@link IllegalStateException}, failing the application context before any request is served.
	 *
	 * @return the post-processor
	 */
	@Bean
	public static BeanPostProcessor pushAttributeKeyValidator() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				Class<?> targetType = Objects.requireNonNullElseGet(AopUtils.getTargetClass(bean), bean::getClass);
				ReflectionUtils.doWithMethods(targetType, method -> {
					if (method.isBridge()) {
						return;
					}
					boolean hasFlow = AnnotatedElementUtils.hasAnnotation(method, Flow.class);
					boolean hasStep = AnnotatedElementUtils.hasAnnotation(method, Step.class);
					if (!hasFlow && !hasStep) {
						return;
					}
					java.lang.annotation.Annotation[][] paramAnns = method.getParameterAnnotations();
					for (java.lang.annotation.Annotation[] paramAnn : paramAnns) {
						for (java.lang.annotation.Annotation ann : paramAnn) {
							if (ann instanceof PushAttribute pa && pa.value().isBlank()) {
								throw new IllegalStateException(targetType.getName() + "." + method.getName()
										+ "() has a @PushAttribute with a blank key"
										+ " — provide a non-blank key");
							}
							if (ann instanceof PushContextValue pcv
									&& pcv.value().isBlank()) {
								throw new IllegalStateException(targetType.getName() + "." + method.getName()
										+ "() has a @PushContextValue with a blank key"
										+ " — provide a non-blank key");
							}
						}
					}
				});
				return bean;
			}
		};
	}

	@Bean
	public static BeanPostProcessor flowAnnotationConflictDetector() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				Class<?> targetType = Objects.requireNonNullElseGet(AopUtils.getTargetClass(bean), bean::getClass);
				ReflectionUtils.doWithMethods(targetType, method -> {
					if (method.isBridge()) {
						return;
					}
					boolean hasFlow = AnnotatedElementUtils.hasAnnotation(method, Flow.class);
					boolean hasStep = AnnotatedElementUtils.hasAnnotation(method, Step.class);
					if (hasFlow && hasStep) {
						throw new IllegalStateException(
								targetType.getName() + "." + method.getName() + "() is annotated with both"
										+ " @Flow and @Step — use one or the other, they cannot coexist on"
										+ " the same method");
					}
				});
				return bean;
			}
		};
	}
}
