package org.hertsig.app

import org.hertsig.dirk.Injectable
import org.hertsig.dirk.scope.Singleton
import javax.inject.Inject

@Injectable(Singleton::class)
class Service(name: String) {
    @Inject constructor() : this("Default name")
    init {
        println("Service $name created from")
//        Throwable().printStackTrace()
    }
}
