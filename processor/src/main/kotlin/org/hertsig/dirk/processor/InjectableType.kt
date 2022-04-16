@file:OptIn(KspExperimental::class)

package org.hertsig.dirk.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Singleton
import org.hertsig.dirk.scope.Thread
import org.hertsig.dirk.scope.Unscoped
import javax.inject.Inject

data class InjectableType(
    val declaration: KSClassDeclaration
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

    private val scopeType = try {
        declaration.getAnnotationsByType(Injectable::class).single().scope
    } catch (e: KSTypeNotPresentException) {
        when (e.ksType.toString()) {
            "Singleton" -> Singleton::class
            "Thread" -> Thread::class
            "Unscoped" -> Unscoped::class
            else -> Unscoped::class
        }
    }
    val scope = ScopeType(scopeType)
}

data class InjectableDependency(val provider: Boolean, val parameter: InjectableType)
