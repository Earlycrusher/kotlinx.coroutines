package kotlinx.coroutines

/**
 * A job that can be completed using [complete()] function.
 * It is returned by [Job()][Job] and [SupervisorJob()][SupervisorJob] constructor functions.
 *
 * All functions on this interface are **thread-safe** and can
 * be safely invoked from concurrent coroutines without external synchronization.
 *
 * **The `CompletableJob` interface is not stable for inheritance in 3rd party libraries**,
 * as new methods might be added to this interface in the future, but is stable for use.
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalForInheritanceCoroutinesApi::class)
public interface CompletableJob : Job {
    /**
     * Completes this job. The result is `true` if this job was completed as a result of this invocation and
     * `false` otherwise (if it was already completed).
     *
     * Subsequent invocations of this function have no effect and always produce `false`.
     *
     * This function transitions this job into _completed_ state if it was not completed or cancelled yet.
     * However, that if this job has children, then it transitions into _completing_ state and becomes _complete_
     * once all its children are [complete][isCompleted]. See [Job] for details.
     */
    public fun complete(): Boolean

    /**
     * Completes this job exceptionally with a given [exception]. The result is `true` if this job was
     * completed as a result of this invocation and `false` otherwise (if it was already completed).
     * [exception] parameter is used as an additional debug information that is not handled by any exception handlers.
     *
     * Subsequent invocations of this function have no effect and always produce `false`.
     *
     * This function transitions this job into the _cancelled_ state if it has not been _completed_ or _cancelled_ yet.
     * However, if this job has children, then it transitions into the _cancelling_ state and becomes _cancelled_
     * once all its children are [complete][isCompleted]. See [Job] for details.
     *
     * It is the responsibility of the caller to properly handle and report the given [exception].
     * All the job’s children will receive a [CancellationException] with
     * the [exception] as a cause for the sake of diagnosis.
     */
    public fun completeExceptionally(exception: Throwable): Boolean
}
