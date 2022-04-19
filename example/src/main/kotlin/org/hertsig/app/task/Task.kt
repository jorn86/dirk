package org.hertsig.app.task

import org.hertsig.app.service.Service
import org.hertsig.dirk.Injectable

@Injectable
class Task(private val service: Service) {
    init {
        println("Task created with service ${service.hashCode()} from")
//        Throwable().printStackTrace()
    }
}