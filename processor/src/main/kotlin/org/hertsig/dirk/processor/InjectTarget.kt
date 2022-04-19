package org.hertsig.dirk.processor

import com.google.devtools.ksp.symbol.*

interface InjectTarget {
    val declaration: KSAnnotated

    fun unresolvedDependencies(): List<KSValueParameter>
}

data class InjectProperty(override val declaration: KSPropertyDeclaration): InjectTarget {
    override fun unresolvedDependencies() = listOfNotNull(declaration.setter?.parameter)
}

data class InjectPropertySetter(override val declaration: KSPropertySetter): InjectTarget {
    override fun unresolvedDependencies() = listOfNotNull(declaration.parameter)
}

data class InjectFunction(override val declaration: KSFunctionDeclaration): InjectTarget {
    override fun unresolvedDependencies() = declaration.parameters
}
