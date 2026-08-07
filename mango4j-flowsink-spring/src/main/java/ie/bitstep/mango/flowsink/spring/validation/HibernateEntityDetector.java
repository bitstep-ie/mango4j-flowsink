package ie.bitstep.mango.flowsink.spring.validation;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ie.bitstep.mango.flowsink.validation.FlowAttributeValidator;

public class HibernateEntityDetector implements FlowAttributeValidator {
	private static final Logger log = LoggerFactory.getLogger(HibernateEntityDetector.class);
	private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
			Boolean.class,
			Byte.class,
			Short.class,
			Integer.class,
			Long.class,
			Float.class,
			Double.class,
			Character.class);
	private static final Set<String> HIBERNATE_PROXY_MARKERS = Set.of("$$_javassist_", "_$$_jvst", "$HibernateProxy$");
	private static final Set<String> ENTITY_ANNOTATIONS =
			Set.of("org.hibernate.annotations.Entity", "jakarta.persistence.Entity", "javax.persistence.Entity");
	private static final Set<String> LEGACY_DATE_CLASS_NAMES =
			Set.of("java.util.Date", "java.sql.Date", "java.sql.Timestamp", "java.util.Calendar");

	/** Bounds recursion so a deeply nested attacker-influenced object graph cannot trigger a StackOverflowError. */
	static final int MAX_DEPTH = 50;

	/** Bounds per-collection scanning so a very large attribute value cannot turn this check into a CPU sink. */
	static final int MAX_ELEMENTS_PER_COLLECTION = 1000;

	private final HibernateEntityLogLevel logLevel;

	public HibernateEntityDetector(HibernateEntityLogLevel logLevel) {
		this.logLevel = logLevel == null ? HibernateEntityLogLevel.ERROR : logLevel;
	}

	@Override
	public void validate(String key, Object value) {
		checkNotHibernateEntity(key, value, logLevel);
	}

	public static void checkNotHibernateEntity(String key, Object value, HibernateEntityLogLevel level) {
		if (value == null) {
			return;
		}
		detect(key, key, value, new IdentityHashMap<>(), level == null ? HibernateEntityLogLevel.ERROR : level, 0);
	}

	private static void detect(
			String root,
			String path,
			Object value,
			Map<Object, Boolean> visited,
			HibernateEntityLogLevel level,
			int depth) {
		if (value == null || visited.containsKey(value)) {
			return;
		}
		Class<?> type = value.getClass();
		if (isHibernateEntity(type)) {
			String message = String.format(
					"Hibernate entity detected in flow attribute '%s' at path '%s'. Class: %s",
					root, path, type.getName());
			reportEntity(message, level);
			return;
		}
		if (isTerminal(type)) {
			return;
		}
		if (depth >= MAX_DEPTH) {
			log.debug("Stopped scanning '{}' at path '{}': max depth {} reached", root, path, MAX_DEPTH);
			return;
		}

		visited.put(value, Boolean.TRUE);

		if (value instanceof Map<?, ?> map) {
			int count = 0;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (count++ >= MAX_ELEMENTS_PER_COLLECTION) {
					break;
				}
				detect(root, path + "[" + entry.getKey() + "]", entry.getValue(), visited, level, depth + 1);
			}
			return;
		}
		if (value instanceof Iterable<?> iterable) {
			int index = 0;
			for (Object entry : iterable) {
				if (index >= MAX_ELEMENTS_PER_COLLECTION) {
					break;
				}
				detect(root, path + "[" + index + "]", entry, visited, level, depth + 1);
				index++;
			}
			return;
		}
		if (type.isArray()) {
			detectArray(root, path, value, visited, level, depth);
			return;
		}
		if (value instanceof Optional<?> optional) {
			optional.ifPresent(nested -> detect(root, path + ".value", nested, visited, level, depth + 1));
			return;
		}

		detectFields(root, path, value, type, visited, level, depth);
	}

	private static void reportEntity(String message, HibernateEntityLogLevel level) {
		if (level == HibernateEntityLogLevel.ERROR) {
			throw new IllegalArgumentException(message);
		}
		if (level == HibernateEntityLogLevel.WARN) {
			log.warn(message);
		} else {
			log.info(message);
		}
	}

	private static void detectArray(
			String root,
			String path,
			Object value,
			Map<Object, Boolean> visited,
			HibernateEntityLogLevel level,
			int depth) {
		int length = Math.min(java.lang.reflect.Array.getLength(value), MAX_ELEMENTS_PER_COLLECTION);
		for (int index = 0; index < length; index++) {
			detect(
					root,
					path + "[" + index + "]",
					java.lang.reflect.Array.get(value, index),
					visited,
					level,
					depth + 1);
		}
	}

	@SuppressWarnings("java:S3011")
	private static void detectFields(
			String root,
			String path,
			Object value,
			Class<?> type,
			Map<Object, Boolean> visited,
			HibernateEntityLogLevel level,
			int depth) {
		for (Field field : getAllFields(type)) {
			if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
				continue;
			}
			try {
				if (!field.canAccess(value)) {
					field.setAccessible(true);
				}
				detect(root, path + "." + field.getName(), field.get(value), visited, level, depth + 1);
			} catch (IllegalAccessException | InaccessibleObjectException e) {
				log.debug("Cannot access field {} on {}: {}", field.getName(), type.getName(), e.getMessage());
			}
		}
	}

	private static boolean isHibernateEntity(Class<?> type) {
		for (String marker : HIBERNATE_PROXY_MARKERS) {
			if (type.getName().contains(marker)) {
				return true;
			}
		}
		for (java.lang.annotation.Annotation annotation : type.getAnnotations()) {
			if (ENTITY_ANNOTATIONS.contains(annotation.annotationType().getName())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isTerminal(Class<?> type) {
		return type.isPrimitive()
				|| SIMPLE_TYPES.contains(type)
				|| CharSequence.class.isAssignableFrom(type)
				|| Number.class.isAssignableFrom(type)
				|| Enum.class.isAssignableFrom(type)
				|| UUID.class.isAssignableFrom(type)
				|| isLegacyDateType(type)
				|| TemporalAccessor.class.isAssignableFrom(type)
				|| Class.class.isAssignableFrom(type);
	}

	private static boolean isLegacyDateType(Class<?> type) {
		Class<?> current = type;
		while (current != null) {
			if (LEGACY_DATE_CLASS_NAMES.contains(current.getName())) {
				return true;
			}
			current = current.getSuperclass();
		}
		return false;
	}

	private static Iterable<Field> getAllFields(Class<?> type) {
		if (type == null || Object.class.equals(type)) {
			return Collections.emptyList();
		}
		Deque<Field> fields = new LinkedList<>();
		Class<?> current = type;
		while (current != null && !Object.class.equals(current)) {
			Collections.addAll(fields, current.getDeclaredFields());
			current = current.getSuperclass();
		}
		return fields;
	}
}
