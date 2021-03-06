package com.fsck.k9.ui.e3.upload

import androidx.lifecycle.ViewModel
import com.fsck.k9.ui.e3.E3UploadMessageLiveEvent

internal class E3KeyUploadViewModel(
        val e3KeyUploadSetupMessageLiveEvent: E3KeyUploadSetupMessageLiveEvent,
        val e3UploadMessageLiveEvent: E3UploadMessageLiveEvent) : ViewModel()