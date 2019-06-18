package com.fsck.k9.ui.e3

import com.fsck.k9.ui.crypto.PgpWordList
import com.fsck.k9.ui.e3.delete.E3DeleteMessageCreator
import com.fsck.k9.ui.e3.delete.E3DeviceDeleteCreateLiveEvent
import com.fsck.k9.ui.e3.delete.E3DeviceDeletePresenter
import com.fsck.k9.ui.e3.delete.E3DeviceDeleteViewModel
import com.fsck.k9.ui.e3.scan.E3KeyScanDownloadLiveEvent
import com.fsck.k9.ui.e3.scan.E3KeyScanPresenter
import com.fsck.k9.ui.e3.scan.E3KeyScanScanLiveEvent
import com.fsck.k9.ui.e3.scan.E3KeyScanViewModel
import com.fsck.k9.ui.e3.undo.E3UndoLiveEvent
import com.fsck.k9.ui.e3.undo.E3UndoPresenter
import com.fsck.k9.ui.e3.undo.E3UndoViewModel
import com.fsck.k9.ui.e3.upload.*
import com.fsck.k9.ui.e3.verify.E3KeyVerificationPresenter
import org.koin.android.architecture.ext.viewModel
import org.koin.dsl.module.applicationContext

val e3KeyUploadUiModule = applicationContext {
    factory { E3KeyUploadSetupMessageLiveEvent(get()) }
    factory { E3UploadMessageLiveEvent(get()) }
    factory { params ->
        E3KeyUploadPresenter(
                lifecycleOwner = params["lifecycleOwner"],
                openPgpApiManager = get(parameters = { params.values }),
                preferences = get(),
                viewModel = get(),
                view = params["e3UploadView"])
    }
    viewModel { E3KeyUploadViewModel(get(), get()) }
    bean { E3KeyUploadMessageCreator(get(), get()) }
}

val e3KeyScanUiModule = applicationContext {
    factory { E3KeyScanScanLiveEvent(get()) }
    factory { E3KeyScanDownloadLiveEvent() }
    factory { params ->
        E3KeyScanPresenter(
                params["lifecycleOwner"],
                get(),
                get(),
                params["e3ScanView"])
    }
    viewModel { E3KeyScanViewModel(get(), get()) }
}

val e3KeyVerifyUiModule = applicationContext {
    factory { params ->
        E3KeyVerificationPresenter(
                lifecycleOwner = params["lifecycleOwner"],
                preferences = get(),
                openPgpApiManager = get(parameters = { params.values }),
                view = params["e3VerifyView"],
                wordGenerator = PgpWordList(get()),
                e3KeyUploadMessageCreator = E3KeyUploadMessageCreator(get(), get()),
                messagingController = get()
        )
    }
}

val e3UndoUiModule = applicationContext {
    factory { E3UndoLiveEvent(get()) }
    factory { params ->
        E3UndoPresenter(
                params["lifecycleOwner"],
                get(),
                get(),
                params["e3UndoView"])
    }
    viewModel { E3UndoViewModel(get()) }
}

val e3DeviceDeleteUiModule = applicationContext {
    factory { E3DeviceDeleteCreateLiveEvent(get()) }
    factory { E3UploadMessageLiveEvent(get()) }
    factory { params ->
        E3DeviceDeletePresenter(
                lifecycleOwner = params["lifecycleOwner"],
                preferences = get(),
                openPgpApiManager = get(parameters = { params.values }),
                view = params["e3DeleteDeviceView"],
                viewModel = get()
        )
    }
    viewModel { E3DeviceDeleteViewModel(get(), get()) }
    bean { E3DeleteMessageCreator(get()) }
}