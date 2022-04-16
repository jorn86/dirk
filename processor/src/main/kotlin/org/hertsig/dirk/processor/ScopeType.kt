package org.hertsig.dirk.processor

import org.hertsig.dirk.scope.Scope
import kotlin.reflect.KClass

data class ScopeType(val type: KClass<out Scope>) {
    val fieldName = type.simpleName!!.replaceFirstChar { it.lowercase() } + "Scope"
}
