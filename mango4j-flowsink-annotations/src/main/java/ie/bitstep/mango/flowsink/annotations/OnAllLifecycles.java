package ie.bitstep.mango.flowsink.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Matches a sink type or handler method against every lifecycle event — {@code STARTED}, {@code COMPLETED}, and
 * {@code FAILED}.
 *
 * <p>Prefer this annotation over an exhaustive
 * {@code @OnFlowLifecycles({@OnFlowLifecycle(STARTED), @OnFlowLifecycle(COMPLETED), @OnFlowLifecycle(FAILED)})}
 * listing. If a new lifecycle value is ever added to {@link OnFlowLifecycle.Lifecycle}, this annotation picks it up
 * automatically, whereas an explicit list would need updating.
 *
 * <p>When placed on a type, it restricts all handler methods to the declared set of lifecycles unless overridden at the
 * method level.
 *
 * <p>When placed on a method, it is equivalent to declaring every known lifecycle value on that method directly.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OnAllLifecycles {}
