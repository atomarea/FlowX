package net.atomarea.flowx.ui.adapter.recycler;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.makeramen.roundedimageview.RoundedImageView;

import net.atomarea.flowx.R;

/**
 * Created by pixel on 10/15/2015.
 */
public class RecyclerItem extends RecyclerView.ViewHolder {
    protected RoundedImageView profile;
    protected TextView convName;

    public RecyclerItem(View itemView) {
        super(itemView);
        this.profile = (RoundedImageView) itemView.findViewById(R.id.conversation_image);
        this.convName = (TextView) itemView.findViewById(R.id.conversation_name);
    }
}
