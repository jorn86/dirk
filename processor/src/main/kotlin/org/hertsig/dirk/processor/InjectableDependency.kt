package org.hertsig.dirk.processor

import com.squareup.kotlinpoet.ClassName

data class InjectableDependency(
    val name: String,
    val className: ClassName,
    val provider: Boolean = false,
    val assisted: Boolean = false,
    val factoryFieldName: String? = null,
    val factoryClassName: ClassName? = null,
)
