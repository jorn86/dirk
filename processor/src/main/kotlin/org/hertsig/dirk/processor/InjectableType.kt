package org.hertsig.dirk.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName

interface InjectableType {
    val declaration: KSDeclaration
    val type: ClassName
    var dependencies: List<InjectableDependency>
    var postConstructFunctions: List<KSFunctionDeclaration>
    val scope: ScopeType

    fun getterName() = "get${type.simpleName}"
    fun factoryClass() = ClassName(type.packageName, "${type.simpleName}Factory")
    fun factoryField() = factoryClass().simpleName.replaceFirstChar { it.lowercase() }
    fun addInjectable(funSpec: FunSpec.Builder): FunSpec.Builder
    fun anyAssisted() = dependencies.any { it.assisted }
    fun unresolvedDependencies(): List<KSValueParameter>
}

data class InjectableFunction(
    override val declaration: KSFunctionDeclaration,
    private val scopeType: ClassName,
): InjectableType {
    val returnType = declaration.returnType!!.resolve()
    override val type = returnType.declaration.asClassName()
    private val createFunction = MemberName(declaration.packageName.asString(), declaration.simpleName.asString())

    override lateinit var dependencies: List<InjectableDependency>
    override lateinit var postConstructFunctions: List<KSFunctionDeclaration>
    override val scope = ScopeType(scopeType)

    override fun unresolvedDependencies() = declaration.parameters
    override fun addInjectable(funSpec: FunSpec.Builder) = funSpec.addCode("%M(", createFunction)
}

data class InjectableClass(
    override val declaration: KSClassDeclaration,
    val constructor: KSFunctionDeclaration,
    private val scopeType: ClassName,
): InjectableType {
    override val type = declaration.asClassName()

    override lateinit var dependencies: List<InjectableDependency>
    override lateinit var postConstructFunctions: List<KSFunctionDeclaration>
    override val scope = ScopeType(scopeType)

    override fun unresolvedDependencies() = constructor.parameters
    override fun addInjectable(funSpec: FunSpec.Builder) = funSpec.addCode("%T(", type)
}
