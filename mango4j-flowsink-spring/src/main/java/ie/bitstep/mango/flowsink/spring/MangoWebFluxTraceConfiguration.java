package ie.bitstep.mango.flowsink.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.spring.webflux.TraceContextWebFilter;

/** Registers the reactive {@code TraceContextWebFilter} when used outside of Spring Boot auto-configuration. */
@Configuration(proxyBeanMethods = false)
public class MangoWebFluxTraceConfiguration {
	/**
	 * @param support flow processor support for cleaning up thread-local state after each request
	 * @return the configured {@code TraceContextWebFilter}
	 */
	@Bean(name = "mangoWebFluxTraceContextFilter")
	public WebFilter mangoWebFluxTraceContextFilter(FlowProcessorSupport support) {
		return new TraceContextWebFilter(support);
	}
}
