package net.atomarea.flowx.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.soundcloud.android.crop.Crop;

import java.io.File;
import java.io.FileNotFoundException;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.R;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.persistance.FileBackend;
import net.atomarea.flowx.utils.ExifHelper;
import net.atomarea.flowx.utils.FileUtils;
import net.atomarea.flowx.utils.PhoneHelper;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.pep.Avatar;

public class PublishProfilePictureActivity extends XmppActivity {

	private static final int REQUEST_CHOOSE_FILE = 0xac23;
	private ImageView avatar;
	private TextView accountTextView;
	private TextView hintOrWarning;
	private TextView secondaryHint;
	private Button cancelButton;
	private Button publishButton;
	private Uri avatarUri;
	private Uri defaultUri;
	private Account account;
	private boolean support = false;
	private OnLongClickListener backToDefaultListener = new OnLongClickListener() {

		@Override
		public boolean onLongClick(View v) {
			avatarUri = defaultUri;
			loadImageIntoPreview(defaultUri);
			return true;
		}
	};
	private boolean mInitialAccountSetup;
	private UiCallback<Avatar> avatarPublication = new UiCallback<Avatar>() {

		@Override
		public void success(Avatar object) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (mInitialAccountSetup) {
						Intent intent = new Intent(getApplicationContext(),
								StartConversationActivity.class);
						intent.putExtra("init", true);
						startActivity(intent);
					}
					Toast.makeText(PublishProfilePictureActivity.this,
							R.string.avatar_has_been_published,
							Toast.LENGTH_SHORT).show();
					finish();
				}
			});
		}

		@Override
		public void error(final int errorCode, Avatar object) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					hintOrWarning.setText(errorCode);
					hintOrWarning.setTextColor(getWarningTextColor());
					publishButton.setText(R.string.publish);
					enablePublishButton();
				}
			});

		}

		@Override
		public void userInputRequried(PendingIntent pi, Avatar object) {
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_publish_profile_picture);
		this.avatar = (ImageView) findViewById(R.id.account_image);
		this.cancelButton = (Button) findViewById(R.id.cancel_button);
		this.publishButton = (Button) findViewById(R.id.publish_button);
		this.accountTextView = (TextView) findViewById(R.id.account);
		this.hintOrWarning = (TextView) findViewById(R.id.hint_or_warning);
		this.secondaryHint = (TextView) findViewById(R.id.secondary_hint);
		this.publishButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (avatarUri != null) {
					publishButton.setText(R.string.publishing);
					disablePublishButton();
					xmppConnectionService.publishAvatar(account, avatarUri,
							avatarPublication);
				}
			}
		});
		this.cancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mInitialAccountSetup) {
					Intent intent = new Intent(getApplicationContext(),
							StartConversationActivity.class);
					if (xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1) {
						intent.putExtra("init", true);
					}
					startActivity(intent);
				}
				finish();
			}
		});
		this.avatar.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (hasStoragePermission(REQUEST_CHOOSE_FILE)) {
					chooseAvatar();
				}

			}
		});
		this.defaultUri = PhoneHelper.getSefliUri(getApplicationContext());
	}

	private void chooseAvatar() {
		Intent attachFileIntent = new Intent();
		attachFileIntent.setType("image/*");
		attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
		Intent chooser = Intent.createChooser(attachFileIntent, getString(R.string.attach_file));
		startActivityForResult(chooser, REQUEST_CHOOSE_FILE);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (grantResults.length > 0)
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (requestCode == REQUEST_CHOOSE_FILE) {
					chooseAvatar();
				}
			} else {
				Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
			}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case REQUEST_CHOOSE_FILE:
					Uri source = data.getData();
					String original = FileUtils.getPath(this, source);
					if (original != null) {
						source = Uri.parse("file://"+original);
					}
					Uri destination = Uri.fromFile(new File(getCacheDir(), "croppedAvatar"));
					final int size = getPixel(192);
					Crop.of(source, destination).asSquare().withMaxSize(size, size).start(this);
					break;
				case Crop.REQUEST_CROP:
					this.avatarUri = Uri.fromFile(new File(getCacheDir(), "croppedAvatar"));
					loadImageIntoPreview(this.avatarUri);
					break;
			}
		}
	}

	@Override
	protected void onBackendConnected() {
		if (getIntent() != null) {
			Jid jid;
			try {
				jid = Jid.fromString(getIntent().getStringExtra("account"));
			} catch (InvalidJidException e) {
				jid = null;
			}
			if (jid != null) {
				this.account = xmppConnectionService.findAccountByJid(jid);
				if (this.account.getXmppConnection() != null) {
					this.support = this.account.getXmppConnection().getFeatures().pep();
				}
				if (this.avatarUri == null) {
					if (this.account.getAvatar() != null
							|| this.defaultUri == null) {
						this.avatar.setImageBitmap(avatarService().get(account, getPixel(192)));
						if (this.defaultUri != null) {
							this.avatar
									.setOnLongClickListener(this.backToDefaultListener);
						} else {
							this.secondaryHint.setVisibility(View.INVISIBLE);
						}
						if (!support) {
							this.hintOrWarning
									.setTextColor(getWarningTextColor());
							this.hintOrWarning
									.setText(R.string.error_publish_avatar_no_server_support);
						}
					} else {
						this.avatarUri = this.defaultUri;
						loadImageIntoPreview(this.defaultUri);
						this.secondaryHint.setVisibility(View.INVISIBLE);
					}
				} else {
					loadImageIntoPreview(avatarUri);
				}
				String account;
				if (Config.DOMAIN_LOCK != null) {
					account = this.account.getJid().getLocalpart();
				} else {
					account = this.account.getJid().toBareJid().toString();
				}
				this.accountTextView.setText(account);
			}
		}

	}

	@Override
	protected void onStart() {
		super.onStart();
		if (getIntent() != null) {
			this.mInitialAccountSetup = getIntent().getBooleanExtra("setup", false);
		}
		if (this.mInitialAccountSetup) {
			this.cancelButton.setText(R.string.skip);
		}
	}

	private Bitmap loadScaledBitmap(Uri uri, int reqSize) throws FileNotFoundException {
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
		int rotation = ExifHelper.getOrientation(getContentResolver().openInputStream(uri));
		options.inSampleSize = FileBackend.calcSampleSize(options, reqSize);
		options.inJustDecodeBounds = false;
		Bitmap bm = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
		return FileBackend.rotate(bm,rotation);
	}

	protected void loadImageIntoPreview(Uri uri) {
		Bitmap bm = null;
		try {
			bm = loadScaledBitmap(uri, getPixel(192));
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (bm == null) {
			disablePublishButton();
			this.hintOrWarning.setTextColor(getWarningTextColor());
			this.hintOrWarning
					.setText(R.string.error_publish_avatar_converting);
			return;
		}
		this.avatar.setImageBitmap(bm);
		if (support) {
			enablePublishButton();
			this.publishButton.setText(R.string.publish);
			this.hintOrWarning.setText(R.string.publish_avatar_explanation);
			this.hintOrWarning.setTextColor(getPrimaryTextColor());
		} else {
			disablePublishButton();
			this.hintOrWarning.setTextColor(getWarningTextColor());
			this.hintOrWarning
					.setText(R.string.error_publish_avatar_no_server_support);
		}
		if (this.defaultUri != null && uri.equals(this.defaultUri)) {
			this.secondaryHint.setVisibility(View.INVISIBLE);
			this.avatar.setOnLongClickListener(null);
		} else if (this.defaultUri != null) {
			this.secondaryHint.setVisibility(View.VISIBLE);
			this.avatar.setOnLongClickListener(this.backToDefaultListener);
		}
	}

	protected void enablePublishButton() {
		this.publishButton.setEnabled(true);
		this.publishButton.setTextColor(getPrimaryTextColor());
	}

	protected void disablePublishButton() {
		this.publishButton.setEnabled(false);
		this.publishButton.setTextColor(getSecondaryTextColor());
	}

	public void refreshUiReal() {
		//nothing to do. This Activity doesn't implement any listeners
	}
}
