package org.hertsig.dirk.processor

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hertsig.dirk.Assisted
import org.hertsig.dirk.Injectable
import org.hertsig.dirk.Provider
import org.hertsig.dirk.scope.Scope
import org.hertsig.dirk.scope.Singleton
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.annotation.Generated
import javax.inject.Inject

class DirkProcessor(private val log: KSPLogger, private val generator: CodeGenerator) : SymbolProcessor {
    private lateinit var metadata: List<InjectableType>
    private lateinit var scopes: List<ScopeType>

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val classes = resolver.getSymbolsWithAnnotation(Injectable::class.qualifiedName!!) +
                resolver.getSymbolsWithAnnotation(javax.inject.Singleton::class.qualifiedName!!).filterIsInstance<KSClassDeclaration>()
        val functions = resolver.getSymbolsWithAnnotation(Provider::class.qualifiedName!!) +
                resolver.getSymbolsWithAnnotation(javax.inject.Singleton::class.qualifiedName!!).filterIsInstance<KSFunctionDeclaration>()
        val files = (classes + functions).mapNotNull { it.containingFile }.distinct()
        metadata = classes.mapNotNull { classMetaData(it) }.toList() +
                functions.mapNotNull { functionMetaData(it) }.toList()
        metadata.forEach {
            it.dependencies = it.unresolvedDependencies().mapNotNull { p -> resolveDependency(it, p) }
        }
        scopes = metadata.map { it.scope }.distinct()
        metadata.forEach(::generateFactory)
        if (metadata.isNotEmpty()) {
            generateDirk(files)
        }
        return listOf()
    }

    private fun functionMetaData(function: KSAnnotated) : InjectableFunction? {
        if (function !is KSFunctionDeclaration || function.functionKind != FunctionKind.TOP_LEVEL) {
            log.error("@Provides annotation can only be applied to top level functions", function)
            return null
        }
        if (function.hasAnnotation(javax.inject.Singleton::class)) {
            return InjectableFunction(function, Singleton::class.asClassName())
        }

        val scopeType = function.getAnnotation(Provider::class)!!.arguments.single().value as KSType
        return InjectableFunction(function, scopeType.declaration.asClassName())
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

        if (type.hasAnnotation(javax.inject.Singleton::class)) {
            return InjectableClass(type, constructor, Singleton::class.asClassName())
        }

        val scopeType = type.getAnnotation(Injectable::class)!!.arguments.single().value as KSType
        return InjectableClass(type, constructor, scopeType.declaration.asClassName())
    }

    private fun resolveDependency(type: InjectableType, parameter: KSValueParameter): InjectableDependency? {
        val assisted = parameter.hasAnnotation(Assisted::class)
        var resolved = parameter.type.resolve()
        if (resolved.isError) {
            // assume it's a (not yet) generated factory
            val factory = metadata.singleOrNull { it.factoryClass().simpleName == parameter.type.toString() }
            if (factory != null) {
                return InjectableDependency(parameter, factory.type, true, false, factory)
            }

            log.error("Unable to resolve type for parameter ${parameter.name?.asString()}", parameter)
            return null
        }

        var target = resolved.declaration.asClassName()
        if (assisted) {
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

    private fun generateDirk(files: Sequence<KSFile>) {
        val packageName = metadata.map { it.type.packageName }.distinct().minByOrNull { it.length }!!
        val file = FileSpec.builder(packageName, "Dirk")
            .addType(dirkType(packageName))
            .build()
        val dependencies = Dependencies(true, *files.toList().toTypedArray())
        generator.createNewFile(dependencies, packageName, "Dirk").bufferedWriter().use {
            file.writeTo(it)
        }
    }

    private fun dirkType(packageName: String) = TypeSpec.classBuilder("Dirk")
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
                factory.initializer("%T()", it.factoryClass())
            } else {
                factory.initializer("%T(%N)", it.factoryClass(), it.scope.fieldName)

                addFunction(FunSpec.builder(it.getterName())
                    .returns(it.type)
                    .addCode("return %N.get()", it.factoryField())
                    .build())
            }

            addProperty(factory.build())
        }
        .addFunction(FunSpec.builder("clearScopes")
            .each(scopes) { addStatement("%N.clear()", it.fieldName) }
            .build())
        .addType(TypeSpec.companionObjectBuilder()
            .addFunction(FunSpec.builder("create")
                .returns(ClassName(packageName, "Dirk"))
                .addStatement("val dirk = Dirk()")
                .each(metadata) { field ->
                    field.dependencies.filter { !it.assisted }.forEach {
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
        type.dependencies.forEach {
            if (!it.provider && it.factory?.anyAssisted() == true) {
                log.error("Cannot directly depend on assited injected type, inject the Factory class instead", it.declaration)
            }
        }

        val factory = TypeSpec.classBuilder(type.factoryClass())
            .addGeneratedAnnotation()
            .each(type.dependencies.filter { !it.assisted }.distinctBy { it.factory!!.factoryClass() }) {
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
            factory.primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.INTERNAL).build())

            get.each(type.dependencies.filter { it.assisted }) {
                    addParameter(it.name, it.className)
                }
                .addCode("return ").apply { type.addInjectable(this) }
                .each(type.dependencies) {
                    if (it.assisted) {
                        addCode("%N, ", it.name)
                    } else if (it.provider) {
                        addCode("%N, ", it.factory!!.factoryField())
                    } else {
                        addCode("%N.get(), ", it.factory!!.factoryField())
                    }
                }
                .addStatement(")")
        } else {
            factory
                .primaryConstructor(FunSpec.constructorBuilder()
                    .addModifiers(KModifier.INTERNAL)
                    .addParameter("scope", Scope::class)
                    .build())
                .addProperty(PropertySpec.builder("scope", Scope::class, KModifier.PRIVATE)
                    .initializer("scope")
                    .build())
                .addSuperinterface(javax.inject.Provider::class.asTypeName().parameterizedBy(type.type))

            get.addModifiers(KModifier.OVERRIDE)
                .beginControlFlow("return %N.getScoped(%T::class)", "scope", type.type)
                .apply { type.addInjectable(this) }
                .each(type.dependencies) {
                    if (it.assisted) {
                        log.error("Cannot directly depend on assisted injected type", type.declaration)
                    }
                    val code = if (it.provider) "%N, " else "%N.get(), "
                    addCode(code, it.factory!!.factoryField())
                }
                .addStatement(")")
                .endControlFlow()
        }

        val getter = get.build()
        return factory.addFunction(getter).build()
    }
}

    private fun TypeSpec.Builder.addGeneratedAnnotation() = addAnnotation(AnnotationSpec.builder(Generated::class)
        .addMember("%S, date = %S", DirkProcessor::class.qualifiedName!!,
            ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .build())
}

private inline fun <R, T> R.each(elements: Iterable<T>, block: R.(T) -> Unit): R = apply { elements.forEach { block(it) } }
