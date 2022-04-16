package org.hertsig.app

import org.hertsig.dirk.Assisted
import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Unscoped

@Injectable
class AssistedTask(private val service: Service, @Assisted val name: String) {
    init {
        println("Assisted task $name created with service ${service.hashCode()} from")
//        Throwable().printStackTrace()
    }
}
