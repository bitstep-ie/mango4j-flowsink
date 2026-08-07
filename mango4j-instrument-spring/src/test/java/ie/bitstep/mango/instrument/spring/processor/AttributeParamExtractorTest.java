package ie.bitstep.mango.instrument.spring.processor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.PushContextValue;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeParamExtractorTest {

	@Test
	void extracts_attribute_and_context_bindings() throws NoSuchMethodException {
		Method method = Sample.class.getDeclaredMethod("annotated", String.class, int.class, String.class);

		AttributeParamExtractor.AttrCtx extracted =
				AttributeParamExtractor.extract(joinPoint(method, new Object[] {"alice", 3, "ignored"}));

		assertThat(extracted.attributes()).containsEntry("user.id", "alice");
		assertThat(extracted.context()).containsEntry("cart.size", 3);
	}

	@Test
	void ignores_empty_arguments_and_blank_keys() throws NoSuchMethodException {
		Method method = Sample.class.getDeclaredMethod("blankKeys", String.class, String.class);

		AttributeParamExtractor.AttrCtx noArgs = AttributeParamExtractor.extract(joinPoint(method, new Object[0]));
		AttributeParamExtractor.AttrCtx blankKeys =
				AttributeParamExtractor.extract(joinPoint(method, new Object[] {"alice", "eur"}));

		assertThat(noArgs.attributes()).isEmpty();
		assertThat(noArgs.context()).isEmpty();
		assertThat(blankKeys.attributes()).isEmpty();
		assertThat(blankKeys.context()).isEmpty();
	}

	@Test
	void extract_returns_empty_when_args_is_null() throws NoSuchMethodException {
		Method method = Sample.class.getDeclaredMethod("annotated", String.class, int.class, String.class);

		AttributeParamExtractor.AttrCtx result = AttributeParamExtractor.extract(joinPoint(method, null));

		assertThat(result.attributes()).isEmpty();
		assertThat(result.context()).isEmpty();
	}

	@Test
	void process_parameter_ignores_non_push_annotations() throws NoSuchMethodException {
		Method method = Sample.class.getDeclaredMethod("nonPushAnnotated", String.class);

		AttributeParamExtractor.AttrCtx result =
				AttributeParamExtractor.extract(joinPoint(method, new Object[] {"alice"}));

		assertThat(result.attributes()).isEmpty();
		assertThat(result.context()).isEmpty();
	}

	@Test
	void passes_entity_values_through_to_attributes_without_early_validation() throws NoSuchMethodException {
		// Validation is deferred to DefaultFlowProcessor.validate() so that the configured
		// FlowAttributeValidator log level is respected. The extractor just extracts.
		Method method = Sample.class.getDeclaredMethod("entityAttr", EntityPayload.class);
		EntityPayload entity = new EntityPayload();

		AttributeParamExtractor.AttrCtx result =
				AttributeParamExtractor.extract(joinPoint(method, new Object[] {entity}));

		assertThat(result.attributes()).containsEntry("payload", entity);
	}

	private static ProceedingJoinPoint joinPoint(Method method, Object[] args) {
		MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
				MethodSignature.class.getClassLoader(),
				new Class<?>[] {MethodSignature.class},
				methodSignatureHandler(method));
		return (ProceedingJoinPoint) Proxy.newProxyInstance(
				ProceedingJoinPoint.class.getClassLoader(),
				new Class<?>[] {ProceedingJoinPoint.class},
				joinPointHandler(signature, args));
	}

	private static InvocationHandler methodSignatureHandler(Method method) {
		return (proxy, invoked, args) -> switch (invoked.getName()) {
			case "getMethod" -> method;
			case "getReturnType" -> method.getReturnType();
			case "toShortString" -> method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(..)";
			case "toLongString", "toString" -> method.toString();
			case "getName" -> method.getName();
			case "getDeclaringType" -> method.getDeclaringClass();
			case "getDeclaringTypeName" -> method.getDeclaringClass().getName();
			case "getParameterTypes" -> method.getParameterTypes();
			case "getParameterNames" -> new String[method.getParameterCount()];
			case "getExceptionTypes" -> method.getExceptionTypes();
			case "getModifiers" -> method.getModifiers();
			default -> defaultValue(invoked.getReturnType());
		};
	}

	private static InvocationHandler joinPointHandler(MethodSignature signature, Object[] args) {
		return (proxy, invoked, ignored) -> switch (invoked.getName()) {
			case "getArgs" -> args;
			case "getSignature" -> signature;
			case "toShortString" -> signature.toShortString();
			case "toLongString", "toString" -> signature.toString();
			case "getKind" -> "method-execution";
			case "proceed" -> null;
			default -> defaultValue(invoked.getReturnType());
		};
	}

	private static Object defaultValue(Class<?> returnType) {
		if (!returnType.isPrimitive()) {
			return null;
		}
		if (returnType == boolean.class) {
			return false;
		}
		if (returnType == char.class) {
			return '\0';
		}
		return 0;
	}

	static class Sample {
		void annotated(
				@PushAttribute("user.id") String userId, @PushContextValue("cart.size") int cartSize, String ignored) {}

		void blankKeys(@PushAttribute(" ") String userId, @PushContextValue("") String currency) {}

		void entityAttr(@PushAttribute("payload") EntityPayload payload) {}

		void nonPushAnnotated(@Deprecated String ignored) {}
	}

	@jakarta.persistence.Entity
	static class EntityPayload {}
}
