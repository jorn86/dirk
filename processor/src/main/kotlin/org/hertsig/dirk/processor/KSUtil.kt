package org.hertsig.dirk.processor

import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.ClassName
import kotlin.reflect.KClass

fun KSDeclaration.asClassName() = ClassName(packageName.asString(), simpleName.asString())
fun KClass<*>.asClassName() = ClassName(java.packageName, simpleName!!)
