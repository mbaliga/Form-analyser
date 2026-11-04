package xyz.mdhv.formanalyser.app.capture

/**
 * Owns a resource whose acquisition completes asynchronously while its UI owner may disappear.
 * Binding and close are serialized so a late completion cannot attach after disposal, and a
 * completion already in progress is released immediately after it finishes.
 */
internal class DeferredResourceBinding<T>(private val release: (T) -> Unit) {
    private var closed = false
    private var resource: T? = null

    @Synchronized
    fun bindIfOpen(resource: T, bind: (T) -> Unit): Boolean {
        if (closed) return false
        this.resource = resource
        bind(resource)
        return true
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        val owned = resource
        resource = null
        owned?.let(release)
    }
}
