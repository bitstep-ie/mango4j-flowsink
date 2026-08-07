package ie.bitstep.mango.flowsink.model;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowEventTest {

	@Test
	void begin_and_end_step_event_records_timing_and_updates() {
		FlowEvent event =
				FlowEvent.builder().name("demo.flow").timestamp(Instant.now()).build();

		event.beginStepEvent("demo.step", 10L, 100L, new FlowAttributes(Map.of("sku", "123")), "CLIENT");
		event.endStepEvent(20L, 175L, Map.of("status", "ok"));

		assertThat(event.events()).hasSize(1);
		StepEvent step = event.events().get(0);
		assertThat(step.name()).isEqualTo("demo.step");
		assertThat(step.timeUnixNano()).isEqualTo(10L);
		assertThat(step.endTimeUnixNano()).isEqualTo(20L);
		assertThat(step.elapsedNanos()).isEqualTo(75L);
		assertThat(step.attributes().map()).containsEntry("sku", "123").containsEntry("status", "ok");
		assertThat(event.endTimestamp()).isNull(); // endTimestamp is set only by markEnd(), not by endStepEvent()

		event.markEnd(null);
		assertThat(event.endTimestamp()).isNotNull();
	}

	@Test
	void snapshot_is_deep_enough_for_attributes_context_and_events() {
		FlowEvent event = FlowEvent.builder().name("demo.flow").build();
		event.attributes().put("user.id", "alice");
		event.eventContext().put("lifecycle", "STARTED");
		event.setReturnValue("ok");
		event.beginStepEvent("demo.step", 10L, 100L, new FlowAttributes(Map.of("sku", "123")), "CLIENT");
		event.endStepEvent(20L, 175L, null);

		FlowEvent snapshot = event.snapshot();
		event.attributes().put("later", "value");
		event.eventContext().put("lifecycle", "COMPLETED");
		event.setReturnValue("later");

		assertThat(snapshot).isNotSameAs(event);
		assertThat(snapshot.attributes().map())
				.containsEntry("user.id", "alice")
				.doesNotContainKey("later");
		assertThat(snapshot.eventContext()).containsEntry("lifecycle", "STARTED");
		assertThat(snapshot.returnValue()).isEqualTo("ok");
		assertThat(snapshot.events()).hasSize(1);
		assertThat(snapshot.events().get(0)).isNotSameAs(event.events().get(0));
	}

	@Test
	void builder_preserves_provided_timestamp_and_defaults_null_to_now() {
		// Use a fixed past instant — clearly distinct from any Instant.now() call inside the constructor,
		// which kills the negated-conditional mutation on the ternary in FlowEvent.<init>.
		Instant fixedPast = Instant.parse("2020-01-01T00:00:00Z");
		FlowEvent withTimestamp =
				FlowEvent.builder().name("demo.flow").timestamp(fixedPast).build();
		FlowEvent withoutTimestamp = FlowEvent.builder().name("demo.flow").build();

		assertThat(withTimestamp.timestamp()).isEqualTo(fixedPast);
		assertThat(withoutTimestamp.timestamp()).isAfter(fixedPast);
	}

	@Test
	void builder_trace_and_defaults_are_preserved() {
		Instant timestamp = Instant.now();
		FlowEvent event = FlowEvent.builder()
				.name("demo.flow")
				.timestamp(timestamp)
				.putAttribute("user.id", "alice")
				.putEventContext("lifecycle", "STARTED")
				.build();

		event.trace("trace-1", "span-2", "parent-3");
		event.setReturnValue("ok");
		event.clearReturnValue();
		event.setKind(null);
		event.endStepEvent(30L, 40L, Map.of("ignored", true));

		StepEvent step = new StepEvent("demo.step", 10L, null, "INTERNAL");
		step.setEndTimeUnixNano(20L);
		step.setElapsedNanos(10L);
		StepEvent snapshot = step.snapshot();
		FlowAttributes nullBacked = new FlowAttributes(null);

		assertThat(event.timestamp()).isEqualTo(timestamp);
		assertThat(event.attributes().map()).containsEntry("user.id", "alice");
		assertThat(event.eventContext()).containsEntry("lifecycle", "STARTED");
		assertThat(event.traceId()).isEqualTo("trace-1");
		assertThat(event.spanId()).isEqualTo("span-2");
		assertThat(event.parentSpanId()).isEqualTo("parent-3");
		assertThat(event.returnValue()).isNull();
		assertThat(event.kind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.INTERNAL);
		assertThat(snapshot.attributes().map()).isEmpty();
		assertThat(snapshot.endTimeUnixNano()).isEqualTo(20L);
		assertThat(snapshot.elapsedNanos()).isEqualTo(10L);
		assertThat(snapshot.kind()).isEqualTo("INTERNAL");
		assertThat(nullBacked.map()).isEmpty();
	}

	@Test
	void end_step_event_with_empty_updates_skips_attribute_merge() {
		FlowEvent event = FlowEvent.builder().name("demo.flow").build();
		event.beginStepEvent("demo.step", 10L, 100L, new FlowAttributes(Map.of("sku", "123")), "CLIENT");
		event.endStepEvent(20L, 175L, Map.of());

		assertThat(event.events()).hasSize(1);
		StepEvent step = event.events().get(0);
		assertThat(step.attributes().map()).containsOnlyKeys("sku");
	}

	@Test
	void return_value_round_trips_until_it_is_cleared() {
		FlowEvent event = FlowEvent.builder().name("demo.flow").build();

		event.setReturnValue(Map.of("status", "ok"));

		assertThat(event.returnValue()).isEqualTo(Map.of("status", "ok"));

		event.clearReturnValue();

		assertThat(event.returnValue()).isNull();
	}

	@Test
	void mark_end_records_provided_instant_and_falls_back_to_now_when_null() {
		Instant specific = Instant.parse("2024-06-01T12:00:00Z");
		FlowEvent event = FlowEvent.builder().name("demo.flow").build();

		event.markEnd(specific);
		assertThat(event.endTimestamp()).isEqualTo(specific);

		FlowEvent nullEvent = FlowEvent.builder().name("demo.flow").build();
		nullEvent.markEnd(null);
		assertThat(nullEvent.endTimestamp()).isNotNull().isAfter(specific);
	}

	@Test
	void flow_attributes_copies_input_and_allows_put() {
		Map<String, Object> source = new java.util.LinkedHashMap<>();
		source.put("user.id", "alice");
		FlowAttributes attributes = new FlowAttributes(source);
		source.put("later", "ignored");

		attributes.put("tenant.id", "bitstep");

		assertThat(attributes.map())
				.containsEntry("user.id", "alice")
				.containsEntry("tenant.id", "bitstep")
				.doesNotContainKey("later");
	}
}
