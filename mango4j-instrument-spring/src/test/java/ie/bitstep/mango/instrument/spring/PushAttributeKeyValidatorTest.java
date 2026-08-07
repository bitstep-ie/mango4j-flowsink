package ie.bitstep.mango.instrument.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.PushContextValue;
import ie.bitstep.mango.instrument.annotations.Step;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushAttributeKeyValidatorTest {

	@Test
	void context_fails_when_flow_method_has_push_attribute_with_blank_key() {
		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(BlankPushAttributeConfig.class))
				.isInstanceOf(BeanCreationException.class)
				.hasMessageContaining("@PushAttribute")
				.hasMessageContaining("blank key")
				.hasMessageContaining("doWork");
	}

	@Test
	void context_fails_when_step_method_has_push_context_value_with_blank_key() {
		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(BlankPushContextValueConfig.class))
				.isInstanceOf(BeanCreationException.class)
				.hasMessageContaining("@PushContextValue")
				.hasMessageContaining("blank key")
				.hasMessageContaining("doStep");
	}

	@Test
	void context_starts_normally_when_push_keys_are_non_blank() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ValidConfig.class)) {
			// no exception — non-blank keys are fine
		}
	}

	// -------------------------------------------------------------------------

	@Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Import(MangoInstrumentationConfiguration.class)
	static class BlankPushAttributeConfig {
		@Bean
		public BlankPushAttributeBean bean() {
			return new BlankPushAttributeBean();
		}
	}

	static class BlankPushAttributeBean {
		@Flow("demo.flow")
		public void doWork(@PushAttribute("") String value) {}
	}

	@Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Import(MangoInstrumentationConfiguration.class)
	static class BlankPushContextValueConfig {
		@Bean
		public BlankPushContextValueBean bean() {
			return new BlankPushContextValueBean();
		}
	}

	static class BlankPushContextValueBean {
		@Step("demo.step")
		public void doStep(@PushContextValue("   ") String value) {}
	}

	@Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Import(MangoInstrumentationConfiguration.class)
	static class ValidConfig {
		@Bean
		public ValidBean validBean() {
			return new ValidBean();
		}
	}

	static class ValidBean {
		@Flow("demo.flow")
		public void flow(@PushAttribute("user.id") String userId) {}

		@Step("demo.step")
		public void step(@PushContextValue("trace.id") String traceId) {}
	}
}
