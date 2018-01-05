package com.fsck.k9.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Filter;
import android.widget.ListView;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.FolderListFilter;
import com.fsck.k9.activity.K9ListActivity;
import com.fsck.k9.mail.Folder;

import java.util.LinkedList;
import java.util.List;

/**
 * Created on 1/5/2018.
 *
 * @author mauzel
 */

public class AccountSetupE3ForceEncrypt extends K9ListActivity implements OnClickListener,
        OnCheckedChangeListener {
    private static final String EXTRA_ACCOUNT = "account";

    private Account account;

    private ListView layout;
    private ArrayAdapter<String> adapter;

    public static void actionE3ForceEncrypt(Context context, Account account) {
        context.startActivity(intentActionE3ForceEncrypt(context, account));
    }

    public static Intent intentActionE3ForceEncrypt(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupE3ForceEncrypt.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.folder_list);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {
            private Filter myFilter = null;

            @Override
            public Filter getFilter() {
                if (myFilter == null) {
                    myFilter = new FolderListFilter<>(this);
                }
                return myFilter;
            }
        };
        setListAdapter(adapter);

        layout = getListView();

        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        account = Preferences.getPreferences(this).getAccount(accountUuid);

        //MessagingController.getInstance(getApplication()).listFolders(account, false, mListener);

        new PopulateFolderCheckBoxesTask().execute();
    }

    @Override
    public void onClick(View view) {

    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }

    private class PopulateFolderCheckBoxesTask extends AsyncTask<Void, Void, Void> {
        List<? extends Folder > folders = new LinkedList<>();
        String[] allFolderValues;
        String[] allFolderLabels;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // TODO: Change to only return remote folders. See PopulateFolderPrefsTask.
                folders = account.getLocalStore().getPersonalNamespaces(false);
            } catch (Exception e) {
                /// this can't be checked in
            }

            /*
            allFolderValues = new String[folders.size() + 1];
            allFolderLabels = new String[folders.size() + 1];

            allFolderValues[0] = K9.FOLDER_NONE;
            allFolderLabels[0] = K9.FOLDER_NONE;

            int i = 1;
            for (Folder folder : folders) {
                allFolderLabels[i] = folder.getName();
                allFolderValues[i] = folder.getName();
                i++;
            }
            */
            return null;
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onPostExecute(Void res) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Now we're in the UI-thread, we can safely change the contents of the adapter.
                    adapter.clear();
                    for (Folder folder: folders) {
                        adapter.add(folder.getName());
                    }

                    adapter.notifyDataSetChanged();

                    /*
                     * Only enable the text filter after the list has been
                     * populated to avoid possible race conditions because our
                     * FolderListFilter isn't really thread-safe.
                     */
                    getListView().setTextFilterEnabled(true);
                }
            });

            /*
            for (String folder : allFolderLabels) {
                CheckBox cb = new CheckBox(getApplicationContext());
                cb.setText(folder);

                layout.addView(cb);
            }
            */
        }
    }
}
