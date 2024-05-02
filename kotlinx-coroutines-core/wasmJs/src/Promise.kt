package kotlinx.coroutines

import kotlin.coroutines.*
import kotlin.js.*

@Suppress("UNUSED_PARAMETER")
internal fun promiseSetDeferred(promise: Promise<JsAny?>, deferred: JsAny): Unit =
    js("promise.deferred = deferred")

@Suppress("UNUSED_PARAMETER")
internal fun promiseGetDeferred(promise: Promise<JsAny?>): JsAny? = js("""{
    console.assert(promise instanceof Promise, "promiseGetDeferred must receive a promise, but got ", promise);
    return promise.deferred == null ? null : promise.deferred; 
}""")


/**
 * Starts new coroutine and returns its result as an implementation of [Promise].
 *
 * Coroutine context is inherited from a [CoroutineScope], additional context elements can be specified with [context] argument.
 * If the context does not have any dispatcher nor any other [ContinuationInterceptor], then [Dispatchers.Default] is used.
 * The parent job is inherited from a [CoroutineScope] as well, but it can also be overridden
 * with corresponding [context] element.
 *
 * By default, the coroutine is immediately scheduled for execution.
 * Other options can be specified via `start` parameter. See [CoroutineStart] for details.
 *
 * @param context additional to [CoroutineScope.coroutineContext] context of the coroutine.
 * @param start coroutine start option. The default value is [CoroutineStart.DEFAULT].
 * @param block the coroutine code.
 */
public fun <T> CoroutineScope.promise(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Promise<JsAny?> =
    async(context, start, block).asPromise()

/**
 * Converts this deferred value to the instance of [Promise<JsAny?>].
 */
public fun <T> Deferred<T>.asPromise(): Promise<JsAny?> {
    val promise = Promise<JsAny?> { resolve, reject ->
        invokeOnCompletion {
            val e = getCompletionExceptionOrNull()
            if (e != null) {
                reject(e.toJsReference())
            } else {
                resolve(getCompleted()?.toJsReference())
            }
        }
    }
    promiseSetDeferred(promise, this.toJsReference())
    return promise
}

/**
 * Converts this promise value to the instance of [Deferred].
 */
@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
public fun <T> Promise<JsAny?>.asDeferred(): Deferred<T> {
    val deferred = promiseGetDeferred(this) as? JsReference<Deferred<T>>
    return deferred?.get() ?: GlobalScope.async(start = CoroutineStart.UNDISPATCHED) { await() }
}

/**
 * Awaits for completion of the promise without blocking.
 *
 * This suspending function is cancellable: if the [Job] of the current coroutine is cancelled while this
 * suspending function is waiting on the promise, this function immediately resumes with [CancellationException].
 * There is a **prompt cancellation guarantee**: even if this function is ready to return the result, but was cancelled
 * while suspended, [CancellationException] will be thrown. See [suspendCancellableCoroutine] for low-level details.
 */
@Suppress("UNCHECKED_CAST")
public suspend fun <T> Promise<JsAny?>.await(): T = suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
    this@await.then(
        onFulfilled = { cont.resume(it as T); null },
        onRejected = { cont.resumeWithException(it.toThrowableOrNull() ?: Exception("Non-Kotlin exception $it")); null }
    )
}
