@file:OptIn(KspExperimental::class)

package org.hertsig.dirk.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Scope
import org.hertsig.dirk.scope.Singleton
import org.hertsig.dirk.scope.Thread
import org.hertsig.dirk.scope.Unscoped
import javax.inject.Inject
import kotlin.reflect.KClass

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

data class InjectableDependency(
    val name: String,
    val className: ClassName,
    val provider: Boolean = false,
    val assisted: Boolean = false,
    val factoryFieldName: String? = null,
    val factoryClassName: ClassName? = null,
)
