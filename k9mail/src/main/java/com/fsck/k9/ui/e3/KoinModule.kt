package com.fsck.k9.ui.e3

import org.koin.android.architecture.ext.viewModel
import org.koin.dsl.module.applicationContext

val e3KeyUploadUiModule = applicationContext {
    factory { E3KeyUploadSetupMessageLiveEvent(get()) }
    factory { E3KeyUploadMessageUploadLiveEvent() }
    factory { params ->
        E3KeyUploadPresenter(
                params["lifecycleOwner"],
                get(),
                get(parameters = { params.values }),
                get(),
                get(),
                get(),
                params["e3UploadView"])
    }
    viewModel { E3KeyUploadViewModel(get(), get()) }
}

val e3KeyScanUiModule = applicationContext {
    factory { E3KeyScanScanLiveEvent(get()) }
    factory { E3KeyScanDownloadLiveEvent() }
    factory { params ->
        E3KeyScanPresenter(
                params["lifecycleOwner"],
                get(),
                get(parameters = { params.values }),
                get(),
                get(),
                params["e3ScanView"])
    }
    viewModel { E3KeyScanViewModel(get(), get()) }
}

val e3UndoUiModule = applicationContext {
    factory { E3UndoLiveEvent(get()) }
    factory { params ->
        E3UndoPresenter(
                params["lifecycleOwner"],
                get(parameters = { params.values }),
                get(),
                get(),
                params["e3UndoView"])
    }
    viewModel { E3UndoViewModel(get()) }
}