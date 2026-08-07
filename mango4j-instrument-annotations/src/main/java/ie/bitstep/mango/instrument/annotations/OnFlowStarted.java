package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a sink method as a handler for the {@code STARTED} lifecycle event.
 *
 * <p>The method runs once per flow, immediately after the flow begins and before the annotated method body executes.
 * Use it for setup, logging, and request bookkeeping — it fires before any outcome is known.
 *
 * <p>A {@code FlowEvent} parameter may be declared to receive the full event; use
 * {@link ie.bitstep.mango.instrument.annotations.PullAttribute} /
 * {@link ie.bitstep.mango.instrument.annotations.PullContextValue} parameters to extract individual values instead.
 */
@Documented
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OnFlowStarted {}
