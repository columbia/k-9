package com.fsck.k9.ui.e3.upload

import androidx.lifecycle.ViewModel

internal class E3KeyUploadViewModel(
        val e3KeyUploadSetupMessageLiveEvent: E3KeyUploadSetupMessageLiveEvent,
        val e3KeyUploadMessageUploadLiveEvent: E3KeyUploadMessageUploadLiveEvent) : ViewModel()