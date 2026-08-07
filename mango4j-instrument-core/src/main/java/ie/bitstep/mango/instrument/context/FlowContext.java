package ie.bitstep.mango.instrument.context;

import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.validation.FlowAttributeValidator;

public class FlowContext {
	private static final Logger log = LoggerFactory.getLogger(FlowContext.class);
	private final FlowProcessorSupport support;
	private final FlowAttributeValidator validator;

	public FlowContext(FlowProcessorSupport support) {
		this(support, null);
	}

	public FlowContext(FlowProcessorSupport support, FlowAttributeValidator validator) {
		this.support = Objects.requireNonNull(support, "support");
		this.validator = validator;
	}

	public <T> T put(String key, T value) {
		return putAttr(key, value);
	}

	public void putAll(Map<String, ?> map) {
		putAllAttrs(map);
	}

	public <T> T putAttr(String key, T value) {
		if (!support.isEnabled() || key == null || key.isBlank()) {
			return value;
		}
		FlowEvent context = support.currentContext();
		if (context != null) {
			validate(key, value);
			context.attributes().put(key, value);
		} else {
			log.debug("FlowContext.putAttr: no active flow on this thread — attribute '{}' was not recorded", key);
		}
		return value;
	}

	public void putAllAttrs(Map<String, ?> map) {
		if (!support.isEnabled() || map == null || map.isEmpty()) {
			return;
		}
		FlowEvent context = support.currentContext();
		if (context == null) {
			log.debug(
					"FlowContext.putAllAttrs: no active flow on this thread — {} attribute(s) were not recorded",
					map.size());
			return;
		}
		map.forEach((key, value) -> {
			if (key != null && !key.isBlank()) {
				validate(key, value);
				context.attributes().put(key, value);
			}
		});
	}

	public <T> T putContext(String key, T value) {
		if (!support.isEnabled() || key == null || key.isBlank()) {
			return value;
		}
		FlowEvent context = support.currentContext();
		if (context != null) {
			validate(key, value);
			context.eventContext().put(key, value);
		} else {
			log.debug("FlowContext.putContext: no active flow on this thread — context key '{}' was not recorded", key);
		}
		return value;
	}

	public void putAllContext(Map<String, ?> map) {
		if (!support.isEnabled() || map == null || map.isEmpty()) {
			return;
		}
		FlowEvent context = support.currentContext();
		if (context == null) {
			log.debug(
					"FlowContext.putAllContext: no active flow on this thread — {} context value(s) were not recorded",
					map.size());
			return;
		}
		map.forEach((key, value) -> {
			if (key != null && !key.isBlank()) {
				validate(key, value);
				context.eventContext().put(key, value);
			}
		});
	}

	private void validate(String key, Object value) {
		if (validator != null) {
			validator.validate(key, value);
		}
	}
}
