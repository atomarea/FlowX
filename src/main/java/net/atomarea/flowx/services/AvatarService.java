package net.atomarea.flowx.services;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.Bookmark;
import net.atomarea.flowx.entities.Contact;
import net.atomarea.flowx.entities.Conversation;
import net.atomarea.flowx.entities.ListItem;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.MucOptions;
import net.atomarea.flowx.utils.UIHelper;
import net.atomarea.flowx.xmpp.OnAdvancedStreamFeaturesLoaded;
import net.atomarea.flowx.xmpp.XmppConnection;

public class AvatarService implements OnAdvancedStreamFeaturesLoaded {

	private static final int FG_COLOR = 0xFFFAFAFA;
	private static final int TRANSPARENT = 0x00000000;
	private static final int PLACEHOLDER_COLOR = 0xFF202020;

	private static final String PREFIX_CONTACT = "contact";
	private static final String PREFIX_CONVERSATION = "conversation";
	private static final String PREFIX_ACCOUNT = "account";
	private static final String PREFIX_GENERIC = "generic";

	final private ArrayList<Integer> sizes = new ArrayList<>();

	protected XmppConnectionService mXmppConnectionService = null;

	public AvatarService(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	private Bitmap get(final Contact contact, final int size, boolean cachedOnly) {
		final String KEY = key(contact, size);
		Bitmap avatar = this.mXmppConnectionService.getBitmapCache().get(KEY);
		if (avatar != null || cachedOnly) {
			return avatar;
		}
		if (contact.getProfilePhoto() != null) {
			avatar = mXmppConnectionService.getFileBackend().cropCenterSquare(Uri.parse(contact.getProfilePhoto()), size);
		}
		if (avatar == null && contact.getAvatar() != null) {
			avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatar(), size);
		}
		if (avatar == null) {
            avatar = get(contact.getDisplayName(), size, cachedOnly);
		}
		this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
		return avatar;
	}

	public Bitmap get(final MucOptions.User user, final int size, boolean cachedOnly) {
		Contact c = user.getContact();
		if (c != null && (c.getProfilePhoto() != null || c.getAvatar() != null)) {
			return get(c, size, cachedOnly);
		} else {
			return getImpl(user, size, cachedOnly);
		}
	}

	private Bitmap getImpl(final MucOptions.User user, final int size, boolean cachedOnly) {
		final String KEY = key(user, size);
		Bitmap avatar = this.mXmppConnectionService.getBitmapCache().get(KEY);
		if (avatar != null || cachedOnly) {
			return avatar;
		}
		if (user.getAvatar() != null) {
			avatar = mXmppConnectionService.getFileBackend().getAvatar(user.getAvatar(), size);
		}
		if (avatar == null) {
			Contact contact = user.getContact();
			if (contact != null) {
				avatar = get(contact, size, cachedOnly);
			} else {
				avatar = get(user.getName(), size, cachedOnly);
			}
		}
		this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
		return avatar;
	}

	public void clear(Contact contact) {
		synchronized (this.sizes) {
			for (Integer size : sizes) {
				this.mXmppConnectionService.getBitmapCache().remove(
						key(contact, size));
			}
		}
	}

	private String key(Contact contact, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_CONTACT + "_" + contact.getAccount().getJid().toBareJid() + "_"
				+ contact.getJid() + "_" + String.valueOf(size);
	}

	private String key(MucOptions.User user, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_CONTACT + "_" + user.getAccount().getJid().toBareJid() + "_"
				+ user.getFullJid() + "_" + String.valueOf(size);
	}

	public Bitmap get(ListItem item, int size) {
		return get(item,size,false);
	}

	public Bitmap get(ListItem item, int size, boolean cachedOnly) {
		if (item instanceof Contact) {
			return get((Contact) item, size,cachedOnly);
		} else if (item instanceof Bookmark) {
			Bookmark bookmark = (Bookmark) item;
			if (bookmark.getConversation() != null) {
				return get(bookmark.getConversation(), size, cachedOnly);
			} else {
				return get(bookmark.getDisplayName(), size, cachedOnly);
			}
		} else {
			return get(item.getDisplayName(), size, cachedOnly);
		}
	}

	public Bitmap get(Conversation conversation, int size) {
		return get(conversation,size,false);
	}

	public Bitmap get(Conversation conversation, int size, boolean cachedOnly) {
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			return get(conversation.getContact(), size, cachedOnly);
		} else {
			return get(conversation.getMucOptions(), size, cachedOnly);
		}
	}

	public void clear(Conversation conversation) {
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			clear(conversation.getContact());
		} else {
			clear(conversation.getMucOptions());
		}
	}

	private Bitmap get(MucOptions mucOptions, int size,  boolean cachedOnly) {
		final String KEY = key(mucOptions, size);
		Bitmap bitmap = this.mXmppConnectionService.getBitmapCache().get(KEY);
		if (bitmap != null || cachedOnly) {
			return bitmap;
		}
		final List<MucOptions.User> users = mucOptions.getUsers();
		int count = users.size();
		bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		bitmap.eraseColor(TRANSPARENT);

		if (count == 0) {
			String name = mucOptions.getConversation().getName();
			drawTile(canvas, name, 0, 0, size, size);
		} else if (count == 1) {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
			drawTile(canvas, mucOptions.getConversation().getAccount(), size / 2 + 1, 0, size, size);
		} else if (count == 2) {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
			drawTile(canvas, users.get(1), size / 2 + 1, 0, size, size);
		} else if (count == 3) {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
			drawTile(canvas, users.get(1), size / 2 + 1, 0, size, size / 2 - 1);
			drawTile(canvas, users.get(2), size / 2 + 1, size / 2 + 1, size,
					size);
		} else if (count == 4) {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size / 2 - 1);
			drawTile(canvas, users.get(1), 0, size / 2 + 1, size / 2 - 1, size);
			drawTile(canvas, users.get(2), size / 2 + 1, 0, size, size / 2 - 1);
			drawTile(canvas, users.get(3), size / 2 + 1, size / 2 + 1, size,
					size);
		} else {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size / 2 - 1);
			drawTile(canvas, users.get(1), 0, size / 2 + 1, size / 2 - 1, size);
			drawTile(canvas, users.get(2), size / 2 + 1, 0, size, size / 2 - 1);
			drawTile(canvas, "\u2026", PLACEHOLDER_COLOR, size / 2 + 1, size / 2 + 1,
					size, size);
		}
		this.mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
		return bitmap;
	}

	public void clear(MucOptions options) {
		synchronized (this.sizes) {
			for (Integer size : sizes) {
				this.mXmppConnectionService.getBitmapCache().remove(key(options, size));
			}
		}
	}

	private String key(MucOptions options, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_CONVERSATION + "_" + options.getConversation().getUuid()
				+ "_" + String.valueOf(size);
	}

	public Bitmap get(Account account, int size) {
		return get(account, size, false);
	}

	public Bitmap get(Account account, int size, boolean cachedOnly) {
		final String KEY = key(account, size);
		Bitmap avatar = mXmppConnectionService.getBitmapCache().get(KEY);
		if (avatar != null || cachedOnly) {
			return avatar;
		}
		avatar = mXmppConnectionService.getFileBackend().getAvatar(account.getAvatar(), size);
		if (avatar == null) {
			avatar = get(account.getJid().toBareJid().toString(), size,false);
		}
		mXmppConnectionService.getBitmapCache().put(KEY, avatar);
		return avatar;
	}

	public Bitmap get(Message message, int size, boolean cachedOnly) {
		final Conversation conversation = message.getConversation();
		if (message.getStatus() == Message.STATUS_RECEIVED) {
			Contact c = message.getContact();
			if (c != null && (c.getProfilePhoto() != null || c.getAvatar() != null)) {
				return get(c, size, cachedOnly);
			} else if (message.getConversation().getMode() == Conversation.MODE_MULTI){
				MucOptions.User user = conversation.getMucOptions().findUserByFullJid(message.getCounterpart());
				if (user != null) {
					return getImpl(user,size,cachedOnly);
				}
			}
			return get(UIHelper.getMessageDisplayName(message), size, cachedOnly);
		} else  {
			return get(conversation.getAccount(), size, cachedOnly);
		}
	}

	public void clear(Account account) {
		synchronized (this.sizes) {
			for (Integer size : sizes) {
				this.mXmppConnectionService.getBitmapCache().remove(key(account, size));
			}
		}
	}

	public void clear(MucOptions.User user) {
		synchronized (this.sizes) {
			for (Integer size : sizes) {
				this.mXmppConnectionService.getBitmapCache().remove(key(user, size));
			}
		}
	}

	private String key(Account account, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_ACCOUNT + "_" + account.getUuid() + "_"
				+ String.valueOf(size);
	}

	public Bitmap get(String name, int size) {
		return get(name,size,false);
	}

	public Bitmap get(final String name, final int size, boolean cachedOnly) {
		final String KEY = key(name, size);
		Bitmap bitmap = mXmppConnectionService.getBitmapCache().get(KEY);
		if (bitmap != null || cachedOnly) {
			return bitmap;
		}
		bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		final String trimmedName = name.trim();
		drawTile(canvas, trimmedName, 0, 0, size, size);
		mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
		return bitmap;
	}

	private String key(String name, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_GENERIC + "_" + name + "_" + String.valueOf(size);
	}

	private boolean drawTile(Canvas canvas, String letter, int tileColor,
						  int left, int top, int right, int bottom) {
		letter = letter.toUpperCase(Locale.getDefault());
		Paint tilePaint = new Paint(), textPaint = new Paint();
		tilePaint.setColor(tileColor);
		textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		textPaint.setColor(FG_COLOR);
		textPaint.setTypeface(Typeface.create("sans-serif-light",
				Typeface.NORMAL));
		textPaint.setTextSize((float) ((right - left) * 0.8));
		Rect rect = new Rect();

		canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
		textPaint.getTextBounds(letter, 0, 1, rect);
		float width = textPaint.measureText(letter);
		canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
				/ 2 + rect.height() / 2, textPaint);
		return true;
	}

	private boolean drawTile(Canvas canvas, MucOptions.User user, int left,
						  int top, int right, int bottom) {
		Contact contact = user.getContact();
		if (contact != null) {
			Uri uri = null;
			if (contact.getProfilePhoto() != null) {
				uri = Uri.parse(contact.getProfilePhoto());
			} else if (contact.getAvatar() != null) {
				uri = mXmppConnectionService.getFileBackend().getAvatarUri(
						contact.getAvatar());
			}
			if (drawTile(canvas, uri, left, top, right, bottom)) {
				return true;
			}
		} else if (user.getAvatar() != null) {
			Uri uri = mXmppConnectionService.getFileBackend().getAvatarUri(user.getAvatar());
			if (drawTile(canvas, uri, left, top, right, bottom)) {
				return true;
			}
		}
		String name = contact != null ? contact.getDisplayName() : user.getName();
		drawTile(canvas, name, left, top, right, bottom);
		return true;
	}

	private boolean drawTile(Canvas canvas, Account account, int left, int top, int right, int bottom) {
		String avatar = account.getAvatar();
		if (avatar != null) {
			Uri uri = mXmppConnectionService.getFileBackend().getAvatarUri(avatar);
			if (uri != null) {
				if (drawTile(canvas, uri, left, top, right, bottom)) {
					return true;
				}
			}
		}
		return drawTile(canvas, account.getJid().toBareJid().toString(), left, top, right, bottom);
	}

	private boolean drawTile(Canvas canvas, String name, int left, int top, int right, int bottom) {
		if (name != null) {
			final String letter = name.isEmpty() ? "X" : name.substring(0, 1);
			final int color = UIHelper.getColorForName(name);
			drawTile(canvas, letter, color, left, top, right, bottom);
			return true;
		}
		return false;
	}

	private boolean drawTile(Canvas canvas, Uri uri, int left, int top, int right, int bottom) {
		if (uri != null) {
			Bitmap bitmap = mXmppConnectionService.getFileBackend()
					.cropCenter(uri, bottom - top, right - left);
			if (bitmap != null) {
				drawTile(canvas, bitmap, left, top, right, bottom);
				return true;
			}
		}
		return false;
	}

	private boolean drawTile(Canvas canvas, Bitmap bm, int dstleft, int dsttop, int dstright, int dstbottom) {
		Rect dst = new Rect(dstleft, dsttop, dstright, dstbottom);
		canvas.drawBitmap(bm, null, dst, null);
		return true;
	}

	@Override
	public void onAdvancedStreamFeaturesAvailable(Account account) {
		XmppConnection.Features features = account.getXmppConnection().getFeatures();
		if (features.pep() && !features.pepPersistent()) {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": has pep but is not persistent");
			if (account.getAvatar() != null) {
				mXmppConnectionService.republishAvatarIfNeeded(account);
			}
		}
	}
}
