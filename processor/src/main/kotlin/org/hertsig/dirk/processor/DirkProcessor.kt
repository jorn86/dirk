package org.hertsig.dirk.processor

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Scope
import javax.inject.Provider

class DirkProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) : SymbolProcessor {
    private lateinit var metadata: List<InjectableType>
    private lateinit var scopes: List<ScopeType>

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val classes = resolver.getSymbolsWithAnnotation(Injectable::class.qualifiedName!!)
        val files = classes.mapNotNull { it.containingFile }.distinct()
        metadata = classes
            .mapNotNull { metaData(it) }
            .toList()
        metadata.forEach {
            it.dependencies = it.constructor.parameters.mapNotNull { parameter ->
                var resolved = parameter.type.resolve()
                val provider = resolved.toString().startsWith("Provider<")
                if (provider) {
                    resolved = resolved.arguments.single().type!!.resolve()
                }

                val target = ClassName(resolved.declaration.packageName.asString(), resolved.declaration.simpleName.asString())
                val parameterType = metadata.singleOrNull { m -> m.className == target }
                if (parameterType == null) {
                    logger.error("Cannot find Factory for argument $parameter (${parameter.type}) of ${it.declaration.qualifiedName?.asString()}", parameter)
                    null
                } else {
                    InjectableDependency(provider, parameterType)
                }
            }
        }
        scopes = metadata.map { it.scope }.distinct()
        metadata.forEach(::generateFactory)
        if (metadata.isNotEmpty()) {
            generateDirk(files)
        }
        return listOf()
    }

    private fun metaData(type: KSAnnotated): InjectableType? {
        if (type !is KSClassDeclaration) {
            logger.error("@Injectable annotation can only be applied to classes", type)
            return null
        }
        return InjectableType(type)
    }

    private fun generateDirk(files: Sequence<KSFile>) {
        val packageName = metadata.map { it.packageName }.distinct().minByOrNull { it.length }!!
        val file = FileSpec.builder(packageName, "Dirk")
            .addType(dirkType(packageName))
            .build()
        val dependencies = Dependencies(true, *files.toList().toTypedArray())
        codeGenerator.createNewFile(dependencies, packageName, "Dirk").bufferedWriter().use {
            file.writeTo(it)
        }
    }

    private fun dirkType(packageName: String) = TypeSpec.classBuilder("Dirk")
        .primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).build())
        .each(scopes) { scope ->
            addProperty(PropertySpec
                .builder(scope.fieldName, scope.type, KModifier.PRIVATE)
                .initializer("%T()", scope.type)
                .build())
        }
        .each(metadata) {
            addProperty(PropertySpec
                .builder(it.factoryFieldName, it.factoryClassName, KModifier.PRIVATE)
                .initializer("%T(%N)", it.factoryClassName, it.scope.fieldName)
                .build())

            addFunction(FunSpec.builder(it.getter)
                .returns(it.className)
                .addCode("return %N.get()", it.factoryFieldName)
                .build())
        }
        .addFunction(FunSpec.builder("clearScopes")
            .each(scopes) { addStatement("%N.clear()", it.fieldName) }
            .build())
        .addType(TypeSpec.companionObjectBuilder()
            .addFunction(FunSpec.builder("create")
                .returns(ClassName(packageName, "Dirk"))
                .addStatement("val dirk = Dirk()")
                .each(metadata) { field ->
                    field.dependencies.forEach {
                        addStatement("dirk.%N.%N = dirk.%N", field.factoryFieldName, it.parameter.factoryFieldName, it.parameter.factoryFieldName)
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
        codeGenerator.createNewFile(Dependencies(false), type.packageName, type.factoryTypeName).bufferedWriter().use {
            file.writeTo(it)
        }
        return file
    }

    private fun factoryType(type: InjectableType) = TypeSpec.classBuilder(type.factoryClassName)
        .primaryConstructor(FunSpec.constructorBuilder().addParameter("scope", Scope::class).build())
        .addProperty(PropertySpec.builder("scope", Scope::class, KModifier.PRIVATE)
            .initializer("scope")
            .build())
        .addModifiers(KModifier.INTERNAL)
        .addSuperinterface(Provider::class.asTypeName().parameterizedBy(type.className))
        .each(type.dependencies) {
            val target = it.parameter
            addProperty(PropertySpec
                .builder(target.factoryFieldName, target.factoryClassName, KModifier.INTERNAL, KModifier.LATEINIT)
                .mutable()
                .build())
        }
        .addFunction(FunSpec.builder("get")
            .addModifiers(KModifier.OVERRIDE)
            .addCode("return %N.getScoped(%T::class) { %T(", "scope", type.className, type.className)
            .each(type.dependencies) {
                if (it.provider) {
                    addCode("%N,", it.parameter.factoryFieldName)
                } else {
                    addCode("%N.get(),", it.parameter.factoryFieldName)
                }
            }
            .addStatement(") }")
            .returns(type.className)
            .build())
        .build()
}

private inline fun <R, T> R.each(elements: Iterable<T>, block: R.(T) -> Unit): R = apply { elements.forEach { block(it) } }
