package net.atomarea.flowx.ui.adapter;

import android.content.Context;
import android.graphics.Point;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.ui.ManageAccountActivity;
import net.atomarea.flowx.ui.XmppActivity;
import net.atomarea.flowx.ui.widget.Switch;

import java.util.List;

public class AccountAdapter extends ArrayAdapter<Account> {

    private XmppActivity activity;
    private List<Account> accounts;

    public AccountAdapter(XmppActivity activity, List<Account> objects) {
        super(activity, 0, objects);
        this.activity = activity;
        accounts = objects;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Account account = getItem(position);
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.account_row, parent, false);
        }
        TextView jid = (TextView) view.findViewById(R.id.account_jid);
        if (Config.DOMAIN_LOCK != null) {
            jid.setText(account.getJid().getLocalpart());
        } else {
            jid.setText(account.getJid().toBareJid().toString());
        }
        TextView statusView = (TextView) view.findViewById(R.id.account_status);
        ImageView imageView = (ImageView) view.findViewById(R.id.account_image);

        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(size);

        imageView.setImageBitmap(activity.avatarService().get(account, size.x));
        statusView.setText(getContext().getString(account.getStatus().getReadableId()));
        switch (account.getStatus()) {
            case ONLINE:
                statusView.setTextColor(activity.getOnlineColor());
                break;
            case DISABLED:
            case CONNECTING:
                statusView.setTextColor(activity.getSecondaryTextColor());
                break;
            default:
                statusView.setTextColor(activity.getWarningTextColor());
                break;
        }
        final Switch tglAccountState = (Switch) view.findViewById(R.id.tgl_account_status);
        final boolean isDisabled = (account.getStatus() == Account.State.DISABLED);
        tglAccountState.setChecked(!isDisabled, false);
        tglAccountState.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b == isDisabled && activity instanceof ManageAccountActivity) {
                    ((ManageAccountActivity) activity).onClickTglAccountState(account, b);
                }
            }
        });

        return view;
    }
}
