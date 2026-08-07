package ie.bitstep.mango.flowsink.validation;

import java.util.Map;

@FunctionalInterface
public interface FlowAttributeValidator {
	void validate(String key, Object value);

	default void validateMap(Map<String, Object> map, String mapName) {
		if (map == null || map.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			try {
				validate(entry.getKey(), entry.getValue());
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException(String.format("Invalid %s entry: %s", mapName, ex.getMessage()), ex);
			}
		}
	}
}
