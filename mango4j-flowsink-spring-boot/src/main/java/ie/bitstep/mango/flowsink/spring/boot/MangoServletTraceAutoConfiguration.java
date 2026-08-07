package ie.bitstep.mango.flowsink.spring.boot;

import jakarta.servlet.Filter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import ie.bitstep.mango.flowsink.core.FlowProcessorSupport;
import ie.bitstep.mango.flowsink.spring.web.TraceContextFilter;

/**
 * Spring Boot auto-configuration that registers the servlet {@code TraceContextFilter} when the Servlet API is on the
 * classpath.
 */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
public class MangoServletTraceAutoConfiguration {
	/**
	 * Registers the {@code TraceContextFilter} that propagates flow trace context across servlet requests.
	 *
	 * @param support flow processor support for cleaning up thread-local state after each request
	 * @return the configured {@code TraceContextFilter}
	 */
	@Bean(name = "mangoTraceContextFilter")
	@ConditionalOnMissingBean(name = "mangoTraceContextFilter")
	public Filter mangoTraceContextFilter(FlowProcessorSupport support) {
		return new TraceContextFilter(support);
	}
}
