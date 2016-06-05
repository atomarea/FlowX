package net.atomarea.flowx.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.preference.PreferenceManager;
import android.util.Log;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.ui.ConversationActivity;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ExceptionHelper {
	private static SimpleDateFormat DATE_FORMATs = new SimpleDateFormat("yyyy-MM-dd");
	public static void init(Context context) {
		if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler)) {
			Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(
					context));
		}
	}

	public static boolean checkForCrash(ConversationActivity activity, final XmppConnectionService service) {
		try {
			final SharedPreferences preferences = PreferenceManager
					.getDefaultSharedPreferences(activity);
			boolean neverSend = preferences.getBoolean("never_send", false);
			if (neverSend || Config.BUG_REPORTS == null) {
				return false;
			}
			List<Account> accounts = service.getAccounts();
			Account account = null;
			for (int i = 0; i < accounts.size(); ++i) {
				if (!accounts.get(i).isOptionSet(Account.OPTION_DISABLED)) {
					account = accounts.get(i);
					break;
				}
			}
			if (account == null) {
				return false;
			}
			final Account finalAccount = account;
			FileInputStream file = activity.openFileInput("stacktrace.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(file);
			BufferedReader stacktrace = new BufferedReader(inputStreamReader);
			final StringBuilder report = new StringBuilder();
			PackageManager pm = activity.getPackageManager();
			PackageInfo packageInfo;
			try {
				packageInfo = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
				report.append("Version: " + packageInfo.versionName + '\n');
				report.append("Last Update: " + DATE_FORMATs.format(new Date(packageInfo.lastUpdateTime)) + '\n');
				Signature[] signatures = packageInfo.signatures;
				if (signatures != null && signatures.length >= 1) {
					report.append("SHA-1: " + CryptoHelper.getFingerprintCert(packageInfo.signatures[0].toByteArray()) + "\n");
				}
				report.append('\n');
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
			String line;
			while ((line = stacktrace.readLine()) != null) {
				report.append(line);
				report.append('\n');
			}
			file.close();
			activity.deleteFile("stacktrace.txt");
			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setTitle(activity.getString(R.string.crash_report_title));
			builder.setMessage(activity.getText(R.string.crash_report_message));
			builder.setPositiveButton(activity.getText(R.string.send_now),
					new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {

							Log.d(Config.LOGTAG, "using account="
									+ finalAccount.getJid().toBareJid()
									+ " to send in stack trace");
							Conversation conversation = null;
							try {
								conversation = service.findOrCreateConversation(finalAccount,
										Jid.fromString(Config.BUG_REPORTS), false);
							} catch (final InvalidJidException ignored) {
							}
							Message message = new Message(conversation, report
									.toString(), Message.ENCRYPTION_NONE);
							service.sendMessage(message);
						}
					});
			builder.setNegativeButton(activity.getText(R.string.send_never),
					new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							preferences.edit().putBoolean("never_send", true)
									.apply();
						}
					});
			builder.create().show();
			return true;
		} catch (final IOException ignored) {
			return false;
		}
	}

	public static void writeToStacktraceFile(Context context, String msg) {
		try {
			OutputStream os = context.openFileOutput("stacktrace.txt", Context.MODE_PRIVATE);
			os.write(msg.getBytes());
			os.flush();
			os.close();
		} catch (IOException ignored) {
		}
	}
}
