package org.hertsig.dirk.scope

import kotlin.reflect.KClass

class Singleton: Scope {
    private val values = mutableMapOf<KClass<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getScoped(key: KClass<T>, provider: () -> T) = values.getOrPut(key, provider) as T
    override fun clear() = values.clear()
}