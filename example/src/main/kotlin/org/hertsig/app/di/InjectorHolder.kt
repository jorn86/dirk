package org.hertsig.app.di

import org.hertsig.app.Dirk
import javax.inject.Inject

object InjectorHolder {
    @Inject
    lateinit var dirk: Dirk
}
