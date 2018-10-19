package com.fsck.k9.ui.e3.verify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import com.fsck.k9.ui.e3.E3ActionBaseActivity
import com.fsck.k9.ui.R
import kotlinx.android.synthetic.main.crypto_e3_key_verify.*
import org.koin.android.ext.android.inject

class E3KeyVerificationActivity : E3ActionBaseActivity() {
    private val presenter: E3KeyVerificationPresenter by inject {
        mapOf("lifecycleOwner" to this, "e3VerifyView" to this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_key_verify)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)
        val msgUid = intent.getStringExtra(EXTRA_MESSAGE_UID)
        val correctVerificationPhrase = intent.getStringExtra(EXTRA_VERIFICATION_PHRASE)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        presenter.initFromIntent(accountUuid)
        presenter.requestUserVerification(msgUid, correctVerificationPhrase)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            presenter.onClickHome()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun sceneBegin() {
        e3KeyVerifyLayoutVerificationPhrases.visibility = View.VISIBLE
        e3KeyVerifyCorrectPhrase.visibility = View.GONE
        e3KeyVerifyErrorWrongPhrase.visibility = View.GONE
    }

    private fun sceneFinished(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3KeyVerifyLayoutVerificationPhrases.visibility = View.GONE
        e3KeyVerifyCorrectPhrase.visibility = View.VISIBLE
        e3KeyVerifyErrorWrongPhrase.visibility
    }

    private fun sceneErrorWrongPhrase() {
        setupSceneTransition()

        e3KeyVerifyLayoutVerificationPhrases.visibility = View.GONE
        e3KeyVerifyCorrectPhrase.visibility = View.GONE
        e3KeyVerifyErrorWrongPhrase.visibility = View.VISIBLE
    }

    fun addPhrasesToListView(phrases: List<String>, listener: AdapterView.OnItemClickListener) {
        val phrasesAdapter = ArrayAdapter<String>(this, R.layout.crypto_e3_key_verify_phrase_row, phrases)
        val listView = findViewById<ListView>(R.id.e3KeyVerifyLayoutVerificationPhrases)

        listView.adapter = phrasesAdapter
        listView.onItemClickListener = listener
    }

    fun returnResult(messageUid: String, successful: Boolean) {
        val resultIntent = Intent()
        resultIntent.putExtra(VERIFY_PHRASE_RESULT_EXTRA_MESSAGE_UID, messageUid)
        if (successful) {
            resultIntent.putExtra(VERIFY_PHRASE_RESULT_EXTRA_SUCCESS, "true")
            sceneFinished()
        } else {
            sceneErrorWrongPhrase()
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val VERIFY_PHRASE_REQUEST_CODE = 1
        const val VERIFY_PHRASE_RESULT_EXTRA_MESSAGE_UID = "message_uid"
        const val VERIFY_PHRASE_RESULT_EXTRA_SUCCESS = "success"
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_MESSAGE_UID = "message_uid"
        private const val EXTRA_VERIFICATION_PHRASE = "verification_phrase"

        fun createIntent(context: Context, accountUuid: String, msgUid: String, verificationPhrase: String): Intent {
            val intent = Intent(context, E3KeyVerificationActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            intent.putExtra(EXTRA_MESSAGE_UID, msgUid)
            intent.putExtra(EXTRA_VERIFICATION_PHRASE, verificationPhrase)
            return intent
        }
    }
}
