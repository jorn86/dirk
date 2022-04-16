package org.hertsig.dirk.scope

import kotlin.concurrent.getOrSet
import kotlin.reflect.KClass

class Thread: Scope {
    private val values = mutableMapOf<KClass<*>, ThreadLocal<*>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getScoped(key: KClass<T>, provider: () -> T): T {
        val local = values.getOrPut(key) { ThreadLocal<T>() }.get() as ThreadLocal<T>
        return local.getOrSet(provider)
    }

    override fun clear() = values.clear()
}