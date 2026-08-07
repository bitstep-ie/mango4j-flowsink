package ie.bitstep.mango.flowsink.core.processor;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowProcessorTest {

	@Test
	void convenience_overloads_delegate_to_full_methods_with_expected_defaults() {
		RecordingFlowProcessor processor = new RecordingFlowProcessor();

		processor.onFlowStarted("demo.start");
		assertThat(processor.name).isEqualTo("demo.start");
		assertThat(processor.attrs).isEqualTo(Map.of());
		assertThat(processor.context).isEqualTo(Map.of());
		assertThat(processor.meta).isNull();

		processor.onFlowStarted("demo.start.meta", Map.of("a", 1), Map.of("b", 2));
		assertThat(processor.name).isEqualTo("demo.start.meta");
		assertThat(processor.attrs).containsEntry("a", 1);
		assertThat(processor.context).containsEntry("b", 2);
		assertThat(processor.meta).isNull();

		processor.onFlowCompleted("demo.done");
		assertThat(processor.name).isEqualTo("demo.done");
		assertThat(processor.attrs).isEqualTo(Map.of());
		assertThat(processor.context).isEqualTo(Map.of());
		assertThat(processor.meta).isNull();

		processor.onFlowCompleted("demo.done.meta", Map.of("c", 3), Map.of("d", 4));
		assertThat(processor.name).isEqualTo("demo.done.meta");
		assertThat(processor.attrs).containsEntry("c", 3);
		assertThat(processor.context).containsEntry("d", 4);
		assertThat(processor.meta).isNull();

		IllegalStateException failure = new IllegalStateException("boom");
		processor.onFlowFailed("demo.fail", failure);
		assertThat(processor.name).isEqualTo("demo.fail");
		assertThat(processor.error).isSameAs(failure);
		assertThat(processor.attrs).isEqualTo(Map.of());
		assertThat(processor.context).isEqualTo(Map.of());
		assertThat(processor.meta).isNull();

		processor.onFlowFailed("demo.fail.meta", failure, Map.of("e", 5), Map.of("f", 6));
		assertThat(processor.name).isEqualTo("demo.fail.meta");
		assertThat(processor.error).isSameAs(failure);
		assertThat(processor.attrs).containsEntry("e", 5);
		assertThat(processor.context).containsEntry("f", 6);
		assertThat(processor.meta).isNull();
	}

	private static final class RecordingFlowProcessor implements FlowProcessor {
		private String name;
		private Throwable error;
		private Map<String, Object> attrs;
		private Map<String, Object> context;
		private FlowMeta meta;

		@Override
		public void onFlowStarted(
				String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
			this.name = name;
			this.error = null;
			this.attrs = extraAttrs;
			this.context = extraContext;
			this.meta = meta;
		}

		@Override
		public void onFlowCompleted(
				String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
			this.name = name;
			this.error = null;
			this.attrs = extraAttrs;
			this.context = extraContext;
			this.meta = meta;
		}

		@Override
		public void onFlowFailed(
				String name,
				Throwable error,
				Map<String, Object> extraAttrs,
				Map<String, Object> extraContext,
				FlowMeta meta) {
			this.name = name;
			this.error = error;
			this.attrs = extraAttrs;
			this.context = extraContext;
			this.meta = meta;
		}
	}
}
