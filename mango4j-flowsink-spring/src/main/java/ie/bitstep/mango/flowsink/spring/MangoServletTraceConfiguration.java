package ie.bitstep.mango.flowsink.spring;

import jakarta.servlet.Filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.spring.web.TraceContextFilter;

/** Registers the servlet {@code TraceContextFilter} when used outside of Spring Boot auto-configuration. */
@Configuration(proxyBeanMethods = false)
public class MangoServletTraceConfiguration {
	/**
	 * @param support flow processor support for cleaning up thread-local state after each request
	 * @return the configured {@code TraceContextFilter}
	 */
	@Bean(name = "mangoTraceContextFilter")
	public Filter mangoTraceContextFilter(FlowProcessorSupport support) {
		return new TraceContextFilter(support);
	}
}
