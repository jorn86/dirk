package org.hertsig.dirk.processor

import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName

data class InjectableDependency(
    val declaration: KSValueParameter,
    val className: ClassName,
    val provider: Boolean = false,
    val assisted: Boolean = false,
    val factory: InjectableType? = null,
) {
    val name = declaration.name!!.asString()
}
