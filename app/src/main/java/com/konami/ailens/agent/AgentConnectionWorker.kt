package com.konami.ailens.agent

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker to periodically check and maintain Agent Service connection
 * Runs in background to ensure Agent stays connected even when app is in background
 */
class AgentConnectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AgentConnectionWorker"
        const val WORK_NAME = "agent_connection_check"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Checking agent connection status")

            val agentService = AgentService.instance

            // Check if agent should be connected but isn't
            if (agentService.lastToken != null &&
                agentService.lastEnv != null &&
                !agentService.isConnected.value) {

                Log.e(TAG, "Agent disconnected, attempting reconnect")
                agentService.attemptReconnect()

                Result.success()
            } else if (agentService.isConnected.value && !agentService.isReady.value) {
                // Connected but not ready - might be stuck
                Log.e(TAG, "Agent connected but not ready, checking...")

                // Wait a bit and check again
                kotlinx.coroutines.delay(5000)

                if (agentService.isConnected.value && !agentService.isReady.value) {
                    Log.e(TAG, "Agent still not ready, reconnecting")
                    agentService.attemptReconnect()
                }

                Result.success()
            } else {
                Log.e(TAG, "Agent connection healthy")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
