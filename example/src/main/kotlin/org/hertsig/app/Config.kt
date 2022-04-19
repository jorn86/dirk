package org.hertsig.app

import javax.inject.Singleton

data class Config(val dbName: String, val defaultSchema: String)

@Singleton
fun config() = Config("database", "schema")
