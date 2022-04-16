package org.hertsig.app

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Service(name: String) {
    @Inject constructor() : this("Default name")
    init {
        println("Service $name created from")
//        Throwable().printStackTrace()
    }
}
