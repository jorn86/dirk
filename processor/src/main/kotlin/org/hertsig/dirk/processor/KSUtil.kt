package org.hertsig.dirk.processor

import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.ClassName

fun KSDeclaration.asClassName() = ClassName(packageName.asString(), simpleName.asString())
