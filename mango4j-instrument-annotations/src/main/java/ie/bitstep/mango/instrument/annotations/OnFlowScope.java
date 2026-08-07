package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a sink type or handler method to flow names that match this scope prefix.
 *
 * <p>When present on both a type and a method, the flow name must satisfy <em>both</em> — the method scope narrows the
 * class scope rather than replacing it. To widen the method scope beyond the class scope, move the broader pattern to
 * the class and use a narrower pattern (or no annotation) on the method.
 *
 * <p>Matching rules:
 *
 * <ul>
 *   <li>Blank value — matches any flow name.
 *   <li>Trailing dot (e.g. {@code "checkout."}) — matches the prefix and all nested names.
 *   <li>Exact name (e.g. {@code "checkout"}) — matches that name and names that start with {@code "checkout."}.
 * </ul>
 *
 * <p>This annotation is repeatable; multiple scopes on the same element are combined with OR — the flow name need only
 * match one of them.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(OnFlowScopes.class)
public @interface OnFlowScope {
	String value();
}
