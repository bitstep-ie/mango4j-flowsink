package ie.bitstep.mango.flowsink.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.core.processor.FlowProcessor;
import ie.bitstep.mango.flowsink.spring.web.FlowWebInterceptor;

/**
 * Registers the {@link FlowWebInterceptor} as a Spring MVC interceptor so that {@code @Flow}-annotated controller
 * handler methods are automatically instrumented without requiring any extra configuration from the application.
 *
 * <p>This configuration is imported by {@link MangoFlowSinkImportSelector} when {@code jakarta.servlet.Filter}
 * is on the classpath, and by {@code MangoServletFlowAutoConfiguration} for Spring Boot applications.
 */
@Configuration(proxyBeanMethods = false)
public class MangoServletFlowConfiguration {

	/**
	 * The interceptor is registered at the highest available precedence so that the flow lifecycle is established
	 * before any application-level interceptors run.
	 */
	static final int INTERCEPTOR_ORDER = Integer.MIN_VALUE + 100;

	/**
	 * Registers a {@link WebMvcConfigurer} that adds the {@link FlowWebInterceptor} at the highest available
	 * precedence.
	 *
	 * @param flowProcessor the flow processor
	 * @param flowProcessorSupport flow processor support for thread-local cleanup
	 * @return the configurer
	 */
	@Bean
	public WebMvcConfigurer mangoFlowWebInterceptorConfigurer(
			FlowProcessor flowProcessor, FlowProcessorSupport flowProcessorSupport) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(new FlowWebInterceptor(flowProcessor, flowProcessorSupport))
						.order(INTERCEPTOR_ORDER);
			}
		};
	}
}
