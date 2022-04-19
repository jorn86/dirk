package org.hertsig.app

import org.hertsig.dirk.Assisted
import org.hertsig.dirk.Injectable
import javax.annotation.PostConstruct

@Injectable
class AssistedTask(private val service: Service, @Assisted val name: String) {
    @PostConstruct
    fun prepare() {
        println("Assisted task $name created with service ${service.hashCode()} from")
    }
}
