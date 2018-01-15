package com.fsck.k9.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.TextView;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.FolderListFilter;
import com.fsck.k9.activity.K9ListActivity;
import com.fsck.k9.e3.E3ForceEncryptFoldersAsyncTask;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.e3.smime.SMIMEEncryptFunctionFactory;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalStore;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

/**
 * Created on 1/12/2018.
 *
 * @author koh
 */

public class AccountSetupE3ForceEncrypt extends K9ListActivity implements OnClickListener {
    private static final String EXTRA_FOLDERS = "folders";
    private Account account;
    private LocalFolder[] folders;
    private ArrayAdapter<LocalFolder> adapter;

    public static void actionE3ForceEncrypt(Context context, Account account, List<String> folders) {
        context.startActivity(intentActionE3ForceEncrypt(context, account, folders));
    }

    public static Intent intentActionE3ForceEncrypt(Context context, Account account, List<String> folders) {
        Intent i = new Intent(context, AccountSetupE3ForceEncrypt.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(AccountSetupE3ForceEncryptPicker.EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_FOLDERS, folders.toArray(new String[0]));
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_e3_force_encrypt_started);

        final TextView mainText = (TextView) findViewById(R.id.e3_force_encrypt_started_text);
        mainText.setText(getString(R.string.account_settings_e3_force_encrypt_started_text));
        final Button confirmButton = (Button) findViewById(R.id.confirm);
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

        final String accountUuid = getIntent().getStringExtra(AccountSetupE3ForceEncryptPicker.EXTRA_ACCOUNT);
        account = Preferences.getPreferences(this).getAccount(accountUuid);

        final String[] strFolders = getIntent().getStringArrayExtra(EXTRA_FOLDERS);
        folders = new LocalFolder[strFolders.length];

        try {
            final LocalStore localStore = LocalStore.getInstance(account, this);

            for (int i = 0; i < strFolders.length; i++) {
                folders[i] = localStore.getFolder(strFolders[i]);
            }

            new PopulateFolderListFromArrayTask(adapter).execute(folders);
        } catch (final MessagingException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.confirm:
                Function<MimeMessage, MimeMessage> encryptFunction = SMIMEEncryptFunctionFactory.get(this, account.getE3KeyName(),
                        account.getE3Password());
                E3ForceEncryptFoldersAsyncTask forceEncryptTask = new E3ForceEncryptFoldersAsyncTask(this, account, encryptFunction);

                forceEncryptTask.execute(folders);
                break;
        }
    }

    private static class PopulateFolderListFromArrayTask extends AsyncTask<LocalFolder, Void, List<LocalFolder>> {
        private ArrayAdapter<LocalFolder> adapter;

        PopulateFolderListFromArrayTask(ArrayAdapter<LocalFolder> adapter) {
            this.adapter = adapter;
        }

        @Override
        protected List<LocalFolder> doInBackground(LocalFolder... params) {
            Preconditions.checkNotNull(params, "Must provide LocalFolders as execute parameter");

            return Arrays.asList(params);
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onPostExecute(final List<LocalFolder> res) {
            // Now we're in the UI-thread, we can safely change the contents of the adapter.
            adapter.clear();
            adapter.addAll(res);
            adapter.notifyDataSetChanged();
        }
    }
}
