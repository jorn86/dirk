package org.hertsig.app.task

import org.hertsig.app.Config
import org.hertsig.app.db.DbService
import org.hertsig.app.service.Service
import javax.inject.Inject

class FrameworkClass {
    @set:Inject lateinit var db: DbService
    @Inject lateinit var config: Config

    @Inject
    fun dependencies(service: Service) {
        println("Got $service ${service.hashCode()}")
        println("$db $config")
    }
}
