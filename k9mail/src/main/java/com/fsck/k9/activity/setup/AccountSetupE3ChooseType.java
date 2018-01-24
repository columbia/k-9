package com.fsck.k9.activity.setup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.activity.K9Activity;
import com.fsck.k9.mail.e3.E3Type;

/**
 * Created on 1/24/2018.
 *
 * @author koh
 */

public class AccountSetupE3ChooseType extends K9Activity implements OnClickListener {

    private Account mAccount;
    private Button mNextButton;
    private String accountPassword;
    private RadioGroup radioGroup;

    public static void actionStartE3ChooseType(final Context context, final Account account, final
    String password, final boolean makeDefault) {
        final Intent i = new Intent(context, AccountSetupE3ChooseType.class);
        i.putExtra(AccountSetupE3.EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(AccountSetupE3.EXTRA_MAKE_DEFAULT, makeDefault);
        i.putExtra(AccountSetupE3.EXTRA_ACCOUNT_PASSWORD, password);
        context.startActivity(i);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_e3_choose_type);

        mNextButton = (Button) findViewById(R.id.next);
        mNextButton.setOnClickListener(this);

        final String accountUuid = getIntent().getStringExtra(AccountSetupE3.EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        accountPassword = getIntent().getStringExtra(AccountSetupE3.EXTRA_ACCOUNT_PASSWORD);

        radioGroup = (RadioGroup) findViewById(R.id.e3_choose_type_radio_group);
    }

    @Override
    public void onClick(View view) {
        final int selectedRadioBtnId = radioGroup.getCheckedRadioButtonId();
        final RadioButton selectedRadioBtn = (RadioButton) findViewById(selectedRadioBtnId);
        final E3Type e3Type = E3Type.fromString(selectedRadioBtn.getText().toString());

        mAccount.setE3Type(e3Type);

        AccountSetupE3.actionStartE3Setup(this, mAccount, accountPassword, false);
    }
}
