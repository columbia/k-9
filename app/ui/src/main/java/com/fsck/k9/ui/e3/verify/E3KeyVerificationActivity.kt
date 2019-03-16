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
import com.fsck.k9.activity.Accounts
import com.fsck.k9.ui.e3.E3ActionBaseActivity
import com.fsck.k9.ui.R
import kotlinx.android.synthetic.main.crypto_e3_key_verify.*
import kotlinx.android.synthetic.main.text_list_item.view.*
import kotlinx.android.synthetic.main.wizard_cancel_done.*
import org.koin.android.ext.android.inject
import java.util.*

class E3KeyVerificationActivity : E3ActionBaseActivity(), View.OnClickListener {
    private val uidsToPhrases: Queue<E3KeyUidToPhrase> = ArrayDeque()
    private val presenter: E3KeyVerificationPresenter by inject {
        mapOf("lifecycleOwner" to this, "e3VerifyView" to this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_key_verify)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)

        val serializedMap = intent.getSerializableExtra(EXTRA_UIDS_TO_PHRASES)

        if (serializedMap != null) {
            @Suppress("UNCHECKED_CAST")
            val uidsToPhrasesMap = intent.getSerializableExtra(EXTRA_UIDS_TO_PHRASES) as Map<String, String>

            for ((uid, phrase) in uidsToPhrasesMap) {
                uidsToPhrases.add(E3KeyUidToPhrase(uid, phrase))
            }
        } else {
            finishAsCancelled()
            return
        }

        e3KeyVerifyNextKey.setOnClickListener {
            sceneBegin()
            requestNextVerification()
        }

        findViewById<View>(R.id.cancel).setOnClickListener(this)
        findViewById<View>(R.id.done).setOnClickListener(this)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        presenter.initFromIntent(accountUuid)
    }

    override fun onStart() {
        super.onStart()

        requestNextVerification()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            presenter.onClickHome()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun requestNextVerification() {
        val uidToPhrase = uidsToPhrases.poll()

        if (uidToPhrase != null) {
            presenter.requestUserVerification(uidToPhrase, uidsToPhrases.isNotEmpty())
        }
    }

    fun sceneBegin() {
        e3KeyVerifyLayoutInstructions.visibility = View.VISIBLE
        e3KeyVerifyLayoutVerificationPhrases.visibility = View.VISIBLE
        e3KeyVerifyCorrectPhrase.visibility = View.GONE
        e3KeyUploadResponseLayoutVerification.visibility = View.GONE
        e3KeyUploadResponseLayoutVerificationPhrase.visibility = View.GONE
        e3KeyVerifyErrorWrongPhrase.visibility = View.GONE
        e3KeyVerifyNextKey.visibility = View.GONE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun sceneFinished(newVerificationPhrase: String?, nextKey: Boolean, transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3KeyVerifyLayoutInstructions.visibility = View.GONE
        e3KeyVerifyLayoutVerificationPhrases.visibility = View.GONE
        e3KeyVerifyCorrectPhrase.visibility = View.VISIBLE
        e3KeyVerifyErrorWrongPhrase.visibility

        if (newVerificationPhrase != null && newVerificationPhrase.isNotEmpty()) {
            e3KeyUploadResponseLayoutVerification.visibility = View.VISIBLE
            e3KeyUploadResponseLayoutVerificationPhrase.visibility = View.VISIBLE
            e3KeyVerificationPhrase.text = newVerificationPhrase
        }

        if (nextKey) {
            e3KeyVerifyNextKey.visibility = View.VISIBLE
        }

        cancel.visibility = View.GONE
        done.visibility = View.VISIBLE
    }

    fun sceneErrorWrongPhrase(nextKey: Boolean) {
        setupSceneTransition()


        e3KeyVerifyLayoutInstructions.visibility = View.GONE
        e3KeyVerifyLayoutVerificationPhrases.visibility = View.GONE
        e3KeyVerifyCorrectPhrase.visibility = View.GONE
        e3KeyUploadResponseLayoutVerification.visibility = View.GONE
        e3KeyUploadResponseLayoutVerificationPhrase.visibility = View.GONE
        e3KeyVerifyErrorWrongPhrase.visibility = View.VISIBLE

        if (nextKey) {
            e3KeyVerifyNextKey.visibility = View.VISIBLE
        }

        cancel.visibility = View.GONE
        done.visibility = View.VISIBLE
    }

    fun addPhrasesToListView(phrases: List<String>, listener: AdapterView.OnItemClickListener) {
        val phrasesAdapter = ArrayAdapter<String>(this, R.layout.crypto_e3_key_verify_phrase_row, phrases)
        val listView = findViewById<ListView>(R.id.e3KeyVerifyLayoutVerificationPhrases)

        listView.adapter = phrasesAdapter
        listView.onItemClickListener = listener
    }

    override fun onClick(v: View) {
        if (v.id == R.id.cancel) {
            setResult(Activity.RESULT_CANCELED)

            finish()
        } else if (v.id == R.id.done) {
            finish()
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_UIDS_TO_PHRASES = "uids_to_phrases"
        private const val EXTRA_FOLDER = "folder"

        @JvmStatic
        fun createIntent(context: Context, accountUuid: String, uidsToPhrases: HashMap<String, String>): Intent {
            val intent = Intent(context, E3KeyVerificationActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            intent.putExtra(EXTRA_UIDS_TO_PHRASES, uidsToPhrases)
            // intent.putExtra(EXTRA_FOLDER, folder)
            return intent
        }
    }

    class E3KeyUidToPhrase internal constructor(val uid: String, val phrase: String)
}
