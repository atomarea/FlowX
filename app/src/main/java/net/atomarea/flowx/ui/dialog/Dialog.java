package net.atomarea.flowx.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.atomarea.flowx.R;

/**
 * Created by Tom on 09.08.2016.
 */
public class Dialog {

    public static Dialog newInstance(Context context, int contentLayout, int stringTitle, int stringPositive, final OnClickListener clickListenerPositive) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final View container = View.inflate(context, R.layout.dialog_container, null);
        ((TextView) container.findViewById(R.id.title)).setText(stringTitle);
        LinearLayout linearLayout = (LinearLayout) container.findViewById(R.id.content);
        linearLayout.addView(View.inflate(context, contentLayout, null));
        builder.setView(container);
        builder.setPositiveButton(stringPositive, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                clickListenerPositive.onPositiveButtonClicked(container);
            }
        });
        return new Dialog(builder.create());
    }

    private AlertDialog alertDialog;

    public Dialog(AlertDialog alertDialogF) {
        alertDialog = alertDialogF;
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(alertDialog.getContext(), R.color.colorAccent));
            }
        });
    }

    public AlertDialog getAlertDialog() {
        return alertDialog;
    }

    public void show() {
        alertDialog.show();
    }

    public void dismiss() {
        alertDialog.dismiss();
    }

    public interface OnClickListener {
        void onPositiveButtonClicked(View rootView);
    }
}
