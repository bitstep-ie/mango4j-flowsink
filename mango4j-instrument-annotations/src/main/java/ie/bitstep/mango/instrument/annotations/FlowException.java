package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls which throwable is injected into a {@link Throwable} parameter of a failure handler.
 *
 * <p>Handlers for {@code @OnFlowFailure} (or {@code @OnFlowLifecycle(FAILED)}) can declare a {@link Throwable}
 * parameter without any annotation. An unannotated {@link Throwable} parameter and one annotated with
 * {@code @FlowException(Source.THROWN)} receive exactly the same value — the exception as thrown. <strong>This
 * annotation is only meaningful when {@link Source#ROOT_CAUSE} is specified.</strong>
 *
 * <pre>{@code
 * // These two declarations are equivalent:
 * void onFailure(Throwable error) { ... }
 * void onFailure(@FlowException Throwable error) { ... }      // Source.THROWN is the default
 *
 * // Only this form changes behaviour — walks getCause() to the ultimate root:
 * void onFailure(@FlowException(Source.ROOT_CAUSE) Throwable root) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowException {
	Source value() default Source.THROWN;

	enum Source {
		/** The exception as thrown — equivalent to {@code event.throwable()}. */
		THROWN,
		/** Walk {@link Throwable#getCause()} to the ultimate root cause and inject that instead. */
		ROOT_CAUSE
	}
}
