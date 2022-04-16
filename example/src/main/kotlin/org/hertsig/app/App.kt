package org.hertsig.app

import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Unscoped
import javax.inject.Provider

@Injectable
class App(private val service: Service, private val task: Provider<Task>) {
    init {
        println("App created with service ${service.hashCode()} from")
//        Throwable().printStackTrace()
    }

    fun run() {
        println("${task.get().hashCode()}")
        println("${task.get().hashCode()}")
    }
}

fun main() = Dirk.create().getApp().run()
