package ie.bitstep.mango.flowsink.spring.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import ie.bitstep.mango.flowsink.spring.MangoFlowSinkConfiguration;

/** Spring Boot auto-configuration that registers the core mango4j instrumentation beans. */
@AutoConfiguration
@Import(MangoFlowSinkConfiguration.class)
public class MangoFlowSinkAutoConfiguration {}
