package com.fsck.k9.ui.e3.verify

import android.app.Activity
import android.arch.lifecycle.LifecycleOwner
import android.content.Intent
import android.widget.AdapterView
import android.widget.TextView
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.crypto.E3Constants
import com.fsck.k9.mail.internet.MimeUtility
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.ui.crypto.PgpWordList
import com.fsck.k9.ui.e3.E3OpenPgpPresenterCallback
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
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
        private val wordGenerator: PgpWordList
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

    fun requestUserVerification(uidsToPhrases: Map<String, String>) {
        val verifiedKeys = arrayListOf<String>()
        for ((uid, phrase) in uidsToPhrases) {
            val phrases = generateRandomPhrasesList(phrase)

            Timber.d("Created list of verification phrases: ${phrases.joinToString(", ")} (correct one is: $phrase)")

            val listener = AdapterView.OnItemClickListener { _, textView, _, _ ->
                val selectedPhrase: String = (textView as TextView).text.toString()

                if (selectedPhrase == phrase) {
                    Timber.d("E3 key verified, recording the msgUid: $uid")
                    addVerifiedKeysFromMessages(listOf(uid))
                    view.returnResult(verifiedKeys, true)
                } else {
                    Timber.d("User chose wrong verification phrase for E3 key in msgUid=$uid, selectedPhrase=$selectedPhrase, correctPhrase=$phrase")
                    view.returnResult(verifiedKeys, false)
                }
            }

            view.addPhrasesToListView(phrases, listener)
        }
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

    private fun addVerifiedKeysFromMessages(msgUids: List<String>) {
        val localMessages = mutableListOf<LocalMessage>()
        addKeysFromMessagesToKeychain(localMessages)
    }

    private fun addKeysFromMessagesToKeychain(keyMessages: List<LocalMessage>) {
        for (keyMsg: LocalMessage in keyMessages) {
            if (!keyMsg.hasAttachments()) {
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