package org.hertsig.dirk.scope

import kotlin.reflect.KClass

class Unscoped: Scope {
    override fun <T: Any> getScoped(key: KClass<T>, provider: () -> T) = provider()
    override fun clear() {}
}
