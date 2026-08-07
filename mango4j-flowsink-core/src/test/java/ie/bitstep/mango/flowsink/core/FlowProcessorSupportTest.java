package ie.bitstep.mango.flowsink.core;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import ie.bitstep.mango.flowsink.model.FlowEvent;

import static org.assertj.core.api.Assertions.assertThat;

class FlowProcessorSupportTest {

	@Test
	void pop_clears_thread_state_on_mismatch() {
		FlowProcessorSupport support = new FlowProcessorSupport();
		FlowEvent first = FlowEvent.builder().name("first").build();
		FlowEvent second = FlowEvent.builder().name("second").build();

		support.push(first);
		support.push(second);
		support.pop(first);

		assertThat(support.currentContext()).isNull();
		assertThat(support.hasActiveFlow()).isFalse();
	}

	@Test
	void unix_nanos_uses_epoch_and_nano_components() {
		FlowProcessorSupport support = new FlowProcessorSupport();
		Instant instant = Instant.ofEpochSecond(12L, 345L);

		assertThat(support.unixNanos(instant)).isEqualTo(12_000_000_345L);
	}

	@Test
	void disabled_support_hides_current_context_but_keeps_stack_for_reenable() {
		FlowProcessorSupport support = new FlowProcessorSupport();
		FlowEvent event = FlowEvent.builder().name("flow").build();
		support.push(event);

		support.setEnabled(false);
		assertThat(support.currentContext()).isNull();
		assertThat(support.hasActiveFlow()).isTrue();

		support.setEnabled(true);
		assertThat(support.currentContext()).isSameAs(event);
	}

	@Test
	void pop_of_expected_top_and_cleanup_thread_locals_clear_active_flow() {
		FlowProcessorSupport support = new FlowProcessorSupport();
		FlowEvent event = FlowEvent.builder().name("flow").build();
		support.push(event);

		support.pop(event);
		assertThat(support.hasActiveFlow()).isFalse();

		support.push(event);
		support.cleanupThreadLocals();
		assertThat(support.currentContext()).isNull();
		assertThat(support.hasActiveFlow()).isFalse();
	}

	@Test
	void pop_of_top_with_remaining_stack_keeps_parent_active() {
		FlowProcessorSupport support = new FlowProcessorSupport();
		FlowEvent parent = FlowEvent.builder().name("parent").build();
		FlowEvent child = FlowEvent.builder().name("child").build();

		support.push(parent);
		support.push(child);
		support.pop(child);

		assertThat(support.currentContext()).isSameAs(parent);
		assertThat(support.hasActiveFlow()).isTrue();
	}

	@Test
	void ignores_null_push_empty_pop_and_no_op_batch_methods() {
		FlowProcessorSupport support = new FlowProcessorSupport();

		support.push(null);
		support.pop(null);
		support.startNewBatch();
		support.clearBatchAfterDispatch();
		support.logOrphanStep("demo.step", null);
		support.logOrphanStep("demo.step", ie.bitstep.mango.flowsink.annotations.OrphanAlert.Level.NONE);
		support.logOrphanStep("demo.step", ie.bitstep.mango.flowsink.annotations.OrphanAlert.Level.TRACE);
		support.logOrphanStep("demo.step", ie.bitstep.mango.flowsink.annotations.OrphanAlert.Level.DEBUG);
		support.logOrphanStep("demo.step", ie.bitstep.mango.flowsink.annotations.OrphanAlert.Level.INFO);
		support.logOrphanStep("demo.step", ie.bitstep.mango.flowsink.annotations.OrphanAlert.Level.WARN);
		support.logOrphanStep("demo.step", ie.bitstep.mango.flowsink.annotations.OrphanAlert.Level.ERROR);

		assertThat(support.currentContext()).isNull();
		assertThat(support.isEnabled()).isTrue();
	}
}
