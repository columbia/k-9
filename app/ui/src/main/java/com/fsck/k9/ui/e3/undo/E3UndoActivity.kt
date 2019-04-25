package com.fsck.k9.ui.e3.undo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.view.children
import com.fsck.k9.ui.R
import com.fsck.k9.ui.e3.E3ActionBaseActivity
import com.fsck.k9.view.StatusIndicator
import kotlinx.android.synthetic.main.crypto_e3_undo.*
import kotlinx.android.synthetic.main.text_list_item.view.*
import kotlinx.android.synthetic.main.wizard_cancel_done.*
import org.koin.android.ext.android.inject

class E3UndoActivity : E3ActionBaseActivity(), View.OnClickListener {
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

        findViewById<View>(R.id.cancel).setOnClickListener(this)
        findViewById<View>(R.id.done).setOnClickListener(this)

        e3CancelUndoButton.setOnClickListener {
            presenter.onClickCancelUndo()
        }
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
        e3UndoScrollView.visibility = View.VISIBLE
        e3ExistingUndoScrollView.visibility = View.GONE

        e3UndoButton.visibility = View.VISIBLE
        e3UndoMsgInfo.visibility = View.VISIBLE
        e3UndoCancelledMsgInfo.visibility = View.GONE

        e3UndoLayoutUndoing.visibility = View.GONE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE

        e3UndoError.visibility = View.GONE
        e3ExistingUndoError.visibility = View.GONE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun sceneLaunchingUndo() {
        setupSceneTransition()

        e3UndoScrollView.visibility = View.VISIBLE
        e3ExistingUndoScrollView.visibility = View.GONE

        e3UndoButton.visibility = View.GONE
        e3UndoMsgInfo.visibility = View.VISIBLE
        e3UndoCancelledMsgInfo.visibility = View.GONE

        e3UndoLayoutUndoing.visibility = View.GONE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE

        e3UndoError.visibility = View.GONE
        e3ExistingUndoError.visibility = View.GONE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun sceneUndoing() {
        setupSceneTransition()

        e3UndoScrollView.visibility = View.GONE
        e3ExistingUndoScrollView.visibility = View.VISIBLE
        e3UndoCancelledMsgInfo.visibility = View.GONE

        e3CancelUndoButton.visibility = View.VISIBLE
        e3ExistingUndoMsgInfo.visibility = View.VISIBLE

        e3ExistingUndoError.visibility = View.GONE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun setLoadingStateUndoing() {
        e3UndoProgressUndoing.setDisplayedChild(StatusIndicator.Status.PROGRESS)
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
        e3UndoCancelledMsgInfo.visibility = View.GONE

        e3UndoLayoutUndoing.visibility = View.VISIBLE
        e3UndoLayoutFinish.visibility = View.VISIBLE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE
        e3UndoError.visibility = View.GONE

        cancel.visibility = View.GONE
        done.visibility = View.VISIBLE
    }

    fun sceneFinishedSucceeded() {
        e3UndoScrollView.visibility = View.VISIBLE
        e3ExistingUndoScrollView.visibility = View.GONE

        e3UndoButton.visibility = View.VISIBLE
        e3UndoMsgInfo.visibility = View.VISIBLE
        e3UndoCancelledMsgInfo.visibility = View.GONE

        e3UndoLayoutUndoing.visibility = View.GONE
        e3UndoLayoutFinish.visibility = View.VISIBLE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE

        e3UndoError.visibility = View.GONE
        e3ExistingUndoError.visibility = View.GONE

        cancel.visibility = View.GONE
        done.visibility = View.VISIBLE
    }

    fun sceneFinishedNoMessages(transition: Boolean = false) {
        sceneFinished(transition)
        e3UndoLayoutFinishNoMessages.visibility = View.VISIBLE
    }

    fun sceneCancelledUndo() {
        e3UndoScrollView.visibility = View.VISIBLE
        e3ExistingUndoScrollView.visibility = View.GONE

        e3UndoButton.visibility = View.GONE
        e3UndoMsgInfo.visibility = View.GONE
        e3UndoCancelledMsgInfo.visibility = View.VISIBLE

        e3UndoLayoutUndoing.visibility = View.GONE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE

        e3UndoError.visibility = View.GONE
        e3ExistingUndoError.visibility = View.GONE

        cancel.visibility = View.GONE
        done.visibility = View.VISIBLE
    }

    fun sceneCancelledUndoWithFailure() {
        sceneCancelledUndo()
        (e3UndoCancelledMsgInfo.children.elementAt(0) as TextView).text = resources.getString(R.string.e3_undo_work_cancelled_due_to_error)
    }

    fun setLoadingStateSendingFailed() {
        e3UndoProgressUndoing.setDisplayedChild(StatusIndicator.Status.OK)
    }

    fun sceneSendError() {
        setupSceneTransition()

        e3UndoButton.visibility = View.GONE
        e3UndoMsgInfo.visibility = View.GONE
        e3UndoCancelledMsgInfo.visibility = View.GONE

        e3UndoLayoutUndoing.visibility = View.VISIBLE
        e3UndoLayoutFinish.visibility = View.GONE
        e3UndoLayoutFinishNoMessages.visibility = View.GONE
        e3UndoError.visibility = View.VISIBLE

        cancel.visibility = View.GONE
        done.visibility = View.VISIBLE
    }

    private fun onCancel() {
        setResult(Activity.RESULT_CANCELED)

        finish()
    }

    override fun onClick(v: View) {
        if (v.id == R.id.cancel) {
            onCancel()
        } else if (v.id == R.id.done) {
            finish()
        }
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