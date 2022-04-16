package org.hertsig.dirk.processor

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.ClassName
import kotlin.reflect.KClass

fun KSDeclaration.asClassName() = ClassName(packageName.asString(), simpleName.asString())
fun KClass<*>.asClassName() = ClassName(java.packageName, simpleName!!)
fun KSAnnotated.getAnnotation(annotationClass: KClass<*>) = annotations.singleOrNull { it.shortName.asString() == annotationClass.simpleName }
fun KSAnnotated.hasAnnotation(annotationClass: KClass<*>) = getAnnotation(annotationClass) != null
