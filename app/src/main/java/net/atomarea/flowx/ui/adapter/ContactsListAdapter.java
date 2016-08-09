package net.atomarea.flowx.ui.adapter;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.atomarea.flowx.R;
import net.atomarea.flowx.async.AvatarImageUpdater;
import net.atomarea.flowx.data.Account;
import net.atomarea.flowx.data.Data;
import net.atomarea.flowx.ui.activities.ChatHistoryActivity;
import net.atomarea.flowx.ui.activities.ContactDetailActivity;
import net.atomarea.flowx.ui.activities.ContactsActivity;

/**
 * Created by Tom on 04.08.2016.
 */
public class ContactsListAdapter extends RecyclerView.Adapter<ContactsListAdapter.ViewHolder> {

    private ContactsActivity activity;

    public ContactsListAdapter(ContactsActivity activity) {
        this.activity = activity;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_contacts_list, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        Account contact = Data.getContacts().get(position);
        holder.ContactName.setText(contact.getName());
        if (holder.ContactPicture != null)
            new AvatarImageUpdater(contact.getXmppAddress(), holder.ContactPicture).execute();
        holder.ContactRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent chatHistoryActivity = new Intent(activity, ChatHistoryActivity.class);
                chatHistoryActivity.putExtra(Data.EXTRA_CHAT_HISTORY_POSITION, Data.getChatHistoryPosition(Data.getContacts().get(holder.getAdapterPosition())));
                activity.startActivity(chatHistoryActivity);
                activity.finish();
            }
        });
        holder.ContactPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent contactDetailActivity = new Intent(activity, ContactDetailActivity.class);
                contactDetailActivity.putExtra(Data.EXTRA_CONTACT_POSITION, holder.getAdapterPosition());
                activity.startActivity(contactDetailActivity);
            }
        });
    }

    @Override
    public int getItemCount() {
        if (Data.getContacts() == null) return 0;
        return Data.getContacts().size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private LinearLayout ContactRow;
        private ImageView ContactPicture;
        private TextView ContactName;

        public ViewHolder(View v) {
            super(v);
            ContactRow = (LinearLayout) v.findViewById(R.id.contact_row);
            ContactPicture = (ImageView) v.findViewById(R.id.contact_picture);
            ContactName = (TextView) v.findViewById(R.id.contact_name);
        }

    }

}
