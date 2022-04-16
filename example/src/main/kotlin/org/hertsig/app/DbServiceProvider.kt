package org.hertsig.app

import org.hertsig.dirk.Assisted
import org.hertsig.dirk.Provider
import org.hertsig.dirk.scope.Thread

class DbService(name: String, schema: String)

// TODO allow multiple providers for one type as long as all have different signatures
//@Singleton
@Provider(Thread::class)
fun defaultDb(config: Config) = customDb(config, config.defaultSchema)

//@Provider
fun customDb(config: Config, @Assisted schema: String) = DbService(config.dbName, schema)
