package net.bdfz.recite.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bdfz.recite.LangLangApplication
import net.bdfz.recite.network.ApiException

class ProgressSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as LangLangApplication
        val session = app.container.sessionStore.read() ?: return@withContext Result.success()
        val dao = app.container.database.progressDao()
        val pending = dao.pending()
        if (pending.isEmpty()) return@withContext Result.success()

        for (item in pending) {
            try {
                app.container.apiClient.pushProgress(session, item.payloadJson)
                dao.deleteOutbox(item.id)
            } catch (error: ApiException) {
                dao.recordAttempt(item.id)
                if (error.statusCode == 401 || error.statusCode == 403) {
                    return@withContext Result.failure()
                }
                return@withContext if (runAttemptCount < 4) Result.retry() else Result.failure()
            }
        }
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK = "recite-progress-sync"
    }
}
