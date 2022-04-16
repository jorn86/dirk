package org.hertsig.app

import org.hertsig.dirk.Injectable

@Injectable
class Task(private val service: Service) {
    init {
        println("Task created with service ${service.hashCode()} from")
//        Throwable().printStackTrace()
    }
}
