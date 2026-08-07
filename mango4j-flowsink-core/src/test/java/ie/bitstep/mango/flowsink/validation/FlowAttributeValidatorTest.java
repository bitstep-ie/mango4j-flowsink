package ie.bitstep.mango.flowsink.validation;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowAttributeValidatorTest {

	@Test
	void validate_map_wraps_invalid_entries_with_map_name() {
		FlowAttributeValidator validator = (key, value) -> {
			if ("bad".equals(key)) {
				throw new IllegalArgumentException("bad key");
			}
		};

		assertThatThrownBy(() -> validator.validateMap(Map.of("bad", 1), "attributes"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid attributes entry: bad key")
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void validate_map_ignores_null_or_empty_maps() {
		FlowAttributeValidator validator = (key, value) -> {
			throw new IllegalArgumentException("should not run");
		};

		assertThatCode(() -> validator.validateMap(null, "attributes")).doesNotThrowAnyException();
		assertThatCode(() -> validator.validateMap(Map.of(), "attributes")).doesNotThrowAnyException();
	}
}
