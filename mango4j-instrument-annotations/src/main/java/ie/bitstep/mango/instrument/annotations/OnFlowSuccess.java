package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a sink method as a handler for the {@code COMPLETED} lifecycle event only — i.e., flows that finished without
 * throwing an exception.
 *
 * <p>Equivalent to {@code @OnFlowLifecycle(OnFlowLifecycle.Lifecycle.COMPLETED)}.
 *
 * <p>Unlike {@link OnFlowCompleted}, this annotation does <strong>not</strong> fire when the flow fails. Use it for
 * success-specific callbacks such as committing a transaction record or emitting a success metric.
 *
 * <p>A {@link Throwable} parameter <strong>cannot</strong> be declared on a success-only handler — the flow has not
 * thrown, so no throwable is available.
 */
@Documented
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OnFlowSuccess {}
