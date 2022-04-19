package org.hertsig.app

import org.hertsig.dirk.Assisted
import org.hertsig.dirk.Provides
import org.hertsig.dirk.scope.Thread
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

class DbService(private val name: String, private val schema: String) {
    @PostConstruct fun connect() {
        println("Connecting to $name:$schema")
    }
}


// TODO allow multiple providers for one type as long as all have different signatures
//@Singleton
@Provides(Thread::class)
fun defaultDb(config: Config) = customDb(config, config.defaultSchema)

//@Provider
fun customDb(config: Config, @Assisted schema: String) = DbService(config.dbName, schema)
