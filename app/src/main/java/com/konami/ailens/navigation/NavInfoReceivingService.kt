package com.konami.ailens.navigation

import android.app.Service
import android.content.Intent
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo

/**
 * Service that receives turn-by-turn navigation information from Navigation SDK
 * and exposes it via LiveData for observation by other components.
 */
class NavInfoReceivingService : Service() {

    private lateinit var incomingMessenger: Messenger
    private lateinit var turnByTurnManager: TurnByTurnManager

    companion object {
        private const val TAG = "NavInfoReceivingService"
        private val navInfoMutableLiveData = MutableLiveData<NavInfo?>()

        /**
         * LiveData that emits NavInfo updates from the Navigation SDK.
         * Components can observe this to receive real-time navigation step updates.
         */
        val navInfoLiveData: LiveData<NavInfo?> get() = navInfoMutableLiveData
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Create TurnByTurnManager instance
        turnByTurnManager = TurnByTurnManager.createInstance()

        // Create a background thread for handling incoming messages
        val thread = HandlerThread("NavInfoReceivingService", Process.THREAD_PRIORITY_DEFAULT)
        thread.start()

        incomingMessenger = Messenger(IncomingNavStepHandler(looper = thread.looper, manager = turnByTurnManager))
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return incomingMessenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service unbound")
        // Clear the data when unbinding
        navInfoMutableLiveData.postValue(null)
        return super.onUnbind(intent)
    }

    /**
     * Handler that processes incoming navigation messages from the Navigation SDK.
     */
    private class IncomingNavStepHandler(
        looper: Looper,
        private val manager: TurnByTurnManager
    ) : android.os.Handler(looper) {
        override fun handleMessage(msg: Message) {
            if (TurnByTurnManager.MSG_NAV_INFO == msg.what) {
                try {
                    val navInfo = manager.readNavInfoFromBundle(msg.data)
                    navInfoMutableLiveData.postValue(navInfo)
                    Log.d(TAG, "Received NavInfo update: state=${navInfo?.navState}, currentStep=${navInfo?.currentStep?.fullInstructionText}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading NavInfo from bundle", e)
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }
}
