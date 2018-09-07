package com.fsck.k9.ui.e3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.fsck.k9.R
import com.fsck.k9.view.StatusIndicator
import kotlinx.android.synthetic.main.crypto_e3_undo.*
import org.koin.android.ext.android.inject

class E3UndoActivity : E3ActionBaseActivity() {
    private val presenter: E3UndoPresenter by inject {
        mapOf("lifecycleOwner" to this, "e3UndoView" to this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_undo)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        e3UndoButton.setOnClickListener {
            presenter.onClickUndo()
        }

        presenter.initFromIntent(accountUuid)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            presenter.onClickHome()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun setAddress(address: String) {
        e3UndoAddress.text = address
    }

    fun sceneBegin() {
        e3UndoButton.visibility = View.VISIBLE
        e3UndoMsgInfo.visibility = View.VISIBLE

        e3UndoLayoutUndoing.visibility = View.GONE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE
        e3UndoError.visibility = View.GONE
    }

    fun sceneUndoing() {
        setupSceneTransition()

        e3UndoButton.visibility = View.GONE
        e3UndoMsgInfo.visibility = View.GONE
        e3UndoLayoutUndoing.visibility = View.VISIBLE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE
        e3UndoError.visibility = View.GONE
    }

    fun setLoadingStateUndoing() {
        e3UndoProgressUndoing.setDisplayedChild(StatusIndicator.Status.PROGRESS)
    }

    fun setLoadingStateDownloading() {
        e3UndoProgressUndoing.setDisplayedChild(StatusIndicator.Status.OK)
    }

    fun setLoadingStateFinished() {
        e3UndoProgressUndoing.setDisplayedChild(StatusIndicator.Status.OK)
    }

    fun sceneFinished(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3UndoButton.visibility = View.GONE
        e3UndoMsgInfo.visibility = View.GONE
        e3UndoLayoutUndoing.visibility = View.VISIBLE
        e3UndoLayoutFinish.visibility = View.VISIBLE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE
        e3UndoError.visibility = View.GONE
    }

    fun sceneFinishedNoMessages(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }

        e3UndoButton.visibility = View.GONE
        e3UndoMsgInfo.visibility = View.GONE
        e3UndoLayoutUndoing.visibility = View.VISIBLE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.VISIBLE
        e3UndoError.visibility = View.GONE
    }

    fun setLoadingStateSendingFailed() {
        e3UndoProgressUndoing.setDisplayedChild(StatusIndicator.Status.OK)
    }

    fun sceneSendError() {
        setupSceneTransition()

        e3UndoButton.visibility = View.GONE
        e3UndoMsgInfo.visibility = View.GONE
        e3UndoLayoutUndoing.visibility = View.VISIBLE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE
        e3UndoError.visibility = View.VISIBLE
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"

        fun createIntent(context: Context, accountUuid: String): Intent {
            val intent = Intent(context, E3UndoActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            return intent
        }
    }
}