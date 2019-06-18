package com.fsck.k9.ui.e3.delete

import android.content.Intent
import android.widget.AdapterView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.ui.R
import com.fsck.k9.ui.e3.E3OpenPgpPresenterCallback
import com.fsck.k9.ui.e3.E3UploadMessageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openintents.openpgp.OpenPgpApiManager
import org.openintents.openpgp.util.OpenPgpApi
import timber.log.Timber
import java.io.InputStream

class E3DeviceDeletePresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val preferences: Preferences,
        private val openPgpApiManager: OpenPgpApiManager,
        private val view: E3DeviceDeleteActivity,
        private val viewModel: E3DeviceDeleteViewModel
) {
    private lateinit var account: Account

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
                        val knownPubKeys = requestKnownE3PublicKeys()
                        view.populateListViewWithE3Devices(knownPubKeys, getDevicesListAdapterListener(knownPubKeys))
                    }
                }
            }

            override fun onOpenPgpProviderError(error: OpenPgpApiManager.OpenPgpProviderError) {
                view.finishWithProviderConnectError(openPgpApiManager.readableOpenPgpProviderName)
            }
        })

        view.sceneBegin()
    }

    private fun requestKnownE3PublicKeys(): List<E3DeleteDeviceRequest> {
        val intent = Intent(OpenPgpApi.ACTION_GET_ENCRYPT_ON_RECEIPT_PUBLIC_KEYS)
        val keyIdsResult = openPgpApiManager.openPgpApi.executeApi(intent, null as InputStream?, null)

        val eorKeyIds = keyIdsResult.getLongArrayExtra(OpenPgpApi.EXTRA_KEY_IDS)
        val eorKeyNames = keyIdsResult.getStringArrayExtra(OpenPgpApi.EXTRA_NAMES)

        if (eorKeyIds.size != eorKeyNames.size) {
            return emptyList()
        }

        val e3KeyIdNames = mutableListOf<E3DeleteDeviceRequest>()

        var i = 0
        while (i < eorKeyIds.size) {
            // Skip this device's own key since it would also delete the private key
            if (eorKeyIds[i] != account.e3Key) {
                e3KeyIdNames.add(E3DeleteDeviceRequest(eorKeyIds[i], eorKeyNames[i]))
            }
            i++
        }

        return e3KeyIdNames
    }

    private fun getDevicesListAdapterListener(e3KeyIdNames: List<E3DeleteDeviceRequest>): AdapterView.OnItemClickListener {
        return AdapterView.OnItemClickListener { _, textView, position, id ->
            val selectedDevice: String = (textView as TextView).text.toString()

            Timber.d("User selected device $selectedDevice (position=$position, id=$id) to delete")

            view.displayConfirmDelete(selectedDevice,
                    object : E3DeviceDeleteActivity.E3ConfirmDeleteCallback {
                        override fun deleteConfirmed() {
                            deleteKey(e3KeyIdNames[position])
                            val knownPubKeys = requestKnownE3PublicKeys()
                            view.populateListViewWithE3Devices(knownPubKeys, getDevicesListAdapterListener(knownPubKeys))
                        }
                    }
            )
        }
    }

    private fun deleteKey(e3KeyIdName: E3DeleteDeviceRequest) {
        val intent = Intent(OpenPgpApi.ACTION_DELETE_ENCRYPT_ON_RECEIPT_KEY)
        intent.putExtra(OpenPgpApi.EXTRA_KEY_ID, e3KeyIdName.keyId)

        val deleteKeyResult = openPgpApiManager.openPgpApi.executeApi(intent, null as InputStream?, null)

        val resultCode = deleteKeyResult.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)

        if (resultCode == OpenPgpApi.RESULT_CODE_SUCCESS) {
            Timber.d("Successfully deleted E3 key from OpenKeychain $e3KeyIdName")
            createDeleteMessage(setOf(e3KeyIdName))
        } else {
            Timber.d("Failed to delete E3 key from OpeKeychain: $resultCode")
        }
    }

    private fun createDeleteMessage(e3DeviceDeleteRequests: Set<E3DeleteDeviceRequest>) {
        GlobalScope.launch(Dispatchers.Main) {
            view.uxDelay()
            view.setStateCreatingDeleteMessage()

            viewModel.e3DeviceDeleteCreateEvent.createE3DeleteMessageAsync(openPgpApiManager.openPgpApi, account, e3DeviceDeleteRequests)
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
                    view.setDeletedDevices(result.sentMessage.deleteRequests)
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