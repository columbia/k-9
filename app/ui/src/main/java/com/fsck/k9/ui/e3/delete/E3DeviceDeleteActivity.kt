package com.fsck.k9.ui.e3.delete

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.core.text.HtmlCompat
import com.fsck.k9.crypto.e3.E3PublicKeyIdName
import com.fsck.k9.ui.R
import com.fsck.k9.ui.e3.E3ActionBaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.crypto_e3_device_delete.*
import kotlinx.android.synthetic.main.wizard_cancel_done.*
import org.koin.android.ext.android.inject

class E3DeviceDeleteActivity : E3ActionBaseActivity(), View.OnClickListener {
    private val presenter: E3DeviceDeletePresenter by inject {
        mapOf("lifecycleOwner" to this, "e3DeleteDeviceView" to this)
    }

    private var mBottomSheetDialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.crypto_e3_device_delete)

        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)

        cancel.setOnClickListener(this)
        done.setOnClickListener(this)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        presenter.initFromIntent(accountUuid)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            presenter.onClickHome()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun addDevicesToListView(devices: List<String>, listener: AdapterView.OnItemClickListener) {
        val phrasesAdapter = ArrayAdapter<String>(this, R.layout.crypto_e3_key_verify_phrase_row, devices)

        e3DeviceDeleteLayoutDevices.adapter = phrasesAdapter
        e3DeviceDeleteLayoutDevices.onItemClickListener = listener
    }

    fun sceneBegin() {
        e3DeviceDeleteLayoutInstructions.visibility = View.VISIBLE
        e3DeviceDeleteLayoutDevices.visibility = View.VISIBLE

        cancel.visibility = View.VISIBLE
        done.visibility = View.GONE
    }

    fun sceneFinished(transition: Boolean = false) {
        if (transition) {
            setupSceneTransition()
        }
    }

    fun populateListViewWithE3Devices(e3KeyIdNames: List<E3PublicKeyIdName>, adapterListener: AdapterView.OnItemClickListener) {
        addDevicesToListView(e3KeyIdNames.map {
            it.keyName ?: resources.getString(R.string.e3_device_delete_missing_device_name)
        }, adapterListener)

    }

    fun displayConfirmDelete(deviceName: String, callback: E3ConfirmDeleteCallback) {
        val bottomSheetLayout: View = layoutInflater.inflate(R.layout.crypto_e3_device_delete_confirm_bottom_sheet, null)

        val deleteConfirmInfoStr = resources.getString(R.string.e3_device_delete_confirm_info, deviceName)
        bottomSheetLayout.findViewById<TextView>(R.id.tv_detail).text = HtmlCompat.fromHtml(deleteConfirmInfoStr, HtmlCompat.FROM_HTML_MODE_COMPACT)

        bottomSheetLayout.findViewById<Button>(R.id.button_no).setOnClickListener {
            mBottomSheetDialog!!.dismiss()
        }
        bottomSheetLayout.findViewById<Button>(R.id.button_ok).setOnClickListener {
            mBottomSheetDialog!!.dismiss()
            callback.deleteConfirmed()
        }

        mBottomSheetDialog = BottomSheetDialog(this)
        mBottomSheetDialog!!.setContentView(bottomSheetLayout)

        mBottomSheetDialog!!.show()
    }

    fun setStateCreatingDeleteMessage() {

    }

    fun setStateUploadFailed() {

    }

    fun setStateUploadFinished() {

    }

    fun sceneUploadError() {

    }

    fun setDeletedDevices(deletedDevices: Set<E3PublicKeyIdName>) {

    }

    override fun onClick(v: View) {
        if (v.id == R.id.cancel) {
            setResult(Activity.RESULT_CANCELED)

            finish()
        } else if (v.id == R.id.done) {
            finish()
        }
    }

    private fun onCancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"

        fun createIntent(context: Context, accountUuid: String): Intent {
            val intent = Intent(context, E3DeviceDeleteActivity::class.java)
            intent.putExtra(EXTRA_ACCOUNT, accountUuid)
            return intent
        }
    }

    interface E3ConfirmDeleteCallback {
        fun deleteConfirmed()
    }
}