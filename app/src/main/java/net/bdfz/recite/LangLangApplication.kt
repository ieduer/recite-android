package net.bdfz.recite

import android.app.Application
import androidx.room.Room
import net.bdfz.recite.data.CorpusRepository
import net.bdfz.recite.data.ReciteDatabase
import net.bdfz.recite.data.ReciteRepository
import net.bdfz.recite.network.ReciteApiClient
import net.bdfz.recite.security.SecureSessionStore

class LangLangApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database: ReciteDatabase = Room.databaseBuilder(
        application,
        ReciteDatabase::class.java,
        "recite.db",
    ).build()
    val corpusRepository = CorpusRepository(application)
    val reciteRepository = ReciteRepository(application, database, corpusRepository)
    val apiClient = ReciteApiClient()
    val sessionStore = SecureSessionStore(application)
}
