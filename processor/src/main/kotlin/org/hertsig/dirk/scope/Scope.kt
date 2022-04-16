package org.hertsig.dirk.scope

import kotlin.reflect.KClass

interface Scope {
    fun <T: Any> getScoped(key: KClass<T>, provider: () -> T): T
    fun clear()
}
