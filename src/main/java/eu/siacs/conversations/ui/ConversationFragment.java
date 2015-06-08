package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import net.java.otr4j.session.SessionStatus;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.DownloadablePlaceholder;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity.OnPresenceSelected;
import eu.siacs.conversations.ui.XmppActivity.OnValueEdited;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.adapter.MessageAdapter.OnContactPictureClicked;
import eu.siacs.conversations.ui.adapter.MessageAdapter.OnContactPictureLongClicked;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ConversationFragment extends Fragment implements EditMessage.KeyboardListener {

	protected Conversation conversation;
	private OnClickListener leaveMuc = new OnClickListener() {

		@Override
		public void onClick(View v) {
			activity.endConversation(conversation);
		}
	};
	private OnClickListener joinMuc = new OnClickListener() {

		@Override
		public void onClick(View v) {
			activity.xmppConnectionService.joinMuc(conversation);
		}
	};
	private OnClickListener enterPassword = new OnClickListener() {

		@Override
		public void onClick(View v) {
			MucOptions muc = conversation.getMucOptions();
			String password = muc.getPassword();
			if (password == null) {
				password = "";
			}
			activity.quickPasswordEdit(password, new OnValueEdited() {

				@Override
				public void onValueEdited(String value) {
					activity.xmppConnectionService.providePasswordForMuc(
							conversation, value);
				}
			});
		}
	};
	protected ListView messagesView;
	final protected List<Message> messageList = new ArrayList<>();
	protected MessageAdapter messageListAdapter;
	private EditMessage mEditMessage;
	private ImageButton mSendButton;
	private RelativeLayout snackbar;
	private TextView snackbarMessage;
	private TextView snackbarAction;
	private boolean messagesLoaded = true;
	private Toast messageLoaderToast;

	private OnScrollListener mOnScrollListener = new OnScrollListener() {

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {
			// TODO Auto-generated method stub

		}

		private int getIndexOf(String uuid, List<Message> messages) {
			if (uuid == null) {
				return 0;
			}
			for(int i = 0; i < messages.size(); ++i) {
				if (uuid.equals(messages.get(i).getUuid())) {
					return i;
				} else {
					Message next = messages.get(i);
					while(next != null && next.wasMergedIntoPrevious()) {
						if (uuid.equals(next.getUuid())) {
							return i;
						}
						next = next.next();
					}

				}
			}
			return 0;
		}

		@Override
		public void onScroll(AbsListView view, int firstVisibleItem,
							 int visibleItemCount, int totalItemCount) {
			synchronized (ConversationFragment.this.messageList) {
				if (firstVisibleItem < 5 && messagesLoaded && messageList.size() > 0) {
					long timestamp = ConversationFragment.this.messageList.get(0).getTimeSent();
					messagesLoaded = false;
					activity.xmppConnectionService.loadMoreMessages(conversation, timestamp, new XmppConnectionService.OnMoreMessagesLoaded() {
						@Override
						public void onMoreMessagesLoaded(final int c, Conversation conversation) {
							if (ConversationFragment.this.conversation != conversation) {
								return;
							}
							activity.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									final int oldPosition = messagesView.getFirstVisiblePosition();
									Message message = messageList.get(oldPosition);
									String uuid = message != null ? message.getUuid() : null;
									View v = messagesView.getChildAt(0);
									final int pxOffset = (v == null) ? 0 : v.getTop();
									ConversationFragment.this.conversation.populateWithMessages(ConversationFragment.this.messageList);
									updateStatusMessages();
									messageListAdapter.notifyDataSetChanged();
									int pos = getIndexOf(uuid,messageList);
									messagesView.setSelectionFromTop(pos, pxOffset);
									messagesLoaded = true;
									if (messageLoaderToast != null) {
										messageLoaderToast.cancel();
									}
								}
							});
						}

						@Override
						public void informUser(final int resId) {

							activity.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (messageLoaderToast != null) {
										messageLoaderToast.cancel();
									}
									if (ConversationFragment.this.conversation != conversation) {
										return;
									}
									messageLoaderToast = Toast.makeText(activity, resId, Toast.LENGTH_LONG);
									messageLoaderToast.show();
								}
							});

						}
					});

				}
			}
		}
	};
	private IntentSender askForPassphraseIntent = null;
	protected OnClickListener clickToDecryptListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (activity.hasPgp() && askForPassphraseIntent != null) {
				try {
					getActivity().startIntentSenderForResult(
							askForPassphraseIntent,
							ConversationActivity.REQUEST_DECRYPT_PGP, null, 0,
							0, 0);
					askForPassphraseIntent = null;
				} catch (SendIntentException e) {
					//
				}
			}
		}
	};
	protected OnClickListener clickToVerify = new OnClickListener() {

		@Override
		public void onClick(View v) {
			activity.verifyOtrSessionDialog(conversation, v);
		}
	};
	private ConcurrentLinkedQueue<Message> mEncryptedMessages = new ConcurrentLinkedQueue<>();
	private boolean mDecryptJobRunning = false;
	private OnEditorActionListener mEditorActionListener = new OnEditorActionListener() {

		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			if (actionId == EditorInfo.IME_ACTION_SEND) {
				InputMethodManager imm = (InputMethodManager) v.getContext()
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				sendMessage();
				return true;
			} else {
				return false;
			}
		}
	};
	private OnClickListener mSendButtonListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Object tag = v.getTag();
			if (tag instanceof SendButtonAction) {
				SendButtonAction action = (SendButtonAction) tag;
				switch (action) {
					case TAKE_PHOTO:
						activity.attachFile(ConversationActivity.ATTACHMENT_CHOICE_TAKE_PHOTO);
						break;
					case SEND_LOCATION:
						activity.attachFile(ConversationActivity.ATTACHMENT_CHOICE_LOCATION);
						break;
					case RECORD_VOICE:
						activity.attachFile(ConversationActivity.ATTACHMENT_CHOICE_RECORD_VOICE);
						break;
					case CHOOSE_PICTURE:
						activity.attachFile(ConversationActivity.ATTACHMENT_CHOICE_CHOOSE_IMAGE);
						break;
					case CANCEL:
						if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
							conversation.setNextCounterpart(null);
							updateChatMsgHint();
							updateSendButton();
						}
						break;
					default:
						sendMessage();
				}
			} else {
				sendMessage();
			}
		}
	};
	private OnClickListener clickToMuc = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Intent intent = new Intent(getActivity(), ConferenceDetailsActivity.class);
			intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
			intent.putExtra("uuid", conversation.getUuid());
			startActivity(intent);
		}
	};
	private ConversationActivity activity;
	private Message selectedMessage;

	private void sendMessage() {
		final String body = mEditMessage.getText().toString();
		if (body.length() == 0 || this.conversation == null) {
			return;
		}
		Message message = new Message(conversation, body, conversation.getNextEncryption(activity.forceEncryption()));
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			if (conversation.getNextCounterpart() != null) {
				message.setCounterpart(conversation.getNextCounterpart());
				message.setType(Message.TYPE_PRIVATE);
			}
		}
		if (conversation.getNextEncryption(activity.forceEncryption()) == Message.ENCRYPTION_OTR) {
			sendOtrMessage(message);
		} else if (conversation.getNextEncryption(activity.forceEncryption()) == Message.ENCRYPTION_PGP) {
			sendPgpMessage(message);
		} else {
			sendPlainTextMessage(message);
		}
	}

	public void updateChatMsgHint() {
		if (conversation.getMode() == Conversation.MODE_MULTI
				&& conversation.getNextCounterpart() != null) {
			this.mEditMessage.setHint(getString(
					R.string.send_private_message_to,
					conversation.getNextCounterpart().getResourcepart()));
		} else {
			switch (conversation.getNextEncryption(activity.forceEncryption())) {
				case Message.ENCRYPTION_NONE:
					mEditMessage
							.setHint(getString(R.string.send_plain_text_message));
					break;
				case Message.ENCRYPTION_OTR:
					mEditMessage.setHint(getString(R.string.send_otr_message));
					break;
				case Message.ENCRYPTION_PGP:
					mEditMessage.setHint(getString(R.string.send_pgp_message));
					break;
				default:
					break;
			}
			getActivity().invalidateOptionsMenu();
		}
	}

	private void setupIme() {
		if (((ConversationActivity) getActivity()).usingEnterKey()) {
			mEditMessage.setInputType(mEditMessage.getInputType() & (~InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
		} else {
			mEditMessage.setInputType(mEditMessage.getInputType() | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_conversation, container, false);
		view.setOnClickListener(null);
		mEditMessage = (EditMessage) view.findViewById(R.id.textinput);
		setupIme();
		mEditMessage.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (activity != null) {
					activity.hideConversationsOverview();
				}
			}
		});
		mEditMessage.setOnEditorActionListener(mEditorActionListener);

		mSendButton = (ImageButton) view.findViewById(R.id.textSendButton);
		mSendButton.setOnClickListener(this.mSendButtonListener);

		snackbar = (RelativeLayout) view.findViewById(R.id.snackbar);
		snackbarMessage = (TextView) view.findViewById(R.id.snackbar_message);
		snackbarAction = (TextView) view.findViewById(R.id.snackbar_action);

		messagesView = (ListView) view.findViewById(R.id.messages_view);
		messagesView.setOnScrollListener(mOnScrollListener);
		messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
		messageListAdapter = new MessageAdapter((ConversationActivity) getActivity(), this.messageList);
		messageListAdapter.setOnContactPictureClicked(new OnContactPictureClicked() {

			@Override
			public void onContactPictureClicked(Message message) {
				if (message.getStatus() <= Message.STATUS_RECEIVED) {
					if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
						if (message.getCounterpart() != null) {
							if (!message.getCounterpart().isBareJid()) {
								highlightInConference(message.getCounterpart().getResourcepart());
							} else {
								highlightInConference(message.getCounterpart().toString());
							}
						}
					} else {
						activity.switchToContactDetails(message.getContact());
					}
				} else {
					Account account = message.getConversation().getAccount();
					Intent intent = new Intent(activity, EditAccountActivity.class);
					intent.putExtra("jid", account.getJid().toBareJid().toString());
					startActivity(intent);
				}
			}
		});
		messageListAdapter
				.setOnContactPictureLongClicked(new OnContactPictureLongClicked() {

					@Override
					public void onContactPictureLongClicked(Message message) {
						if (message.getStatus() <= Message.STATUS_RECEIVED) {
							if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
								if (message.getCounterpart() != null) {
									privateMessageWith(message.getCounterpart());
								}
							}
						} else {
							activity.showQrCode();
						}
					}
				});
		messagesView.setAdapter(messageListAdapter);

		registerForContextMenu(messagesView);

		return view;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
									ContextMenuInfo menuInfo) {
		synchronized (this.messageList) {
			super.onCreateContextMenu(menu, v, menuInfo);
			AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
			this.selectedMessage = this.messageList.get(acmi.position);
			populateContextMenu(menu);
		}
	}

	private void populateContextMenu(ContextMenu menu) {
		final Message m = this.selectedMessage;
		if (m.getType() != Message.TYPE_STATUS) {
			activity.getMenuInflater().inflate(R.menu.message_context, menu);
			menu.setHeaderTitle(R.string.message_options);
			MenuItem copyText = menu.findItem(R.id.copy_text);
			MenuItem shareWith = menu.findItem(R.id.share_with);
			MenuItem sendAgain = menu.findItem(R.id.send_again);
			MenuItem copyUrl = menu.findItem(R.id.copy_url);
			MenuItem downloadImage = menu.findItem(R.id.download_image);
			MenuItem cancelTransmission = menu.findItem(R.id.cancel_transmission);
			if ((m.getType() != Message.TYPE_TEXT && m.getType() != Message.TYPE_PRIVATE)
					|| m.getDownloadable() != null || GeoHelper.isGeoUri(m.getBody())) {
				copyText.setVisible(false);
			}
			if ((m.getType() == Message.TYPE_TEXT
					|| m.getType() == Message.TYPE_PRIVATE
					|| m.getDownloadable() != null)
					&& (!GeoHelper.isGeoUri(m.getBody()))) {
				shareWith.setVisible(false);
			}
			if (m.getStatus() != Message.STATUS_SEND_FAILED) {
				sendAgain.setVisible(false);
			}
			if (((m.getType() != Message.TYPE_IMAGE && m.getDownloadable() == null)
					|| m.getImageParams().url == null) && !GeoHelper.isGeoUri(m.getBody())) {
				copyUrl.setVisible(false);
			}
			if (m.getType() != Message.TYPE_TEXT
					|| m.getDownloadable() != null
					|| !m.bodyContainsDownloadable()) {
				downloadImage.setVisible(false);
			}
			if (!((m.getDownloadable() != null && !(m.getDownloadable() instanceof DownloadablePlaceholder))
					|| (m.isFileOrImage() && (m.getStatus() == Message.STATUS_WAITING
					|| m.getStatus() == Message.STATUS_OFFERED)))) {
				cancelTransmission.setVisible(false);
			}
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.share_with:
				shareWith(selectedMessage);
				return true;
			case R.id.copy_text:
				copyText(selectedMessage);
				return true;
			case R.id.send_again:
				resendMessage(selectedMessage);
				return true;
			case R.id.copy_url:
				copyUrl(selectedMessage);
				return true;
			case R.id.download_image:
				downloadImage(selectedMessage);
				return true;
			case R.id.cancel_transmission:
				cancelTransmission(selectedMessage);
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	private void shareWith(Message message) {
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		if (GeoHelper.isGeoUri(message.getBody())) {
			shareIntent.putExtra(Intent.EXTRA_TEXT, message.getBody());
			shareIntent.setType("text/plain");
		} else {
			shareIntent.putExtra(Intent.EXTRA_STREAM,
					activity.xmppConnectionService.getFileBackend()
							.getJingleFileUri(message));
			shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			String path = message.getRelativeFilePath();
			String mime = path == null ? null : URLConnection.guessContentTypeFromName(path);
			if (mime == null) {
				mime = "image/webp";
			}
			shareIntent.setType(mime);
		}
		activity.startActivity(Intent.createChooser(shareIntent, getText(R.string.share_with)));
	}

	private void copyText(Message message) {
		if (activity.copyTextToClipboard(message.getMergedBody(),
				R.string.message_text)) {
			Toast.makeText(activity, R.string.message_copied_to_clipboard,
					Toast.LENGTH_SHORT).show();
		}
	}

	private void resendMessage(Message message) {
		if (message.getType() == Message.TYPE_FILE || message.getType() == Message.TYPE_IMAGE) {
			DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
			if (!file.exists()) {
				Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
				message.setDownloadable(new DownloadablePlaceholder(Downloadable.STATUS_DELETED));
				return;
			}
		}
		activity.xmppConnectionService.resendFailedMessages(message);
	}

	private void copyUrl(Message message) {
		final String url;
		final int resId;
		if (GeoHelper.isGeoUri(message.getBody())) {
			resId = R.string.location;
			url = message.getBody();
		} else {
			resId = R.string.image_url;
			url = message.getImageParams().url.toString();
		}
		if (activity.copyTextToClipboard(url, resId)) {
			Toast.makeText(activity, R.string.url_copied_to_clipboard,
					Toast.LENGTH_SHORT).show();
		}
	}

	private void downloadImage(Message message) {
		activity.xmppConnectionService.getHttpConnectionManager()
				.createNewConnection(message);
	}

	private void cancelTransmission(Message message) {
		Downloadable downloadable = message.getDownloadable();
		if (downloadable != null) {
			downloadable.cancel();
		} else {
			activity.xmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
		}
	}

	protected void privateMessageWith(final Jid counterpart) {
		this.mEditMessage.setText("");
		this.conversation.setNextCounterpart(counterpart);
		updateChatMsgHint();
		updateSendButton();
	}

	protected void highlightInConference(String nick) {
		String oldString = mEditMessage.getText().toString().trim();
		if (oldString.isEmpty() || mEditMessage.getSelectionStart() == 0) {
			mEditMessage.getText().insert(0, nick + ": ");
		} else {
			if (mEditMessage.getText().charAt(
					mEditMessage.getSelectionStart() - 1) != ' ') {
				nick = " " + nick;
			}
			mEditMessage.getText().insert(mEditMessage.getSelectionStart(),
					nick + " ");
		}
	}

	@Override
	public void onStop() {
		mDecryptJobRunning = false;
		super.onStop();
		if (this.conversation != null) {
			final String msg = mEditMessage.getText().toString();
			this.conversation.setNextMessage(msg);
			updateChatState(this.conversation, msg);
		}
	}

	private void updateChatState(final Conversation conversation, final String msg) {
		ChatState state = msg.length() == 0 ? Config.DEFAULT_CHATSTATE : ChatState.PAUSED;
		Account.State status = conversation.getAccount().getStatus();
		if (status == Account.State.ONLINE && conversation.setOutgoingChatState(state)) {
			activity.xmppConnectionService.sendChatState(conversation);
		}
	}

	public void reInit(Conversation conversation) {
		if (conversation == null) {
			return;
		}

		this.activity = (ConversationActivity) getActivity();

		if (this.conversation != null) {
			final String msg = mEditMessage.getText().toString();
			this.conversation.setNextMessage(msg);
			if (this.conversation != conversation) {
				updateChatState(this.conversation, msg);
			}
			this.conversation.trim();
		}

		this.askForPassphraseIntent = null;
		this.conversation = conversation;
		this.mDecryptJobRunning = false;
		this.mEncryptedMessages.clear();
		if (this.conversation.getMode() == Conversation.MODE_MULTI) {
			this.conversation.setNextCounterpart(null);
		}
		this.mEditMessage.setKeyboardListener(null);
		this.mEditMessage.setText("");
		this.mEditMessage.append(this.conversation.getNextMessage());
		this.mEditMessage.setKeyboardListener(this);
		this.messagesView.setAdapter(messageListAdapter);
		updateMessages();
		this.messagesLoaded = true;
		int size = this.messageList.size();
		if (size > 0) {
			messagesView.setSelection(size - 1);
		}
	}

	private OnClickListener mUnblockClickListener = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			v.post(new Runnable() {
				@Override
				public void run() {
					v.setVisibility(View.INVISIBLE);
				}
			});
			if (conversation.isDomainBlocked()) {
				BlockContactDialog.show(activity, activity.xmppConnectionService, conversation);
			} else {
				activity.unblockConversation(conversation);
			}
		}
	};

	private OnClickListener mAddBackClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			final Contact contact = conversation == null ? null : conversation.getContact();
			if (contact != null) {
				activity.xmppConnectionService.createContact(contact);
				activity.switchToContactDetails(contact);
			}
		}
	};

	private OnClickListener mUnmuteClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			activity.unmuteConversation(conversation);
		}
	};

	private OnClickListener mAnswerSmpClickListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			Intent intent = new Intent(activity, VerifyOTRActivity.class);
			intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
			intent.putExtra("contact", conversation.getContact().getJid().toBareJid().toString());
			intent.putExtra("account", conversation.getAccount().getJid().toBareJid().toString());
			intent.putExtra("mode", VerifyOTRActivity.MODE_ANSWER_QUESTION);
			startActivity(intent);
		}
	};

	private void updateSnackBar(final Conversation conversation) {
		final Account account = conversation.getAccount();
		final Contact contact = conversation.getContact();
		final int mode = conversation.getMode();
		if (conversation.isBlocked()) {
			showSnackbar(R.string.contact_blocked, R.string.unblock, this.mUnblockClickListener);
		} else if (!contact.showInRoster() && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
			showSnackbar(R.string.contact_added_you, R.string.add_back, this.mAddBackClickListener);
		} else if (mode == Conversation.MODE_MULTI
				&& !conversation.getMucOptions().online()
				&& account.getStatus() == Account.State.ONLINE) {
			switch (conversation.getMucOptions().getError()) {
				case MucOptions.ERROR_NICK_IN_USE:
					showSnackbar(R.string.nick_in_use, R.string.edit, clickToMuc);
					break;
				case MucOptions.ERROR_UNKNOWN:
					showSnackbar(R.string.conference_not_found, R.string.leave, leaveMuc);
					break;
				case MucOptions.ERROR_PASSWORD_REQUIRED:
					showSnackbar(R.string.conference_requires_password, R.string.enter_password, enterPassword);
					break;
				case MucOptions.ERROR_BANNED:
					showSnackbar(R.string.conference_banned, R.string.leave, leaveMuc);
					break;
				case MucOptions.ERROR_MEMBERS_ONLY:
					showSnackbar(R.string.conference_members_only, R.string.leave, leaveMuc);
					break;
				case MucOptions.KICKED_FROM_ROOM:
					showSnackbar(R.string.conference_kicked, R.string.join, joinMuc);
					break;
				default:
					break;
			}
		} else if (askForPassphraseIntent != null) {
			showSnackbar(R.string.openpgp_messages_found, R.string.decrypt, clickToDecryptListener);
		} else if (mode == Conversation.MODE_SINGLE
				&& conversation.smpRequested()) {
			showSnackbar(R.string.smp_requested, R.string.verify, this.mAnswerSmpClickListener);
		} else if (mode == Conversation.MODE_SINGLE
				&& conversation.hasValidOtrSession()
				&& (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED)
				&& (!conversation.isOtrFingerprintVerified())) {
			showSnackbar(R.string.unknown_otr_fingerprint, R.string.verify, clickToVerify);
		} else if (conversation.isMuted()) {
			showSnackbar(R.string.notifications_disabled, R.string.enable, this.mUnmuteClickListener);
		} else {
			hideSnackbar();
		}
	}

	public void updateMessages() {
		synchronized (this.messageList) {
			if (getView() == null) {
				return;
			}
			final ConversationActivity activity = (ConversationActivity) getActivity();
			if (this.conversation != null) {
				updateSnackBar(this.conversation);
				conversation.populateWithMessages(ConversationFragment.this.messageList);
				for (final Message message : this.messageList) {
					if (message.getEncryption() == Message.ENCRYPTION_PGP
							&& (message.getStatus() == Message.STATUS_RECEIVED || message
							.getStatus() >= Message.STATUS_SEND)
							&& message.getDownloadable() == null) {
						if (!mEncryptedMessages.contains(message)) {
							mEncryptedMessages.add(message);
						}
					}
				}
				decryptNext();
				updateStatusMessages();
				this.messageListAdapter.notifyDataSetChanged();
				updateChatMsgHint();
				if (!activity.isConversationsOverviewVisable() || !activity.isConversationsOverviewHideable()) {
					activity.sendReadMarkerIfNecessary(conversation);
				}
				this.updateSendButton();
			}
		}
	}

	private void decryptNext() {
		Message next = this.mEncryptedMessages.peek();
		PgpEngine engine = activity.xmppConnectionService.getPgpEngine();

		if (next != null && engine != null && !mDecryptJobRunning) {
			mDecryptJobRunning = true;
			engine.decrypt(next, new UiCallback<Message>() {

				@Override
				public void userInputRequried(PendingIntent pi, Message message) {
					mDecryptJobRunning = false;
					askForPassphraseIntent = pi.getIntentSender();
					updateSnackBar(conversation);
				}

				@Override
				public void success(Message message) {
					mDecryptJobRunning = false;
					try {
						mEncryptedMessages.remove();
					} catch (final NoSuchElementException ignored) {

					}
					askForPassphraseIntent = null;
					activity.xmppConnectionService.updateMessage(message);
				}

				@Override
				public void error(int error, Message message) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
					mDecryptJobRunning = false;
					try {
						mEncryptedMessages.remove();
					} catch (final NoSuchElementException ignored) {

					}
					activity.xmppConnectionService.updateConversationUi();
				}
			});
		}
	}

	private void messageSent() {
		int size = this.messageList.size();
		messagesView.setSelection(size - 1);
		mEditMessage.setText("");
		updateChatMsgHint();
	}

	enum SendButtonAction {TEXT, TAKE_PHOTO, SEND_LOCATION, RECORD_VOICE, CANCEL, CHOOSE_PICTURE}

	private int getSendButtonImageResource(SendButtonAction action, int status) {
		switch (action) {
			case TEXT:
				switch (status) {
					case Presences.CHAT:
					case Presences.ONLINE:
						return R.drawable.ic_send_text_online;
					case Presences.AWAY:
						return R.drawable.ic_send_text_away;
					case Presences.XA:
					case Presences.DND:
						return R.drawable.ic_send_text_dnd;
					default:
						return R.drawable.ic_send_text_offline;
				}
			case TAKE_PHOTO:
				switch (status) {
					case Presences.CHAT:
					case Presences.ONLINE:
						return R.drawable.ic_send_photo_online;
					case Presences.AWAY:
						return R.drawable.ic_send_photo_away;
					case Presences.XA:
					case Presences.DND:
						return R.drawable.ic_send_photo_dnd;
					default:
						return R.drawable.ic_send_photo_offline;
				}
			case RECORD_VOICE:
				switch (status) {
					case Presences.CHAT:
					case Presences.ONLINE:
						return R.drawable.ic_send_voice_online;
					case Presences.AWAY:
						return R.drawable.ic_send_voice_away;
					case Presences.XA:
					case Presences.DND:
						return R.drawable.ic_send_voice_dnd;
					default:
						return R.drawable.ic_send_voice_offline;
				}
			case SEND_LOCATION:
				switch (status) {
					case Presences.CHAT:
					case Presences.ONLINE:
						return R.drawable.ic_send_location_online;
					case Presences.AWAY:
						return R.drawable.ic_send_location_away;
					case Presences.XA:
					case Presences.DND:
						return R.drawable.ic_send_location_dnd;
					default:
						return R.drawable.ic_send_location_offline;
				}
			case CANCEL:
				switch (status) {
					case Presences.CHAT:
					case Presences.ONLINE:
						return R.drawable.ic_send_cancel_online;
					case Presences.AWAY:
						return R.drawable.ic_send_cancel_away;
					case Presences.XA:
					case Presences.DND:
						return R.drawable.ic_send_cancel_dnd;
					default:
						return R.drawable.ic_send_cancel_offline;
				}
			case CHOOSE_PICTURE:
				switch (status) {
					case Presences.CHAT:
					case Presences.ONLINE:
						return R.drawable.ic_send_picture_online;
					case Presences.AWAY:
						return R.drawable.ic_send_picture_away;
					case Presences.XA:
					case Presences.DND:
						return R.drawable.ic_send_picture_dnd;
					default:
						return R.drawable.ic_send_picture_offline;
				}
		}
		return R.drawable.ic_send_text_offline;
	}

	public void updateSendButton() {
		final Conversation c = this.conversation;
		final SendButtonAction action;
		final int status;
		final boolean empty = this.mEditMessage == null || this.mEditMessage.getText().length() == 0;
		if (c.getMode() == Conversation.MODE_MULTI) {
			if (empty && c.getNextCounterpart() != null) {
				action = SendButtonAction.CANCEL;
			} else {
				action = SendButtonAction.TEXT;
			}
		} else {
			if (empty) {
				String setting = activity.getPreferences().getString("quick_action","recent");
				if (!setting.equals("none") && UIHelper.receivedLocationQuestion(conversation.getLatestMessage())) {
					setting = "location";
				} else if (setting.equals("recent")) {
					setting = activity.getPreferences().getString("recently_used_quick_action","text");
				}
				switch (setting) {
					case "photo":
						action = SendButtonAction.TAKE_PHOTO;
						break;
					case "location":
						action = SendButtonAction.SEND_LOCATION;
						break;
					case "voice":
						action = SendButtonAction.RECORD_VOICE;
						break;
					case "picture":
						action = SendButtonAction.CHOOSE_PICTURE;
						break;
					default:
						action = SendButtonAction.TEXT;
						break;
				}
			} else {
				action = SendButtonAction.TEXT;
			}
		}
		if (activity.useSendButtonToIndicateStatus() && c != null
				&& c.getAccount().getStatus() == Account.State.ONLINE) {
			if (c.getMode() == Conversation.MODE_SINGLE) {
				status = c.getContact().getMostAvailableStatus();
			} else {
				status = c.getMucOptions().online() ? Presences.ONLINE : Presences.OFFLINE;
			}
		} else {
			status = Presences.OFFLINE;
		}
		this.mSendButton.setTag(action);
		this.mSendButton.setImageResource(getSendButtonImageResource(action, status));
	}

	protected void updateStatusMessages() {
		synchronized (this.messageList) {
			if (conversation.getMode() == Conversation.MODE_SINGLE) {
				ChatState state = conversation.getIncomingChatState();
				if (state == ChatState.COMPOSING) {
					this.messageList.add(Message.createStatusMessage(conversation, getString(R.string.contact_is_typing, conversation.getName())));
				} else if (state == ChatState.PAUSED) {
					this.messageList.add(Message.createStatusMessage(conversation, getString(R.string.contact_has_stopped_typing, conversation.getName())));
				} else {
					for (int i = this.messageList.size() - 1; i >= 0; --i) {
						if (this.messageList.get(i).getStatus() == Message.STATUS_RECEIVED) {
							return;
						} else {
							if (this.messageList.get(i).getStatus() == Message.STATUS_SEND_DISPLAYED) {
								this.messageList.add(i + 1,
										Message.createStatusMessage(conversation, getString(R.string.contact_has_read_up_to_this_point, conversation.getName())));
								return;
							}
						}
					}
				}
			}
		}
	}

	protected void showSnackbar(final int message, final int action,
								final OnClickListener clickListener) {
		snackbar.setVisibility(View.VISIBLE);
		snackbar.setOnClickListener(null);
		snackbarMessage.setText(message);
		snackbarMessage.setOnClickListener(null);
		snackbarAction.setVisibility(View.VISIBLE);
		snackbarAction.setText(action);
		snackbarAction.setOnClickListener(clickListener);
	}

	protected void hideSnackbar() {
		snackbar.setVisibility(View.GONE);
	}

	protected void sendPlainTextMessage(Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		activity.xmppConnectionService.sendMessage(message);
		messageSent();
	}

	protected void sendPgpMessage(final Message message) {
		final ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		final Contact contact = message.getConversation().getContact();
		if (activity.hasPgp()) {
			if (conversation.getMode() == Conversation.MODE_SINGLE) {
				if (contact.getPgpKeyId() != 0) {
					xmppService.getPgpEngine().hasKey(contact,
							new UiCallback<Contact>() {

								@Override
								public void userInputRequried(PendingIntent pi,
															  Contact contact) {
									activity.runIntent(
											pi,
											ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
								}

								@Override
								public void success(Contact contact) {
									messageSent();
									activity.encryptTextMessage(message);
								}

								@Override
								public void error(int error, Contact contact) {

								}
							});

				} else {
					showNoPGPKeyDialog(false,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
													int which) {
									conversation
											.setNextEncryption(Message.ENCRYPTION_NONE);
									xmppService.databaseBackend
											.updateConversation(conversation);
									message.setEncryption(Message.ENCRYPTION_NONE);
									xmppService.sendMessage(message);
									messageSent();
								}
							});
				}
			} else {
				if (conversation.getMucOptions().pgpKeysInUse()) {
					if (!conversation.getMucOptions().everybodyHasKeys()) {
						Toast warning = Toast
								.makeText(getActivity(),
										R.string.missing_public_keys,
										Toast.LENGTH_LONG);
						warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
						warning.show();
					}
					activity.encryptTextMessage(message);
					messageSent();
				} else {
					showNoPGPKeyDialog(true,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
													int which) {
									conversation
											.setNextEncryption(Message.ENCRYPTION_NONE);
									message.setEncryption(Message.ENCRYPTION_NONE);
									xmppService.databaseBackend
											.updateConversation(conversation);
									xmppService.sendMessage(message);
									messageSent();
								}
							});
				}
			}
		} else {
			activity.showInstallPgpDialog();
		}
	}

	public void showNoPGPKeyDialog(boolean plural,
								   DialogInterface.OnClickListener listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		if (plural) {
			builder.setTitle(getString(R.string.no_pgp_keys));
			builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
		} else {
			builder.setTitle(getString(R.string.no_pgp_key));
			builder.setMessage(getText(R.string.contact_has_no_pgp_key));
		}
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.send_unencrypted),
				listener);
		builder.create().show();
	}

	protected void sendOtrMessage(final Message message) {
		final ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		activity.selectPresence(message.getConversation(),
				new OnPresenceSelected() {

					@Override
					public void onPresenceSelected() {
						message.setCounterpart(conversation.getNextCounterpart());
						xmppService.sendMessage(message);
						messageSent();
					}
				});
	}

	public void appendText(String text) {
		if (text == null) {
			return;
		}
		String previous = this.mEditMessage.getText().toString();
		if (previous.length() != 0 && !previous.endsWith(" ")) {
			text = " " + text;
		}
		this.mEditMessage.append(text);
	}

	@Override
	public boolean onEnterPressed() {
		if (activity.enterIsSend()) {
			sendMessage();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void onTypingStarted() {
		Account.State status = conversation.getAccount().getStatus();
		if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
			activity.xmppConnectionService.sendChatState(conversation);
		}
		updateSendButton();
	}

	@Override
	public void onTypingStopped() {
		Account.State status = conversation.getAccount().getStatus();
		if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
			activity.xmppConnectionService.sendChatState(conversation);
		}
	}

	@Override
	public void onTextDeleted() {
		Account.State status = conversation.getAccount().getStatus();
		if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
			activity.xmppConnectionService.sendChatState(conversation);
		}
		updateSendButton();
	}

}
