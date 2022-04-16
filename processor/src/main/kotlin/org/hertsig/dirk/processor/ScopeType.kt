package org.hertsig.dirk.processor

import com.squareup.kotlinpoet.ClassName

data class ScopeType(val className: ClassName) {
    val fieldName = className.simpleName.replaceFirstChar { it.lowercase() } + "Scope"
}
