package ie.bitstep.mango.instrument.core.sinks;

import ie.bitstep.mango.instrument.model.FlowEvent;

@FunctionalInterface
public interface FlowSinkHandler {
	void handle(FlowEvent event);

	/** Returns a human-readable name used for thread naming and diagnostics. */
	default String sinkName() {
		return getClass().getSimpleName();
	}
}
