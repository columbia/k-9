package com.fsck.k9.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Filter;
import android.widget.ListView;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.FolderListFilter;
import com.fsck.k9.activity.K9ListActivity;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mailstore.LocalFolder;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

/**
 * Provides the view and options for allowing a user to force encrypt their mailbox by IMAP folder.
 * <p>
 * Created on 1/5/2018.
 *
 * @author koh
 */
public class AccountSetupE3ForceEncryptPicker extends K9ListActivity implements OnClickListener,
        OnCheckedChangeListener {
    static final String EXTRA_ACCOUNT = "account";

    private Account account;
    private ArrayAdapter<LocalFolder> adapter;
    private Button nextButton;

    public static void actionE3ForceEncryptPicker(Context context, Account account) {
        context.startActivity(intentActionE3ForceEncryptPicker(context, account));
    }

    public static Intent intentActionE3ForceEncryptPicker(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupE3ForceEncryptPicker.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_e3_force_encrypt);
        nextButton = (Button) findViewById(R.id.next);
        nextButton.setOnClickListener(this);

        adapter = new ArrayAdapter<LocalFolder>(this, android.R.layout.simple_list_item_multiple_choice) {
            private Filter myFilter = null;

            @Override
            public Filter getFilter() {
                if (myFilter == null) {
                    myFilter = new FolderListFilter<>(this);
                }
                return myFilter;
            }
        };
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setAdapter(adapter);

        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        account = Preferences.getPreferences(this).getAccount(accountUuid);

        new PopulateFolderListTask(adapter).execute(account);

        // TODO: Might cause a race, so is there a better way?
        getListView().setTextFilterEnabled(true);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.next:
                SparseBooleanArray checked = getListView().getCheckedItemPositions();
                List<String> selectedItems = new ArrayList<>();
                for (int i = 0; i < checked.size(); i++) {
                    int position = checked.keyAt(i);
                    final LocalFolder folder = adapter.getItem(position);

                    if (checked.valueAt(i) && folder != null) {
                        selectedItems.add(folder.getName());
                    }
                }

                AccountSetupE3ForceEncrypt.actionE3ForceEncrypt(this, account, selectedItems);
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }

    private static class PopulateFolderListTask extends AsyncTask<Account, Void, List<LocalFolder>> {
        private ArrayAdapter<LocalFolder> adapter;

        PopulateFolderListTask(ArrayAdapter<LocalFolder> adapter) {
            this.adapter = adapter;
        }

        @Override
        protected List<LocalFolder> doInBackground(Account... params) {
            Preconditions.checkNotNull(params, "Must provide Account as execute parameter");

            List<LocalFolder> folders = new LinkedList<>();

            try {
                // TODO: Change to only return remote folders. See PopulateFolderPrefsTask.
                folders = params[0].getLocalStore().getPersonalNamespaces(false);
            } catch (MessagingException e) {
                Timber.e(e, "Failed to get list of folders");
            }

            return folders;
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
