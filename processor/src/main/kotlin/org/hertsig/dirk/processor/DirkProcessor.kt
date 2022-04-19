package org.hertsig.dirk.processor

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hertsig.dirk.Assisted
import org.hertsig.dirk.Injectable
import org.hertsig.dirk.Provides
import org.hertsig.dirk.scope.Scope
import org.hertsig.dirk.scope.Singleton
import org.hertsig.dirk.scope.Unscoped
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.annotation.Generated
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import kotlin.reflect.KClass

class DirkProcessor(private val log: KSPLogger, private val generator: CodeGenerator) : SymbolProcessor {
    private lateinit var metadata: List<InjectableType>
    private lateinit var scopes: List<ScopeType>
    private val dirkPackage by lazy { metadata.map { it.type.packageName }.distinct().minByOrNull { it.length }!! }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val injectAnnotated = resolver.getAnnotatedSymbols<KSAnnotated>(Inject::class)
        val injectConstructors = injectAnnotated.filter { it is KSFunctionDeclaration && it.isConstructor() }
            .mapNotNull { it.parent as? KSClassDeclaration }

        val classes = (resolver.getAnnotatedSymbols<KSClassDeclaration>(Injectable::class, javax.inject.Singleton::class) + injectConstructors).distinct()
        val providerFunctions = resolver.getAnnotatedSymbols<KSFunctionDeclaration>(Provides::class, javax.inject.Singleton::class)
        val injectTargets = injectAnnotated.filterNot { it is KSFunctionDeclaration && it.isConstructor() }

        metadata = (classes.mapNotNull { classMetaData(it) } + providerFunctions.mapNotNull { functionMetaData(it) })
        metadata.forEach {
            it.dependencies = it.unresolvedDependencies().mapNotNull { p -> resolveDependency(it.declaration, p) }
        }

        scopes = metadata.map { it.scope }.distinct()

        val files = (classes + providerFunctions).mapNotNull { it.containingFile }.distinct()
        metadata.forEach(::generateFactory)
        if (metadata.isNotEmpty()) {
            generateDirk(files, injectTargets)
        }
        return listOf()
    }

    private fun functionMetaData(function: KSAnnotated) : InjectableFunction? {
        if (function !is KSFunctionDeclaration || function.functionKind != FunctionKind.TOP_LEVEL) {
            log.error("@Provides annotation can only be applied to top level functions", function)
            return null
        }
        val metaData = if (function.hasAnnotation(javax.inject.Singleton::class)) {
            InjectableFunction(function, Singleton::class.asClassName())
        } else {
            val scopeType = function.getAnnotation(Provides::class)!!.arguments.single().value as KSType
            InjectableFunction(function, scopeType.declaration.asClassName())
        }
        resolveLifecycle(metaData.returnType.declaration as KSClassDeclaration, metaData)
        return metaData
    }

    private fun classMetaData(type: KSAnnotated): InjectableClass? {
        if (type !is KSClassDeclaration) {
            log.error("@Injectable annotation can only be applied to classes", type)
            return null
        }

        val constructor = type.getConstructors().singleOrNull { it.hasAnnotation(Inject::class) }
            ?: type.primaryConstructor
        if (constructor == null) {
            log.error("@Injectable must have a primary constructor or single constructor annotated with @Inject", type)
            return null
        }

        val metaData = if (type.hasAnnotation(javax.inject.Singleton::class)) {
            InjectableClass(type, constructor, Singleton::class.asClassName())
        } else {
            val declaredScope = type.getAnnotation(Injectable::class)
                ?.let { (it.arguments.single().value as KSType).declaration.asClassName() }
            InjectableClass(type, constructor, declaredScope ?: Unscoped::class.asClassName())
        }
        resolveLifecycle(type, metaData)
        return metaData
    }

    private fun resolveLifecycle(type: KSClassDeclaration, metaData: InjectableType) {
        val functions = type.getAllFunctions()
        metaData.postConstructFunctions = functions.filter { it.hasAnnotation(PostConstruct::class) }
            .onEach { if (it.parameters.isNotEmpty()) log.error("PostConstruct functions must not have parameters", it) }
            .toList()
        metaData.preDestroyFunctions = functions.filter { it.hasAnnotation(PreDestroy::class) }
            .onEach { if (it.parameters.isNotEmpty()) log.error("PreDestroy functions must not have parameters", it) }
            .toList()
    }

    private fun resolveDependency(type: KSNode, parameter: KSValueParameter): Dependency? {
        var resolved = parameter.type.resolve()
        if (resolved.isError) {
            val dtype = if (parameter.parent is KSPropertySetter) {
                ((parameter.parent as KSPropertySetter).parent as KSPropertyDeclaration).type
            } else {
                parameter.type
            }
            if (dtype.toString() == "Dirk") {
                return DirkInjectable
            }

            // assume it's a (not yet) generated factory
            val factory = metadata.singleOrNull { it.factoryClass().simpleName == parameter.type.toString() }
            if (factory != null) {
                return InjectableDependency(parameter, factory.type, true, false, factory)
            }

            val location = if (parameter.parent is KSPropertySetter) type else parameter
            log.error("Unable to resolve type for parameter ${parameter.name?.asString()}", location)
            return null
        }

        var target = resolved.declaration.asClassName()
        if (parameter.hasAnnotation(Assisted::class)) {
            return InjectableDependency(parameter, target, false, true)
        }

        val factory = metadata.singleOrNull { it.factoryClass() == target }
        if (factory != null) {
            return InjectableDependency(parameter, target, true, false, factory)
        }

        val provider = resolved.toString().startsWith("Provider<")
        if (provider) {
            resolved = resolved.arguments.single().type!!.resolve()
            target = resolved.declaration.asClassName()
        }

        val parameterType = metadata.singleOrNull { m -> m.type == target }
        if (parameterType == null) {
            log.error("Cannot find Factory for argument $parameter (${parameter.type}) of $type", parameter)
            return null
        }
        return InjectableDependency(parameter, target, provider, false, parameterType)
    }

    private fun generateDirk(files: List<KSFile>, injectTargets: List<KSAnnotated>) {
        val file = FileSpec.builder(dirkPackage, "Dirk")
            .addType(dirkType(dirkPackage, injectTargets))
            .build()
        val dependencies = Dependencies(true, *files.toTypedArray())
        generator.createNewFile(dependencies, dirkPackage, "Dirk").bufferedWriter().use {
            file.writeTo(it)
        }
    }

    private fun dirkType(packageName: String, injectTargets: List<KSAnnotated>) = TypeSpec.classBuilder("Dirk")
            .addGeneratedAnnotation()
            .primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).build())
            .each(scopes) { scope ->
                addProperty(PropertySpec
                    .builder(scope.fieldName, scope.className, KModifier.PRIVATE)
                    .initializer("%T()", scope.className)
                    .build())
            }
            .each(metadata) {
                val factory = PropertySpec.builder(it.factoryField(), it.factoryClass())
                if (it.anyAssisted()) {
                    factory.initializer("%T(this)", it.factoryClass())
                } else {
                    factory.initializer("%T(this, %N)", it.factoryClass(), it.scope.fieldName)

                    addFunction(FunSpec.builder(it.getterName())
                        .returns(it.type)
                        .addCode("return %N.get()", it.factoryField())
                        .build())
                }

                addProperty(factory.build())
            }
            .each(injectTargets.groupBy { it.findContainingClass() }.entries) { (owner, injectionPoints) ->
                addFunction(FunSpec.builder("inject" + owner.simpleName.asString())
                    .addParameter("instance", owner.asClassName())
                    .each(injectionPoints) {
                        when (it) {
                            is KSPropertySetter -> {
                                val dep = resolveDependency(it, it.parameter)
                                if (dep != null) {
                                    val property = it.parent as KSPropertyDeclaration
                                    addCode("instance.%N = ", property.simpleName.asString()).dependencyValue(dep, "this").addStatement("")
                                }
                            }
                            is KSPropertyDeclaration -> {
                                val dep = resolveDependency(it, it.setter!!.parameter)
                                if (dep != null) {
                                    addCode("instance.%N = ", it.simpleName.asString()).dependencyValue(dep, "this").addStatement("")
                                }
                            }
                            is KSFunctionDeclaration -> {
                                addCode("instance.%N(", it.simpleName.asString())
                                it.parameters.mapNotNull { p -> resolveDependency(it, p) }
                                    .forEach { p -> dependencyValue(p).addCode(", ") }
                                addStatement(")")
                            }
                            else -> log.error("Invalid @Inject annotation", it)
                        }
                    }
                    .build())


//                    .addParameter("instance", "Any")
//                    .each(params) { p ->
//                        val dependency = resolveDependency(it, p)
//                        if (dependency != null) {
//                            addParameter(p)
//                            addStatement("%N.inject(%N)", dependency.factoryField(), p.name)
//                        }
//                    }
            }
            .addFunction(FunSpec.builder("clearScopes")
                .addModifiers(KModifier.PRIVATE)
                .each(scopes) { addStatement("%N.clear()", it.fieldName) }
                .build())
            .addProperty(PropertySpec.builder("destroyHooks", destroyHooksType)
                .addModifiers(KModifier.PRIVATE)
                .initializer("mutableListOf()")
                .build())
            .addFunction(FunSpec.builder("destroy")
                .addStatement("destroyHooks.forEach { it() }")
                .addStatement("destroyHooks.clear()")
                .addStatement("clearScopes()")
                .build())
            .addFunction(FunSpec.builder("addDestroyHook")
                .addModifiers(KModifier.INTERNAL)
                .addParameter("hook", destroyHookType)
                .addStatement("%N.add(%N)", "destroyHooks", "hook")
                .build())
            .addType(TypeSpec.companionObjectBuilder()
                .addFunction(FunSpec.builder("create")
                    .returns(ClassName(packageName, "Dirk"))
                    .addStatement("val dirk = Dirk()")
                    .each(metadata) { field ->
                        field.dependencies.filterIsInstance<InjectableDependency>().filter { !it.assisted }.forEach {
                            addStatement("dirk.%N.%N = dirk.%N", field.factoryField(), it.factory!!.factoryField(), it.factory.factoryField())
                        }
                    }
                    .addStatement("return dirk")
                    .build())
                .build())
            .build()

    private fun generateFactory(type: InjectableType): FileSpec {
        val file = FileSpec.builder(type.factoryClass().packageName, type.factoryClass().simpleName)
            .addType(factoryType(type))
            .build()
        generator.createNewFile(Dependencies(false), type.factoryClass().packageName, type.factoryClass().simpleName).bufferedWriter().use {
            file.writeTo(it)
        }
        return file
    }

    private fun factoryType(type: InjectableType): TypeSpec {
        type.dependencies.filterIsInstance<InjectableDependency>().forEach {
            if (!it.provider && it.factory?.anyAssisted() == true) {
                log.error("Cannot directly depend on assited injected type, inject the Factory class instead", it.declaration)
            }
        }

        val factory = TypeSpec.classBuilder(type.factoryClass())
            .addGeneratedAnnotation()
            .addProperty(PropertySpec.builder("dirk", ClassName(dirkPackage, "Dirk"))
                .addModifiers(KModifier.PRIVATE)
                .initializer("dirk")
                .build())
            .each(type.dependencies
                .filterIsInstance<InjectableDependency>()
                .filter { !it.assisted }
                .distinctBy { it.factory!!.factoryClass() }) {
                addProperty(PropertySpec
                    .builder(it.factory!!.factoryField(), it.factory.factoryClass(), KModifier.INTERNAL, KModifier.LATEINIT)
                    .mutable()
                    .build())
            }

        val get = FunSpec.builder("get").returns(type.type)

        if (type.anyAssisted()) {
            if (type.scope.fieldName != "unscopedScope") {
                log.error("Cannot use assisted injection with scoped injection", type.declaration)
            }
            factory.primaryConstructor(FunSpec.constructorBuilder()
                .addModifiers(KModifier.INTERNAL)
                .addParameter("dirk", ClassName(dirkPackage, "Dirk"))
                .build())

            get.each(type.dependencies.filterIsInstance<InjectableDependency>().filter { it.assisted }) {
                    addParameter(it.name, it.className)
                }
                .addCode("return ").apply { type.addInjectable(this) }
                .each(type.dependencies) { dependencyValue(it).addCode(", ") }
                .callLifecycle(type)
        } else {
            factory
                .primaryConstructor(FunSpec.constructorBuilder()
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("dirk", ClassName(dirkPackage, "Dirk"))
                    .addParameter("scope", Scope::class)
                    .build())
                .addProperty(PropertySpec.builder("scope", Scope::class, KModifier.PRIVATE)
                    .initializer("scope")
                    .build())
                .addSuperinterface(javax.inject.Provider::class.asTypeName().parameterizedBy(type.type))

            get.addModifiers(KModifier.OVERRIDE)
                .beginControlFlow("return %N.getScoped(%T::class)", "scope", type.type)
                .apply { type.addInjectable(this) }
                .each(type.dependencies) { dependencyValue(it).addCode(", ") }
                .callLifecycle(type)
                .endControlFlow()
        }

        val getter = get.build()
        return factory.addFunction(getter).build()
    }

    private fun FunSpec.Builder.dependencyValue(it: Dependency, dirkName: String = "dirk") =
        if (it !is InjectableDependency) {
            addCode("%L", dirkName)
        } else if (it.assisted) {
            addCode("%N", it.name)
        } else if (it.provider) {
            addCode("%N", it.factory!!.factoryField())
        } else {
            addCode("%N.get()", it.factory!!.factoryField())
        }

    private fun FunSpec.Builder.callLifecycle(type: InjectableType): FunSpec.Builder {
        return if (type.postConstructFunctions.isEmpty() && type.preDestroyFunctions.isEmpty()) {
            addStatement(")")
        } else {
            addCode(")").beginControlFlow(".also")
                .each(type.postConstructFunctions) { addStatement("it.%N()", it.simpleName.asString()) }
                .each(type.preDestroyFunctions) {
                    addStatement("%N.addDestroyHook(it::%N)", "dirk", it.simpleName.asString())
                }
                .endControlFlow()
        }
    }

    private fun TypeSpec.Builder.addGeneratedAnnotation() = addAnnotation(AnnotationSpec.builder(Generated::class)
        .addMember("%S, date = %S", DirkProcessor::class.qualifiedName!!,
            ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .build())

    private inline fun <reified T: KSAnnotated> Resolver.getAnnotatedSymbols(vararg annotations: KClass<*>) =
        annotations.flatMap { getSymbolsWithAnnotation(it.qualifiedName!!) }
            .filterIsInstance<T>()
            .distinct()

    private tailrec fun KSNode.findContainingClass(): KSClassDeclaration = when (this) {
        is KSClassDeclaration -> this
        is KSFunctionDeclaration -> parent!!.findContainingClass()
        is KSPropertyDeclaration -> parent!!.findContainingClass()
        is KSPropertySetter -> parent!!.findContainingClass()
        else -> throw java.lang.IllegalArgumentException("Unsupported type: $javaClass $this")
    }

    companion object {
        private val destroyHookType = LambdaTypeName.get(returnType = Unit::class.asTypeName())
        private val destroyHooksType = ClassName("kotlin.collections", "MutableList").parameterizedBy(destroyHookType)
    }
}

private inline fun <R, T> R.each(elements: Iterable<T>, block: R.(T) -> Unit): R = apply { elements.forEach { block(it) } }
