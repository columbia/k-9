package com.fsck.k9.ui.e3.delete

import android.widget.AdapterView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.crypto.e3.E3PublicKeyIdName
import com.fsck.k9.crypto.e3.E3PublicKeyManager
import com.fsck.k9.ui.e3.E3UploadMessageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openintents.openpgp.OpenPgpApiManager
import timber.log.Timber

class E3DeviceDeletePresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val preferences: Preferences,
        private val openPgpApiManager: OpenPgpApiManager,
        private val view: E3DeviceDeleteActivity,
        private val viewModel: E3DeviceDeleteViewModel
) {
    private lateinit var account: Account
    private lateinit var e3PublicKeyManager: E3PublicKeyManager

    init {
        viewModel.e3DeviceDeleteCreateEvent.observe(lifecycleOwner, Observer { msg -> msg?.let { onEventE3CreateDeleteMessage(it) } })
        viewModel.e3UploadMessageLiveEvent.observe(lifecycleOwner, Observer { pi -> onUploadedE3Message(pi) })
    }

    fun initFromIntent(accountUuid: String?) {
        if (accountUuid == null) {
            view.finishWithInvalidAccountError()
            return
        }

        account = preferences.getAccount(accountUuid)
        openPgpApiManager.setOpenPgpProvider(account.e3Provider, object : OpenPgpApiManager.OpenPgpApiManagerCallback {
            override fun onOpenPgpProviderStatusChanged() {
                Timber.d("Got openPgpProviderState=${openPgpApiManager.openPgpProviderState}")
                when (openPgpApiManager.openPgpProviderState) {
                    OpenPgpApiManager.OpenPgpProviderState.UI_REQUIRED -> {
                        view.finishWithProviderConnectError(openPgpApiManager.readableOpenPgpProviderName)
                    }
                    OpenPgpApiManager.OpenPgpProviderState.OK -> {
                        e3PublicKeyManager = E3PublicKeyManager(openPgpApiManager.openPgpApi)
                        populateE3DeviceList()
                    }
                }
            }

            override fun onOpenPgpProviderError(error: OpenPgpApiManager.OpenPgpProviderError) {
                view.finishWithProviderConnectError(openPgpApiManager.readableOpenPgpProviderName)
            }
        })

        view.sceneBegin()
    }

    private fun getDevicesListAdapterListener(e3KeyIdNames: List<E3PublicKeyIdName>): AdapterView.OnItemClickListener {
        return AdapterView.OnItemClickListener { _, textView, position, id ->
            val selectedDevice: String = (textView as TextView).text.toString()

            Timber.d("User selected device $selectedDevice (position=$position, id=$id) to delete")

            view.displayConfirmDelete(selectedDevice,
                    object : E3DeviceDeleteActivity.E3ConfirmDeleteCallback {
                        override fun deleteConfirmed() {
                            e3PublicKeyManager.deletePublicKeyFromKeychain(e3KeyIdNames[position])
                            createDeleteMessage(setOf(e3KeyIdNames[position]))
                            populateE3DeviceList()
                        }
                    }
            )
        }
    }

    private fun populateE3DeviceList() {
        val knownPubKeys = e3PublicKeyManager.requestKnownE3PublicKeys().filter { it.keyId != account.e3Key }
        view.populateListViewWithE3Devices(knownPubKeys, getDevicesListAdapterListener(knownPubKeys))
    }

    private fun createDeleteMessage(e3DeviceIdNames: Set<E3PublicKeyIdName>) {
        GlobalScope.launch(Dispatchers.Main) {
            view.uxDelay()
            view.setStateCreatingDeleteMessage()

            viewModel.e3DeviceDeleteCreateEvent.createE3DeleteMessageAsync(openPgpApiManager.openPgpApi, account, e3DeviceIdNames)
        }
    }

    private fun onEventE3CreateDeleteMessage(deleteMsg: E3DeleteMessage) {
        //view.setLoadingStateSending()
        //view.sceneGeneratingAndUploading()

        viewModel.e3UploadMessageLiveEvent.sendMessageAsync(account, deleteMsg)
    }

    private fun onUploadedE3Message(result: E3UploadMessageResult?) {
        when (result) {
            null -> view.sceneBegin()
            is E3UploadMessageResult.Success -> {
                //pendingIntentForGetKey = result.pendingIntent
                view.setStateUploadFinished()
                view.sceneFinished()

                if (result.sentMessage is E3DeleteMessage) {
                    view.setDeletedDevices(result.sentMessage.idNames)
                }
            }
            is E3UploadMessageResult.Failure -> {
                Timber.e(result.exception, "Error uploading E3 key")
                view.setStateUploadFailed()
                view.sceneUploadError()
            }
        }
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }
}