package com.fsck.k9

import com.fsck.k9.activity.activityModule
import com.fsck.k9.contacts.contactsModule
import com.fsck.k9.fragment.fragmentModule
import com.fsck.k9.ui.e3.e3KeyScanUiModule
import com.fsck.k9.ui.e3.e3KeyUploadUiModule
import com.fsck.k9.ui.e3.e3UndoUiModule
import com.fsck.k9.ui.endtoend.endToEndUiModule
import com.fsck.k9.ui.settings.settingsUiModule
import com.fsck.k9.ui.uiModule

val uiModules = listOf(
        activityModule,
        uiModule,
        settingsUiModule,
        endToEndUiModule,
        fragmentModule,
        contactsModule,
        e3KeyUploadUiModule,
        e3KeyScanUiModule,
        e3UndoUiModule
)
