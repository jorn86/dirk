package org.hertsig.app

import org.hertsig.app.db.DbService
import org.hertsig.app.di.CustomScope
import org.hertsig.app.di.InjectorHolder
import org.hertsig.app.service.Service
import org.hertsig.app.task.AssistedTaskFactory
import org.hertsig.app.task.FrameworkClass
import org.hertsig.app.task.Task
import org.hertsig.dirk.Injectable
import javax.inject.Provider

@Injectable(CustomScope::class)
class App(
    private val service: Service,
    private val db: DbService,
    private val assistedTask: AssistedTaskFactory,
    private val task: Provider<Task>,
    private val task2: Task,
) {
    fun run() {
        task.get()
        assistedTask.get("One")
        assistedTask.get("Two")
        task.get()
    }
}

fun main() {
    val dirk = Dirk.create()
    dirk.injectInjectorHolder(InjectorHolder)
    // or: InjectorHolder.dirk = dirk
    dirk.getApp().run()
    dirk.injectFrameworkClass(FrameworkClass())
    println(dirk.assistedTaskFactory.get("Test"))
    dirk.destroy()
    dirk.getApp().run()
}
