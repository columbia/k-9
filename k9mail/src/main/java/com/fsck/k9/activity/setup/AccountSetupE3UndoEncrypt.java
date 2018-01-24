package com.fsck.k9.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.TextView;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.FolderListFilter;
import com.fsck.k9.activity.K9ListActivity;
import com.fsck.k9.e3.E3UndoEncryptFoldersAsyncTask;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.e3.smime.ComposedDecryptSMIMEToMessageFunction;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalStore;
import com.google.common.base.Function;

import java.util.List;

/**
 * Created on 1/16/2018.
 *
 * @author koh
 */

public class AccountSetupE3UndoEncrypt extends K9ListActivity implements OnClickListener {
    private static final String EXTRA_FOLDERS = "folders";
    private Account account;
    private LocalFolder[] folders;
    private ArrayAdapter<LocalFolder> adapter;

    public static void actionE3UndoEncrypt(Context context, Account account, List<String> folders) {
        context.startActivity(intentActionE3UndoEncrypt(context, account, folders));
    }

    public static Intent intentActionE3UndoEncrypt(Context context, Account account, List<String> folders) {
        Intent i = new Intent(context, AccountSetupE3UndoEncrypt.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(AccountSetupE3FolderListPicker.EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_FOLDERS, folders.toArray(new String[0]));
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_e3_force_encrypt_started);

        final TextView mainText = (TextView) findViewById(R.id.e3_force_encrypt_started_text);
        mainText.setText(getString(R.string.account_settings_e3_undo_encrypt_started_text));
        final Button confirmButton = (Button) findViewById(R.id.confirm);
        confirmButton.setText(R.string.e3_undo_encrypt_confirm);
        confirmButton.setOnClickListener(this);

        adapter = new ArrayAdapter<LocalFolder>(this, android.R.layout.simple_list_item_1) {
            private Filter myFilter = null;

            @Override
            public Filter getFilter() {
                if (myFilter == null) {
                    myFilter = new FolderListFilter<>(this);
                }
                return myFilter;
            }
        };
        getListView().setAdapter(adapter);

        final String accountUuid = getIntent().getStringExtra(AccountSetupE3FolderListPicker.EXTRA_ACCOUNT);
        account = Preferences.getPreferences(this).getAccount(accountUuid);

        final String[] strFolders = getIntent().getStringArrayExtra(EXTRA_FOLDERS);
        folders = new LocalFolder[strFolders.length];

        try {
            final LocalStore localStore = LocalStore.getInstance(account, this);

            for (int i = 0; i < strFolders.length; i++) {
                folders[i] = localStore.getFolder(strFolders[i]);
            }

            new AccountSetupE3ForceEncrypt.PopulateFolderListFromArrayTask(adapter).execute(folders);
        } catch (final MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.confirm:
                Function<Part, MimeMessage> decryptFunction =
                        new ComposedDecryptSMIMEToMessageFunction(this,
                                account.getUuid(),
                                account.getE3KeyName(),
                                account.getE3Password());
                E3UndoEncryptFoldersAsyncTask forceEncryptTask = new E3UndoEncryptFoldersAsyncTask(this, account, decryptFunction);

                forceEncryptTask.execute(folders);
                break;
        }
    }
}
