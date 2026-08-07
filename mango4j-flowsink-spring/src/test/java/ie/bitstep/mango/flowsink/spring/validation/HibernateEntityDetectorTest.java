package ie.bitstep.mango.flowsink.spring.validation;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HibernateEntityDetectorTest {

	@jakarta.persistence.Entity
	static class EntityType {
		String id = "1";
	}

	static class Wrapper {
		Object payload;

		Wrapper(Object payload) {
			this.payload = payload;
		}
	}

	static class Node {
		Object value;
		Node next;

		Node(Object value) {
			this.value = value;
		}
	}

	static class ParentHolder {
		Object inherited;
	}

	static class ChildHolder extends ParentHolder {
		transient Object ignoredTransient;
		static Object ignoredStatic;
	}

	static class ProxyMarker$HibernateProxy$Thing {
		String id = "1";
	}

	static class ThreadHolder {
		Thread thread = new Thread();
	}

	static class PrivateEntityHolder {
		private final Object nested = new EntityType();
	}

	enum SampleEnum {
		VALUE
	}

	@Test
	void throws_when_nested_entity_is_detected() {
		Wrapper wrapper = new Wrapper(List.of(Map.of("entity", new EntityType()), Optional.empty()));

		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", wrapper, HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload");
	}

	@Test
	void allows_terminal_and_non_entity_nested_values() {
		Wrapper wrapper = new Wrapper(List.of(Map.of("number", 1, "text", "ok"), Optional.of("value")));

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", wrapper, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void handles_cycles_without_throwing_for_non_entities() {
		Node first = new Node("ok");
		Node second = new Node(Optional.of("value"));
		first.next = second;
		second.next = first;

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", first, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void warn_and_info_modes_do_not_throw_when_entity_is_present() {
		Logger logger = (Logger) LoggerFactory.getLogger(HibernateEntityDetector.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
							"payload", new EntityType(), HibernateEntityLogLevel.WARN))
					.doesNotThrowAnyException();
			assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
							"payload", new EntityType(), HibernateEntityLogLevel.INFO))
					.doesNotThrowAnyException();

			assertThat(appender.list)
					.filteredOn(event -> event.getFormattedMessage().contains("flow attribute 'payload'"))
					.hasSize(2)
					.extracting(ILoggingEvent::getLevel)
					.containsExactly(Level.WARN, Level.INFO);
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void traverses_superclass_fields_but_ignores_static_and_transient_fields() {
		ChildHolder holder = new ChildHolder();
		holder.inherited = new EntityType();
		holder.ignoredTransient = new EntityType();
		ChildHolder.ignoredStatic = new EntityType();

		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", holder, HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload.inherited");
	}

	@Test
	void allows_terminal_arrays_and_optional_empty_values() {
		Object[] payload = {UUID.randomUUID(), "ok", Optional.empty(), new int[] {1, 2, 3}};

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", payload, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void detects_entities_inside_object_arrays() {
		Object[] payload = {"ok", new EntityType()};

		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", payload, HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload[1]");
	}

	@Test
	void detects_entities_inside_optional_values() {
		Wrapper wrapper = new Wrapper(Optional.of(new EntityType()));

		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", wrapper, HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload.payload.value");
	}

	@Test
	void detects_entities_in_later_iterable_entries_and_reports_the_correct_index() {
		Wrapper wrapper = new Wrapper(List.of("ok", new EntityType()));

		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", wrapper, HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload.payload[1]");
	}

	@Test
	void uses_error_level_when_detector_is_constructed_with_null() {
		HibernateEntityDetector detector = new HibernateEntityDetector(null);

		assertThatThrownBy(() -> detector.validate("payload", new EntityType()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload");
		assertThat(readLogLevel(detector)).isEqualTo(HibernateEntityLogLevel.ERROR);
	}

	@Test
	void uses_error_level_when_static_check_receives_null_level() {
		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity("payload", new EntityType(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload");
	}

	@Test
	void ignores_null_values_and_terminal_types_via_validator_entrypoint() {
		HibernateEntityDetector detector = new HibernateEntityDetector(HibernateEntityLogLevel.ERROR);

		assertThatCode(() -> detector.validate("payload", null)).doesNotThrowAnyException();
		assertThatCode(() -> detector.validate("payload", SampleEnum.VALUE)).doesNotThrowAnyException();
		assertThatCode(() -> detector.validate("payload", HibernateEntityDetector.class))
				.doesNotThrowAnyException();
	}

	@Test
	void detects_proxy_marker_names_and_allows_other_terminal_types() {
		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", new ProxyMarker$HibernateProxy$Thing(), HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ProxyMarker$HibernateProxy$Thing");

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", java.util.Date.from(Instant.now()), null))
				.doesNotThrowAnyException();
		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", Instant.now(), HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", new Object(), HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", new ThreadHolder(), HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void inspects_private_fields_when_reflection_access_needs_to_be_opened() {
		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", new PrivateEntityHolder(), HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("payload.nested");
	}

	@Test
	void ignores_broken_runtime_annotations_when_entity_introspection_fails() throws Exception {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		Path tempDir = Files.createTempDirectory("hibernate-detector-broken-annotation");
		Path packageDir = Files.createDirectories(tempDir.resolve("dynamic"));
		Path annotationSource = packageDir.resolve("BrokenMarker.java");
		Path targetSource = packageDir.resolve("BrokenAnnotatedType.java");

		Files.writeString(
				annotationSource,
				"""
				package dynamic;

				import java.lang.annotation.ElementType;
				import java.lang.annotation.Retention;
				import java.lang.annotation.RetentionPolicy;
				import java.lang.annotation.Target;

				@Retention(RetentionPolicy.RUNTIME)
				@Target(ElementType.TYPE)
				public @interface BrokenMarker {
				}
				""");
		Files.writeString(
				targetSource,
				"""
				package dynamic;

				@BrokenMarker
				public class BrokenAnnotatedType {
				}
				""");

		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
			boolean compiled = compiler.getTask(
							null,
							fileManager,
							null,
							List.of("-d", tempDir.toString()),
							null,
							fileManager.getJavaFileObjects(annotationSource.toFile(), targetSource.toFile()))
					.call();
			if (!compiled) {
				throw new IllegalStateException("dynamic compilation failed");
			}
		}

		Files.delete(tempDir.resolve("dynamic/BrokenMarker.class"));

		try (URLClassLoader classLoader = new URLClassLoader(
				new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
			Class<?> brokenType = Class.forName("dynamic.BrokenAnnotatedType", true, classLoader);
			Object instance = brokenType.getDeclaredConstructor().newInstance();

			assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
							"payload", instance, HibernateEntityLogLevel.ERROR))
					.doesNotThrowAnyException();
		}
	}

	@Test
	void null_element_in_array_is_silently_skipped_by_detect() {
		Object[] payloadWithNull = {null, "ok", null};

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", payloadWithNull, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void get_all_fields_handles_interface_superclass_returning_null() throws Exception {
		Method getAllFields = HibernateEntityDetector.class.getDeclaredMethod("getAllFields", Class.class);
		getAllFields.setAccessible(true);

		assertThatCode(() -> getAllFields.invoke(null, Runnable.class)).doesNotThrowAnyException();
	}

	@Test
	void private_type_helpers_cover_remaining_terminal_and_field_cases() throws Exception {
		Method isTerminal = HibernateEntityDetector.class.getDeclaredMethod("isTerminal", Class.class);
		Method getAllFields = HibernateEntityDetector.class.getDeclaredMethod("getAllFields", Class.class);
		isTerminal.setAccessible(true);
		getAllFields.setAccessible(true);

		assertThatCode(() -> isTerminal.invoke(null, int.class)).doesNotThrowAnyException();
		assertThatCode(() -> isTerminal.invoke(null, BigDecimal.class)).doesNotThrowAnyException();
		assertThatCode(() -> getAllFields.invoke(null, new Object[] {null})).doesNotThrowAnyException();
		assertThatCode(() -> getAllFields.invoke(null, Object.class)).doesNotThrowAnyException();
		assertThatCode(() -> getAllFields.invoke(null, ChildHolder.class)).doesNotThrowAnyException();
	}

	@Test
	void is_legacy_date_type_recognises_direct_and_inherited_date_classes() throws Exception {
		Method isLegacyDateType = HibernateEntityDetector.class.getDeclaredMethod("isLegacyDateType", Class.class);
		isLegacyDateType.setAccessible(true);

		assertThat(isLegacyDateType.invoke(null, java.util.Date.class)).isEqualTo(true);
		assertThat(isLegacyDateType.invoke(null, java.sql.Timestamp.class)).isEqualTo(true);
		// java.sql.Time extends java.util.Date but is not itself in the set — exercises hierarchy walk
		assertThat(isLegacyDateType.invoke(null, java.sql.Time.class)).isEqualTo(true);
		assertThat(isLegacyDateType.invoke(null, String.class)).isEqualTo(false);
	}

	@Test
	void logs_debug_message_when_max_depth_is_reached() {
		Logger logger = (Logger) LoggerFactory.getLogger(HibernateEntityDetector.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			Object deeplyNested = nestedMaps(HibernateEntityDetector.MAX_DEPTH + 5, "leaf-value");

			HibernateEntityDetector.checkNotHibernateEntity("payload", deeplyNested, HibernateEntityLogLevel.ERROR);

			assertThat(appender.list)
					.anyMatch(event -> event.getLevel() == Level.DEBUG
							&& event.getFormattedMessage().contains("max depth"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void map_recursion_increases_depth_so_entities_beyond_max_depth_are_unreachable() {
		Object deeplyNested = nestedMaps(HibernateEntityDetector.MAX_DEPTH + 10, new EntityType());

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", deeplyNested, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void list_recursion_increases_depth_so_entities_beyond_max_depth_are_unreachable() {
		Object deeplyNested = nestedLists(HibernateEntityDetector.MAX_DEPTH + 10, new EntityType());

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", deeplyNested, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void array_recursion_increases_depth_so_entities_beyond_max_depth_are_unreachable() {
		Object deeplyNested = nestedArrays(HibernateEntityDetector.MAX_DEPTH + 10, new EntityType());

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", deeplyNested, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void optional_recursion_increases_depth_so_entities_beyond_max_depth_are_unreachable() {
		Object deeplyNested = nestedOptionals(HibernateEntityDetector.MAX_DEPTH + 10, new EntityType());

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", deeplyNested, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void field_recursion_increases_depth_so_entities_beyond_max_depth_are_unreachable() {
		Object deeplyNested = nestedWrappers(HibernateEntityDetector.MAX_DEPTH + 10, new EntityType());

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", deeplyNested, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	private static Object nestedMaps(int depth, Object leaf) {
		Object current = leaf;
		for (int i = 0; i < depth; i++) {
			current = Map.of("k", current);
		}
		return current;
	}

	private static Object nestedLists(int depth, Object leaf) {
		Object current = leaf;
		for (int i = 0; i < depth; i++) {
			current = List.of(current);
		}
		return current;
	}

	private static Object nestedArrays(int depth, Object leaf) {
		Object current = leaf;
		for (int i = 0; i < depth; i++) {
			current = new Object[] {current};
		}
		return current;
	}

	private static Object nestedOptionals(int depth, Object leaf) {
		Object current = leaf;
		for (int i = 0; i < depth; i++) {
			current = Optional.of(current);
		}
		return current;
	}

	private static Object nestedWrappers(int depth, Object leaf) {
		Object current = leaf;
		for (int i = 0; i < depth; i++) {
			current = new Wrapper(current);
		}
		return current;
	}

	@Test
	void detect_stops_recursing_once_max_depth_is_reached() throws Exception {
		Method detect = HibernateEntityDetector.class.getDeclaredMethod(
				"detect",
				String.class,
				String.class,
				Object.class,
				Map.class,
				HibernateEntityLogLevel.class,
				int.class);
		detect.setAccessible(true);
		Wrapper atMaxDepth = new Wrapper(new EntityType());
		Map<Object, Boolean> visited = new java.util.IdentityHashMap<>();

		detect.invoke(
				null,
				"root",
				"root",
				atMaxDepth,
				visited,
				HibernateEntityLogLevel.ERROR,
				HibernateEntityDetector.MAX_DEPTH);

		// the depth gate fires before the wrapper is even marked visited, so its entity field is never reached
		assertThat(visited).isEmpty();
	}

	@Test
	void detect_still_descends_one_level_below_max_depth() throws Exception {
		Method detect = HibernateEntityDetector.class.getDeclaredMethod(
				"detect",
				String.class,
				String.class,
				Object.class,
				Map.class,
				HibernateEntityLogLevel.class,
				int.class);
		detect.setAccessible(true);
		Wrapper belowMaxDepth = new Wrapper(new EntityType());
		Map<Object, Boolean> visited = new java.util.IdentityHashMap<>();

		assertThatThrownBy(() -> detect.invoke(
						null,
						"root",
						"root",
						belowMaxDepth,
						visited,
						HibernateEntityLogLevel.ERROR,
						HibernateEntityDetector.MAX_DEPTH - 1))
				.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void caps_the_number_of_iterable_elements_scanned() {
		List<Object> items = new java.util.ArrayList<>();
		for (int i = 0; i < HibernateEntityDetector.MAX_ELEMENTS_PER_COLLECTION; i++) {
			items.add("ok");
		}
		items.add(new EntityType()); // sits just past the cap, so it must never be reached

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", items, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void still_scans_the_last_iterable_element_within_the_cap() {
		List<Object> items = new java.util.ArrayList<>();
		for (int i = 0; i < HibernateEntityDetector.MAX_ELEMENTS_PER_COLLECTION - 1; i++) {
			items.add("ok");
		}
		items.add(new EntityType()); // last element still within the cap boundary

		assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", items, HibernateEntityLogLevel.ERROR))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void caps_the_number_of_map_entries_scanned() {
		Map<String, Object> entries = new java.util.LinkedHashMap<>();
		for (int i = 0; i < HibernateEntityDetector.MAX_ELEMENTS_PER_COLLECTION; i++) {
			entries.put("key" + i, "ok");
		}
		entries.put("overflow", new EntityType()); // sits just past the cap, so it must never be reached

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", entries, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	@Test
	void caps_the_number_of_array_elements_scanned() {
		Object[] items = new Object[HibernateEntityDetector.MAX_ELEMENTS_PER_COLLECTION + 1];
		java.util.Arrays.fill(items, "ok");
		items[HibernateEntityDetector.MAX_ELEMENTS_PER_COLLECTION] = new EntityType(); // just past the cap

		assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
						"payload", items, HibernateEntityLogLevel.ERROR))
				.doesNotThrowAnyException();
	}

	private static Object readLogLevel(HibernateEntityDetector detector) {
		try {
			var field = HibernateEntityDetector.class.getDeclaredField("logLevel");
			field.setAccessible(true);
			return field.get(detector);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}
}
