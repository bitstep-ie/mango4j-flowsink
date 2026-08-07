package ie.bitstep.mango.flowsink.spring;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.opentelemetry.api.trace.SpanKind;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ie.bitstep.mango.flowsink.annotations.Flow;
import ie.bitstep.mango.flowsink.annotations.Kind;
import ie.bitstep.mango.flowsink.annotations.OrphanAlert;
import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.Step;
import ie.bitstep.mango.flowsink.core.sinks.FlowSinkHandler;
import ie.bitstep.mango.flowsink.model.FlowEvent;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAspectStepBehaviorTest {

	@Test
	void nested_step_is_recorded_on_parent_flow() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
			SampleFlows flows = context.getBean(SampleFlows.class);
			CapturingSink sink = context.getBean(CapturingSink.class);

			flows.checkout("alice");

			awaitSize(sink.events, 2);
			FlowEvent completed = sink.events.get(1);
			assertThat(completed.events()).hasSize(1);
			assertThat(completed.events().get(0).name()).isEqualTo("demo.stock.verify");
			assertThat(completed.events().get(0).attributes().map()).containsEntry("sku", "SKU-1");
			assertThat(completed.events().get(0).kind()).isEqualTo(SpanKind.CLIENT.name());
		}
	}

	@Test
	void failed_nested_step_can_be_recovered_while_root_flow_still_completes() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
			SampleFlows flows = context.getBean(SampleFlows.class);
			CapturingSink sink = context.getBean(CapturingSink.class);

			flows.checkoutWithRecoveredStepFailure("SKU-9");

			awaitSize(sink.events, 2);
			FlowEvent completed = sink.events.get(1);
			assertThat(completed.name()).isEqualTo("demo.checkout.recovered");
			assertThat(completed.eventContext()).containsEntry("lifecycle", "COMPLETED");
			assertThat(completed.events()).hasSize(1);
			assertThat(completed.events().get(0).name()).isEqualTo("demo.stock.reserve");
			assertThat(completed.events().get(0).attributes().map())
					.containsEntry("sku", "SKU-9")
					.containsEntry("error", "IllegalStateException");
		}
	}

	@Test
	void orphan_step_is_promoted_to_flow_and_failure_is_captured() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
			SampleFlows flows = context.getBean(SampleFlows.class);
			CapturingSink sink = context.getBean(CapturingSink.class);

			try {
				flows.orphanFail("SKU-2");
			} catch (IllegalStateException ignored) {
			}

			awaitSize(sink.events, 2);
			FlowEvent failed = sink.events.get(1);
			assertThat(failed.name()).isEqualTo("demo.orphan.step");
			assertThat(failed.eventContext()).containsEntry("lifecycle", "FAILED");
			assertThat(failed.attributes().map()).containsEntry("sku", "SKU-2").containsKey("error");
			assertThat(failed.throwable()).isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void implicit_step_name_uses_method_signature() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
			SampleFlows flows = context.getBean(SampleFlows.class);
			CapturingSink sink = context.getBean(CapturingSink.class);

			flows.orphanImplicitName();

			awaitSize(sink.events, 2);
			assertThat(sink.events.get(1).name()).contains("orphanImplicitName");
		}
	}

	private static void awaitSize(List<FlowEvent> events, int expectedSize) {
		long deadline = System.currentTimeMillis() + 2000;
		while (System.currentTimeMillis() < deadline) {
			if (events.size() >= expectedSize) {
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		assertThat(events).hasSize(expectedSize);
	}

	@Configuration
	@EnableMangoFlowSink
	static class TestConfig {
		@Bean
		StockService stockService() {
			return new StockService();
		}

		@Bean
		SampleFlows sampleFlows(StockService stockService) {
			return new SampleFlows(stockService);
		}

		@Bean
		CapturingSink capturingSink() {
			return new CapturingSink();
		}
	}

	static class SampleFlows {
		private final StockService stockService;

		SampleFlows(StockService stockService) {
			this.stockService = stockService;
		}

		@Flow(name = "demo.checkout")
		public void checkout(@PushAttribute("user.id") String userId) {
			stockService.verifyStock("SKU-1");
		}

		@Flow(name = "demo.checkout.recovered")
		public void checkoutWithRecoveredStepFailure(@PushAttribute("sku") String sku) {
			try {
				stockService.reserveStock(sku);
			} catch (IllegalStateException ignored) {
			}
		}

		@Step("demo.orphan.step")
		@OrphanAlert(OrphanAlert.Level.NONE)
		public void orphanFail(@PushAttribute("sku") String sku) {
			throw new IllegalStateException("boom");
		}

		@Step
		@OrphanAlert(OrphanAlert.Level.NONE)
		public void orphanImplicitName() {}
	}

	static class StockService {
		@Step("demo.stock.verify")
		@Kind(SpanKind.CLIENT)
		public void verifyStock(@PushAttribute("sku") String sku) {}

		@Step("demo.stock.reserve")
		public void reserveStock(@PushAttribute("sku") String sku) {
			throw new IllegalStateException("reserve failed");
		}
	}

	static class CapturingSink implements FlowSinkHandler {
		final List<FlowEvent> events = new CopyOnWriteArrayList<>();

		@Override
		public void handle(FlowEvent event) {
			events.add(event);
		}
	}
}
