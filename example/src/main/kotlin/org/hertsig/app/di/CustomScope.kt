package org.hertsig.app.di

import org.hertsig.dirk.scope.Scope
import kotlin.reflect.KClass

class CustomScope : Scope {
    override fun <T : Any> getScoped(key: KClass<T>, provider: () -> T): T {
        provider()
        return provider()
    }

    override fun clear() {}
}