package org.hertsig.app

import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Singleton

@Injectable(Singleton::class)
class Service {
    init {
        println("Service created from")
//        Throwable().printStackTrace()
    }
}
