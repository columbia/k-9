package com.fsck.k9.ui.e3.delete

import androidx.lifecycle.ViewModel
import com.fsck.k9.ui.e3.E3UploadMessageLiveEvent

internal class E3DeviceDeleteViewModel(
        val e3DeviceDeleteCreateEvent: E3DeviceDeleteCreateLiveEvent,
        val e3UploadMessageLiveEvent: E3UploadMessageLiveEvent) : ViewModel()