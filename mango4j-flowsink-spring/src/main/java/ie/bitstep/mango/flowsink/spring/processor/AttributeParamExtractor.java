package ie.bitstep.mango.flowsink.spring.processor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import ie.bitstep.mango.flowsink.annotations.PushAttribute;
import ie.bitstep.mango.flowsink.annotations.PushContextValue;

public final class AttributeParamExtractor {

	public record AttrCtx(Map<String, Object> attributes, Map<String, Object> context) {}

	private AttributeParamExtractor() {}

	public static AttrCtx extract(ProceedingJoinPoint joinPoint) {
		Map<String, Object> attrs = new LinkedHashMap<>();
		Map<String, Object> context = new LinkedHashMap<>();
		Object[] args = joinPoint.getArgs();
		if (args == null || args.length == 0) {
			return new AttrCtx(attrs, context);
		}
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
		Annotation[][] parameterAnnotations = method.getParameterAnnotations();
		int parameterCount = Math.min(args.length, parameterAnnotations.length);
		for (int index = 0; index < parameterCount; index++) {
			processParameter(args[index], parameterAnnotations[index], attrs, context);
		}
		return new AttrCtx(attrs, context);
	}

	private static void processParameter(
			Object value, Annotation[] annotations, Map<String, Object> attrs, Map<String, Object> context) {
		for (Annotation annotation : annotations) {
			if (annotation instanceof PushAttribute pushAttribute) {
				addToMap(pushAttribute.value(), value, attrs);
			} else if (annotation instanceof PushContextValue pushContextValue) {
				addToMap(pushContextValue.value(), value, context);
			}
		}
	}

	private static void addToMap(String key, Object value, Map<String, Object> target) {
		if (key.isBlank()) {
			return;
		}
		target.put(key, value);
	}
}
