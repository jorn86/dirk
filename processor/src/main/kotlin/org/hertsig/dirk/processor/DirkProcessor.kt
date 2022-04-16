package org.hertsig.dirk.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hertsig.dirk.Assisted
import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Scope
import javax.inject.Provider

@OptIn(KspExperimental::class)
class DirkProcessor(private val log: KSPLogger, private val generator: CodeGenerator) : SymbolProcessor {
    private lateinit var metadata: List<InjectableType>
    private lateinit var scopes: List<ScopeType>

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val classes = resolver.getSymbolsWithAnnotation(Injectable::class.qualifiedName!!)
        val files = classes.mapNotNull { it.containingFile }.distinct()
        metadata = classes.mapNotNull { metaData(it) }.toList()
        metadata.forEach {
            it.dependencies = it.constructor.parameters.mapNotNull { p -> it.resolveDependency(p) }
        }
        scopes = metadata.map { it.scope }.distinct()
        metadata.forEach(::generateFactory)
        if (metadata.isNotEmpty()) {
            generateDirk(files)
        }
        return listOf()
    }

    private fun InjectableType.resolveDependency(parameter: KSValueParameter): InjectableDependency? {
        val assisted = parameter.isAnnotationPresent(Assisted::class)
        var resolved = parameter.type.resolve()
        if (resolved.isError) {
            // assume it's a (not yet) generated factory
            val factory = metadata.singleOrNull { it.factoryTypeName == parameter.type.toString() }
            if (factory != null) {
                return InjectableDependency(parameter.name!!.asString(), factory.className, true, false, factory.factoryFieldName, factory.factoryClassName)
            }

            log.error("Unable to resolve type for parameter ${parameter.name?.asString()}", parameter)
            return null
        }

        var target = resolved.declaration.asClassName()
        if (assisted) {
            return InjectableDependency(parameter.name!!.asString(), target, false, true)
        }

        val factory = metadata.singleOrNull { it.factoryClassName == target }
        if (factory != null) {
            return InjectableDependency(parameter.name!!.asString(), target, true, false, factory.factoryFieldName, factory.factoryClassName)
        }

        val provider = resolved.toString().startsWith("Provider<")
        if (provider) {
            resolved = resolved.arguments.single().type!!.resolve()
            target = resolved.declaration.asClassName()
        }

        val parameterType = metadata.singleOrNull { m -> m.className == target }
        if (parameterType == null) {
            log.error("Cannot find Factory for argument $parameter (${parameter.type}) of ${declaration.qualifiedName?.asString()}", parameter)
            return null
        }
        return InjectableDependency(parameter.name!!.asString(), target, provider, false, parameterType.factoryFieldName, parameterType.factoryClassName)
    }

    private fun metaData(type: KSAnnotated): InjectableType? {
        if (type !is KSClassDeclaration) {
            log.error("@Injectable annotation can only be applied to classes", type)
            return null
        }

        val injectable = type.annotations.single { it.shortName.asString() == Injectable::class.simpleName!! }
            .arguments.singleOrNull()?.value as? KSType
        return InjectableType(type, injectable!!.declaration.asClassName())
    }

    private fun generateDirk(files: Sequence<KSFile>) {
        val packageName = metadata.map { it.packageName }.distinct().minByOrNull { it.length }!!
        val file = FileSpec.builder(packageName, "Dirk")
            .addType(dirkType(packageName))
            .build()
        val dependencies = Dependencies(true, *files.toList().toTypedArray())
        generator.createNewFile(dependencies, packageName, "Dirk").bufferedWriter().use {
            file.writeTo(it)
        }
    }

    private fun dirkType(packageName: String) = TypeSpec.classBuilder("Dirk")
        .primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).build())
        .each(scopes) { scope ->
            addProperty(PropertySpec
                .builder(scope.fieldName, scope.className, KModifier.PRIVATE)
                .initializer("%T()", scope.className)
                .build())
        }
        .each(metadata) {
            val factory = PropertySpec.builder(it.factoryFieldName, it.factoryClassName)
            if (it.anyAssisted) {
                factory.initializer("%T()", it.factoryClassName)
            } else {
                factory.initializer("%T(%N)", it.factoryClassName, it.scope.fieldName)

                addFunction(FunSpec.builder(it.getter)
                    .returns(it.className)
                    .addCode("return %N.get()", it.factoryFieldName)
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
                        addStatement("dirk.%N.%N = dirk.%N", field.factoryFieldName, it.factoryFieldName!!, it.factoryFieldName)
                    }
                }
                .addStatement("return dirk")
                .build())
            .build())
        .build()

    private fun generateFactory(type: InjectableType): FileSpec {
        val file = FileSpec.builder(type.packageName, type.factoryTypeName)
            .addType(factoryType(type))
            .build()
        generator.createNewFile(Dependencies(false), type.packageName, type.factoryTypeName).bufferedWriter().use {
            file.writeTo(it)
        }
        return file
    }

    private fun factoryType(type: InjectableType): TypeSpec {
        val factory = TypeSpec.classBuilder(type.factoryClassName)
            .each(type.dependencies.filter { !it.assisted }.distinctBy { it.factoryClassName }) {
                addProperty(PropertySpec
                    .builder(it.factoryFieldName!!, it.factoryClassName!!, KModifier.INTERNAL, KModifier.LATEINIT)
                    .mutable()
                    .build())
            }

        val get = FunSpec.builder("get").returns(type.className)

        if (type.anyAssisted) {
            if (type.scope.fieldName != "unscopedScope") {
                log.error("Cannot use assisted injection with scoped injection", type.declaration)
            }
            factory.primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.INTERNAL).build())
            get.each(type.dependencies.filter { it.assisted }) {
                    addParameter(it.name, it.className)
                }
                .addCode("return %T(", type.className)
                .each(type.dependencies) {
                    if (it.assisted) {
                        addCode("%N, ", it.name)
                    } else if (it.provider) {
                        addCode("%N, ", it.factoryFieldName)
                    } else {
                        addCode("%N.get(), ", it.factoryFieldName)
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
                .addSuperinterface(Provider::class.asTypeName().parameterizedBy(type.className))

            get.addModifiers(KModifier.OVERRIDE)
                .beginControlFlow("return %N.getScoped(%T::class)", "scope", type.className)
                .addCode("%T(", type.className)
                .each(type.dependencies) {
                    val code = if (it.provider) "%N, " else "%N.get(), "
                    addCode(code, it.factoryFieldName)
                }
                .addStatement(")")
                .endControlFlow()
        }

        val getter = get.build()
        return factory.addFunction(getter).build()
    }
}

private inline fun <R, T> R.each(elements: Iterable<T>, block: R.(T) -> Unit): R = apply { elements.forEach { block(it) } }
