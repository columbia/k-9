package com.fsck.k9.ui.e3.verify

import androidx.lifecycle.LifecycleOwner
import android.content.Intent
import android.widget.AdapterView
import android.widget.TextView
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.crypto.e3.E3Constants
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mail.internet.MimeUtility
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.ui.crypto.PgpWordList
import com.fsck.k9.ui.e3.E3OpenPgpPresenterCallback
import com.fsck.k9.ui.e3.upload.E3KeyUploadMessage
import com.fsck.k9.ui.e3.upload.E3KeyUploadMessageCreator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.anko.coroutines.experimental.bg
import org.openintents.openpgp.OpenPgpApiManager
import org.openintents.openpgp.util.OpenPgpApi
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream

class E3KeyVerificationPresenter internal constructor(
        lifecycleOwner: LifecycleOwner,
        private val preferences: Preferences,
        private val openPgpApiManager: OpenPgpApiManager,
        private val view: E3KeyVerificationActivity,
        private val wordGenerator: PgpWordList,
        private val e3KeyUploadMessageCreator: E3KeyUploadMessageCreator,
        private val messagingController: MessagingController
) {
    private lateinit var account: Account

    fun initFromIntent(accountUuid: String?) {
        if (accountUuid == null) {
            view.finishWithInvalidAccountError()
            return
        }

        account = preferences.getAccount(accountUuid)
        openPgpApiManager.setOpenPgpProvider(account.e3Provider, E3OpenPgpPresenterCallback(openPgpApiManager, view))

        view.sceneBegin()
    }

    fun requestUserVerification(uidToPhrase: E3KeyVerificationActivity.E3KeyUidToPhrase, moreKeys: Boolean = false) {
        val uid = uidToPhrase.uid
        val phrase = uidToPhrase.phrase
        val phrases = generateRandomPhrasesList(phrase)

        Timber.d("Created list of verification phrases: ${phrases.joinToString(", ")} (correct one is: $phrase)")

        val listener = AdapterView.OnItemClickListener { _, textView, _, _ ->
            val selectedPhrase: String = (textView as TextView).text.toString()

            if (selectedPhrase == phrase) {
                Timber.d("E3 key verified, adding to keychain")
                val e3Digests = addVerifiedKeysFromMessages(listOf(uid))
                Timber.d("E3 key added to keychain, now attempting to upload all own keys")
                val newVerificationPhrase = uploadKeys(e3Digests)?.verificationPhrase

                view.sceneFinished(newVerificationPhrase, moreKeys)
            } else {
                Timber.d("User chose wrong verification phrase for E3 key in msgUid=$uid, selectedPhrase=$selectedPhrase, correctPhrase=$phrase")
                view.sceneErrorWrongPhrase(moreKeys)
            }

            if (!moreKeys) {
                openPgpApiManager.setOpenPgpProvider(null, null)
            }
        }

        view.addPhrasesToListView(phrases, listener)
    }

    // Invoked in response to verifying a newly detected key
    private fun uploadKeys(receivedE3KeyDigests: E3DigestsAndResponses): E3KeyUploadMessage? {
        val keyUploadMessage = e3KeyUploadMessageCreator.loadAllE3KeysUploadMessage(
                openPgpApiManager.openPgpApi, account, receivedE3KeyDigests)

        if (keyUploadMessage == null) {
            Timber.d("E3 key upload skipped probably because already uploaded key")
            return null
        }

        GlobalScope.launch(Dispatchers.Main) {
            val setupMessage = bg {
                messagingController.sendMessageBlocking(account, keyUploadMessage.keyUploadMessage)
            }

            delay(2000)

            try {
                setupMessage.await()
                Timber.d("E3 Successfully uploaded keys")
            } catch (e: Exception) {
                Timber.e(e, "E3 failed to upload keys")
            }
        }

        return keyUploadMessage
    }

    private fun generateRandomPhrasesList(correctVerificationPhrase: String): List<String> {
        val phrases: MutableList<String> = mutableListOf(correctVerificationPhrase)

        while (phrases.size < E3Constants.E3_VERIFICATION_PHRASES) {
            val randomPhrase = wordGenerator.getRandomWords(E3Constants.E3_VERIFICATION_PHRASE_LENGTH)
            val randomPhraseStr = randomPhrase.joinToString(E3Constants.E3_VERIFICATION_PHRASE_DELIMITER)

            if (randomPhraseStr != correctVerificationPhrase) {
                phrases.add(randomPhraseStr)
            }
        }

        phrases.shuffle()
        return phrases
    }

    // Returns a list of the E3 Key Digests of all the verified keys and their RESPONSE TO digests
    private fun addVerifiedKeysFromMessages(msgUids: List<String>): E3DigestsAndResponses {
        val e3KeyDigests = mutableSetOf<String>()
        val allResponseToE3KeyDigests = mutableSetOf<String>()
        val localMessages = mutableListOf<LocalMessage>()
        val localFolder = account.localStore.getFolder(account.inboxFolder)
        val fetchProfile = FetchProfile()

        fetchProfile.add(FetchProfile.Item.ENVELOPE)
        fetchProfile.add(FetchProfile.Item.BODY)

        for (uid in msgUids) {
            val localMessage = localFolder.getMessage(uid)
            localMessages.add(localMessage)

            val e3KeyDigest = localMessage.getHeader(E3Constants.MIME_E3_DIGEST)[0]
            e3KeyDigests.add(e3KeyDigest)

            if (localMessage.headerNames.contains(E3Constants.MIME_E3_RESPONSE_TO)) {
                val decodedResponseTo = MimeUtility.unfoldAndDecode(localMessage.getHeader(E3Constants.MIME_E3_RESPONSE_TO)[0])
                val responseToE3KeyDigests = decodedResponseTo.split(E3Constants.E3_KEY_DIGEST_DELIMITER)
                allResponseToE3KeyDigests.addAll(responseToE3KeyDigests)
            }
        }

        localFolder.fetch(localMessages, fetchProfile, null)
        addKeysFromMessagesToKeychain(localMessages)

        return E3DigestsAndResponses(e3KeyDigests, allResponseToE3KeyDigests)
    }

    // TODO: E3 use E3PublicKeyManager
    private fun addKeysFromMessagesToKeychain(keyMessages: List<LocalMessage>) {
        for (keyMsg: LocalMessage in keyMessages) {
            if (!keyMsg.hasAttachments()) {
                Timber.e("Got a keyMsg without any attachments")
                continue
            }

            val keyPart = MimeUtility.findFirstPartByMimeType(keyMsg, E3Constants.CONTENT_TYPE_PGP_KEYS)

            if (keyPart == null) {
                Timber.e("Did not find any ${E3Constants.CONTENT_TYPE_PGP_KEYS} attachment in E3 key message: ${keyMsg.messageId} ${keyMsg.preview}")
                continue
            }

            val keyBytes = ByteArrayOutputStream()
            keyPart.body.writeTo(keyBytes)

            val pgpApiIntent = Intent(OpenPgpApi.ACTION_ADD_ENCRYPT_ON_RECEIPT_KEY)
            pgpApiIntent.putExtra(OpenPgpApi.EXTRA_ASCII_ARMORED_KEY, keyBytes.toByteArray())

            val result = openPgpApiManager.openPgpApi.executeApi(pgpApiIntent, null as InputStream?, null)
            val resultCode = result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)

            if (resultCode == OpenPgpApi.RESULT_CODE_SUCCESS) {
                Timber.d("Successfully added E3 public key to OpenKeychain")
            } else {
                Timber.d("Failed to add E3 public key to OpeKeychain: $resultCode")
            }
        }
    }

    fun onClickHome() {
        view.finishAsCancelled()
    }
}

data class E3DigestsAndResponses(val verifiedE3KeyDigests: Set<String>, val responseToKeyDigests: Set<String>)