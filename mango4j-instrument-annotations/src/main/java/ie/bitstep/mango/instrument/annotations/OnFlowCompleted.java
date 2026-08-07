package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a sink method as a terminal handler — one that runs regardless of whether the flow succeeded or failed.
 *
 * <p><strong>This annotation fires for both {@code COMPLETED} and {@code FAILED} lifecycle events.</strong> It is
 * equivalent to declaring {@code @OnFlowLifecycle(COMPLETED) @OnFlowLifecycle(FAILED)} on the same method, not to
 * {@code @OnFlowLifecycle(COMPLETED)} alone.
 *
 * <ul>
 *   <li>For success-only callbacks use {@link OnFlowSuccess} — lifecycle {@code COMPLETED} only.
 *   <li>For failure-only callbacks use {@link OnFlowFailure} — lifecycle {@code FAILED} only, dispatched before
 *       {@code @OnFlowCompleted} handlers.
 * </ul>
 *
 * <p>A {@link Throwable} parameter may be declared; it receives the failure throwable when the flow failed, and
 * {@code null} when the flow succeeded.
 */
@Documented
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OnFlowCompleted {}
