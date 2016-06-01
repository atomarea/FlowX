package net.atomarea.flowx.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.persistance.DatabaseBackend;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class WelcomeActivity extends Activity {

	boolean dbExist = checkDatabase();
	boolean backup_existing = false;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.welcome);


		//check if there is a backed up database --
		if (dbExist) {
			backup_existing = true;
		}

		final Button ImportDatabase = (Button) findViewById(R.id.import_database);
		final TextView ImportText = (TextView) findViewById(R.id.import_text);

		if (backup_existing) {
			ImportDatabase.setVisibility(View.VISIBLE);
			ImportText.setVisibility(View.VISIBLE);
		}

		ImportDatabase.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					ImportDatabase();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		final Button createAccount = (Button) findViewById(R.id.create_account);
		createAccount.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(WelcomeActivity.this, MagicCreateActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
			}
		});
		final Button useOwnProvider = (Button) findViewById(R.id.use_own_provider);
		useOwnProvider.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class));
			}
		});

	}

	private boolean checkDatabase() {

		SQLiteDatabase checkDB = null;
		String DB_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FlowX/.Database/";
		String DB_NAME = "Database.bak";

		try {
			String myPath = DB_PATH + DB_NAME;
			checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
			Log.d(Config.LOGTAG,"Backup found");
		} catch (SQLiteException e) {
			//database does't exist yet.
		}

		if (checkDB != null) {
			checkDB.close();
		}
		return checkDB != null ? true : false;
	}

	private void ImportDatabase() throws IOException {

		// Set location for the db:
		OutputStream myOutput = new FileOutputStream(this.getDatabasePath(DatabaseBackend.DATABASE_NAME));

		// Set the folder on the SDcard
		File directory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/FlowX/.Database/");

		// Set the input file stream up:
		InputStream myInput = new FileInputStream(directory.getPath() + "/Database.bak");

		// Transfer bytes from the input file to the output file
		byte[] buffer = new byte[1024];
		int length;
		while ((length = myInput.read(buffer)) > 0) {
			myOutput.write(buffer, 0, length);
		}
		Log.d(Config.LOGTAG,"Starting import of backup");

		// Close and clear the streams
		myOutput.flush();
		myOutput.close();
		myInput.close();

		Log.d(Config.LOGTAG, "New Features - Uninstall old version of FlowX Messenger");
		if (isPackageInstalled("net.atomarea.flowx")) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.uninstall_app_text)
					.setPositiveButton(R.string.uninstall, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialogInterface, int i) {
							//start the deinstallation of old version
							if (isPackageInstalled("net.atomarea.flowx")) {
								Uri packageURI_VR = Uri.parse("package:net.atomarea.flowx");
								Intent uninstallIntent_VR = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI_VR);
								if (uninstallIntent_VR.resolveActivity(getPackageManager()) != null) {
									startActivity(uninstallIntent_VR);
								}
							}
						}
					})
					.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialogInterface, int i) {
							Log.d(Config.LOGTAG, "New Features - Uninstall cancled");
							restart();
						}
					});
			builder.create().show();
		} else {
			restart();
		}

	}

	private void restart() {
		//restart app
		Log.d(Config.LOGTAG, "Restarting " + getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName()));
		Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		System.exit(0);
	}

	private boolean isPackageInstalled(String targetPackage) {
		List<ApplicationInfo> packages;
		PackageManager pm;
		pm = getPackageManager();
		packages = pm.getInstalledApplications(0);
		for (ApplicationInfo packageInfo : packages) {
			if (packageInfo.packageName.equals(targetPackage)) return true;
		}
		return false;
	}

}
