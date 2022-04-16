@file:OptIn(KspExperimental::class)

package org.hertsig.dirk.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import javax.inject.Inject

data class InjectableType(
    val declaration: KSClassDeclaration,
    val scopeType: ClassName,
) {
    val packageName = declaration.packageName.asString()
    val typeName = declaration.simpleName.asString()
    val className = ClassName(packageName, typeName)
    val factoryTypeName = "${typeName}Factory"
    val factoryFieldName = factoryTypeName.replaceFirstChar { it.lowercase() }
    val factoryClassName = ClassName(packageName, factoryTypeName)
    val getter = "get${typeName}"

    val constructor = declaration.getConstructors().singleOrNull { it.isAnnotationPresent(Inject::class) }
        ?: declaration.primaryConstructor
        ?: throw IllegalStateException("@Injectable ${declaration.qualifiedName} must have a primary constructor or single constructor annotated with @Inject")

    lateinit var dependencies: List<InjectableDependency>

    val scope = ScopeType(scopeType)
    val anyAssisted; get() = dependencies.any { it.assisted }
}
