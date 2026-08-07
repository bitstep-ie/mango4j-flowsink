package ie.bitstep.mango.flowsink.core.processor;

import java.util.Map;

public interface FlowProcessor {
	default void onFlowStarted(String name) {
		onFlowStarted(name, Map.of(), Map.of());
	}

	default void onFlowStarted(String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext) {
		onFlowStarted(name, extraAttrs, extraContext, null);
	}

	default void onFlowCompleted(String name) {
		onFlowCompleted(name, Map.of(), Map.of());
	}

	default void onFlowCompleted(String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext) {
		onFlowCompleted(name, extraAttrs, extraContext, null);
	}

	default void onFlowFailed(String name, Throwable error) {
		onFlowFailed(name, error, Map.of(), Map.of());
	}

	default void onFlowFailed(
			String name, Throwable error, Map<String, Object> extraAttrs, Map<String, Object> extraContext) {
		onFlowFailed(name, error, extraAttrs, extraContext, null);
	}

	void onFlowStarted(String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta);

	void onFlowCompleted(String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta);

	void onFlowFailed(
			String name,
			Throwable error,
			Map<String, Object> extraAttrs,
			Map<String, Object> extraContext,
			FlowMeta meta);
}
