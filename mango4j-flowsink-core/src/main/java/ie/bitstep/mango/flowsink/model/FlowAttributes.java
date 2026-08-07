package ie.bitstep.mango.flowsink.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class FlowAttributes {
	private final Map<String, Object> attributes;

	public FlowAttributes() {
		this(new LinkedHashMap<>());
	}

	public FlowAttributes(Map<String, Object> attributes) {
		this.attributes = new LinkedHashMap<>(attributes == null ? Map.of() : attributes);
	}

	public void put(String key, Object value) {
		attributes.put(key, value);
	}

	public Map<String, Object> map() {
		return attributes;
	}
}
