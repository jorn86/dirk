package org.hertsig.dirk

import org.hertsig.dirk.scope.Scope
import org.hertsig.dirk.scope.Unscoped
import kotlin.reflect.KClass

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class Provides(val scope: KClass<out Scope> = Unscoped::class)
