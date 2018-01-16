package com.fsck.k9.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.util.SparseBooleanArray;
import android.view.View;

import com.fsck.k9.Account;
import com.fsck.k9.R;
import com.fsck.k9.mailstore.LocalFolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 1/16/2018.
 *
 * @author koh
 */

public class AccountSetupE3UndoEncryptPicker extends AccountSetupE3FolderListPicker {

    public static void actionE3UndoEncryptPicker(Context context, Account account) {
        context.startActivity(intentActionE3UndoEncryptPicker(context, account));
    }

    public static Intent intentActionE3UndoEncryptPicker(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupE3UndoEncryptPicker.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        return i;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.next:
                SparseBooleanArray checked = getListView().getCheckedItemPositions();
                List<String> selectedItems = new ArrayList<>();
                for (int i = 0; i < checked.size(); i++) {
                    int position = checked.keyAt(i);
                    final LocalFolder folder = getAdapter().getItem(position);

                    if (checked.valueAt(i) && folder != null) {
                        selectedItems.add(folder.getName());
                    }
                }

                AccountSetupE3UndoEncrypt.actionE3UndoEncrypt(this, getAccount(), selectedItems);
                break;
        }
    }
}
