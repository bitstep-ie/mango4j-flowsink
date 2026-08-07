package ie.bitstep.mango.flowsink.spring.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.spring.webflux.TraceContextWebFilter;

/**
 * Spring Boot auto-configuration that registers the WebFlux {@code TraceContextWebFilter} when the WebFlux API is on
 * the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(WebFilter.class)
public class MangoWebFluxTraceAutoConfiguration {
	/**
	 * Registers the {@code TraceContextWebFilter} that propagates flow trace context across reactive requests.
	 *
	 * @param support flow processor support for cleaning up thread-local state after each request
	 * @return the configured {@code TraceContextWebFilter}
	 */
	@Bean(name = "mangoWebFluxTraceContextFilter")
	@ConditionalOnMissingBean(name = "mangoWebFluxTraceContextFilter")
	public WebFilter mangoWebFluxTraceContextFilter(FlowProcessorSupport support) {
		return new TraceContextWebFilter(support);
	}
}
