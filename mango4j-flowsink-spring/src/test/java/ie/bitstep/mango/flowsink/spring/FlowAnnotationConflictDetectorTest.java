package ie.bitstep.mango.flowsink.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.annotations.Step;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowAnnotationConflictDetectorTest {

	@Test
	void context_fails_to_start_when_method_has_both_flow_and_step() {
		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(ConflictConfig.class))
				.isInstanceOf(BeanCreationException.class)
				.hasMessageContaining("@Flow")
				.hasMessageContaining("@Step")
				.hasMessageContaining("doWork");
	}

	@Test
	void context_starts_normally_when_flow_and_step_are_on_separate_methods() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ValidConfig.class)) {
			// no exception — separate methods are fine
		}
	}

	// -------------------------------------------------------------------------

	@Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Import(MangoFlowSinkConfiguration.class)
	static class ConflictConfig {
		@Bean
		public ConflictBean conflictBean() {
			return new ConflictBean();
		}
	}

	static class ConflictBean {
		@Flow("demo.flow")
		@Step("demo.step")
		public void doWork() {}
	}

	@Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Import(MangoFlowSinkConfiguration.class)
	static class ValidConfig {
		@Bean
		public ValidBean validBean() {
			return new ValidBean();
		}
	}

	static class ValidBean {
		@Flow("demo.flow")
		public void flow() {}

		@Step("demo.step")
		public void step() {}
	}
}
