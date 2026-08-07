package ie.bitstep.mango.flowsink.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter to be recorded as a flow attribute.
 *
 * <p><strong>Warning:</strong> the annotated value is forwarded verbatim to every registered sink (logs, exporters,
 * custom handlers) with no redaction. Never annotate a parameter that may carry secrets, tokens, or other sensitive
 * data.
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PushAttribute {
	String value();
}
