package org.hertsig.app.service

import org.hertsig.app.db.DbService
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Service(private val db: DbService, private val name: String) {
    @Inject
    constructor(db: DbService) : this(db, "Default name")

    @PostConstruct
    fun start() {
        println("Starting service $name")
    }

    @PreDestroy
    fun stop() {
        println("Stopping service $name")
    }
}