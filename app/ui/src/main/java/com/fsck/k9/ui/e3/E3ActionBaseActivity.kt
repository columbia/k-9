package com.fsck.k9.ui.e3

import android.app.PendingIntent
import android.content.IntentSender
import android.os.Build
import android.transition.TransitionInflater
import android.transition.TransitionManager
import com.fsck.k9.activity.K9Activity
import com.fsck.k9.finishWithErrorToast
import com.fsck.k9.ui.R
import kotlinx.coroutines.delay
import timber.log.Timber

abstract class E3ActionBaseActivity : K9Activity() {

    fun finishWithInvalidAccountError() {
        finishWithErrorToast(R.string.toast_account_not_found)
    }

    fun finishWithProviderConnectError(providerName: String) {
        finishWithErrorToast(R.string.toast_openpgp_provider_error, providerName)
    }

    fun launchUserInteractionPendingIntent(pendingIntent: PendingIntent) {
        try {
            startIntentSender(pendingIntent.intentSender, null, 0, 0, 0)
        } catch (e: IntentSender.SendIntentException) {
            Timber.e(e)
        }
    }

    fun setupSceneTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val transition = TransitionInflater.from(this).inflateTransition(R.transition.transfer_transitions)
            TransitionManager.beginDelayedTransition(findViewById(android.R.id.content), transition)
        }
    }

    fun finishAsCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    suspend fun uxDelay() {
        // called before logic resumes upon screen transitions, to give some breathing room
        delay(E3ActionBaseActivity.UX_DELAY_MS)
    }

    companion object {
        private const val UX_DELAY_MS = 1200L
    }
}