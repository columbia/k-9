package com.fsck.k9.ui.e3

import android.arch.lifecycle.ViewModel

internal class E3KeyScanViewModel(
        val e3KeyScanScanLiveEvent: E3KeyScanScanLiveEvent,
        val e3KeyScanDownloadLiveEvent: E3KeyScanDownloadLiveEvent) : ViewModel()