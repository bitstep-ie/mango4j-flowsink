package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience annotation for handlers that should run for emitted failed lifecycle events.
 *
 * <p>This is equivalent in intent to targeting {@link OnFlowLifecycle.Lifecycle#FAILED}, but reads more directly for
 * dedicated failure handlers. It is still lifecycle-based, not root-outcome-based.
 */
@Documented
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OnFlowFailure {}
