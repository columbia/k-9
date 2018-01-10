package com.fsck.k9.e3;


import android.os.AsyncTask;

import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.internet.MimeMessage;
import com.google.common.base.Function;

/**
 * Created on 1/9/2018.
 *
 * @author koh
 */

public class E3ForceEncryptFoldersAsyncTask extends AsyncTask<Folder, Void, Void> {
    final Function<MimeMessage, MimeMessage> encryptFunction;

    public E3ForceEncryptFoldersAsyncTask(Function<MimeMessage, MimeMessage> encryptFunction) {
        this.encryptFunction = encryptFunction;
    }

    @Override
    protected Void doInBackground(Folder... voids) {

        return null;
    }
}
