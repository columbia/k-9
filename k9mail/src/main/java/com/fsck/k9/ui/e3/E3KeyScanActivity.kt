package com.fsck.k9.ui.e3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.fsck.k9.R
import kotlinx.android.synthetic.main.crypto_e3_key_scan.*
import org.koin.android.ext.android.inject

class E3KeyScanActivity : E3ActionBaseActivity() {
    private val presenter: E3KeyScanPresenter by inject {
        mapOf("lifecycleOwner" to this, "e3ScanView" to this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_key_scan)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        e3KeyScanButton.setOnClickListener { presenter.onClickUpload() }

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
        e3KeyScanAddress.text = address
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"

        fun createIntent(context: Context, accountUuid: String): Intent {
            val intent = Intent(context, E3KeyScanActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            return intent
        }
    }
}