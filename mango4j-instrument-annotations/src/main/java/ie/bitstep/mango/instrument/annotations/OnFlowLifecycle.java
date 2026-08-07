package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Matches a sink method against the lifecycle of the emitted {@code FlowEvent}.
 *
 * <p>This is event-oriented rather than final-outcome-oriented. A nested {@code @Flow} can emit a
 * {@link Lifecycle#FAILED} event even if an outer caller catches the exception and the overall root flow later
 * completes successfully. {@code @Step}-annotated methods do not emit their own lifecycle events; step failures are
 * recorded as {@code StepEvent} entries inside the parent flow's event.
 *
 * <p>Use this when the handler should react to a specific emitted lifecycle event.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Repeatable(OnFlowLifecycles.class)
public @interface OnFlowLifecycle {
	Lifecycle value();

	enum Lifecycle {
		STARTED,
		COMPLETED,
		FAILED
	}
}
