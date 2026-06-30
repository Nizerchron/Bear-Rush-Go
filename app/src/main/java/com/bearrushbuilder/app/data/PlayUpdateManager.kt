package com.bearrushbuilder.app.data

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * PlayUpdateManager handles native Google Play In-App Updates.
 * Standard implementation using failure listeners and robust safety checks.
 */
object PlayUpdateManager {
    const val REQUEST_CODE_UPDATE = 1002
    private const val TAG = "PlayUpdateManager"

    /**
     * Checks for updates on Google Play and triggers immediate update flow if found.
     */
    fun checkAndStartUpdate(activity: Activity) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.IMMEDIATE,
                            activity,
                            REQUEST_CODE_UPDATE
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to start update flow", t)
                    }
                }
            }

            appUpdateInfoTask.addOnFailureListener { exception ->
                Log.e(TAG, "App update info task failed: ${exception.message}", exception)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize AppUpdateManager or request update info", t)
        }
    }

    /**
     * Resumes an update flow that was started and is still in progress.
     * Call this from Activity's onResume.
     */
    fun resumeInProgressUpdate(activity: Activity) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(activity)
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.IMMEDIATE,
                            activity,
                            REQUEST_CODE_UPDATE
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to resume update flow", t)
                    }
                }
            }

            appUpdateInfoTask.addOnFailureListener { exception ->
                Log.e(TAG, "App update info resume task failed: ${exception.message}", exception)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resume AppUpdateManager update check", t)
        }
    }
}
