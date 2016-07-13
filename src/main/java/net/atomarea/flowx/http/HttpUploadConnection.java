package net.atomarea.flowx.http;

import android.app.PendingIntent;
import android.os.PowerManager;
import android.util.Log;
import android.util.Pair;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.entities.DownloadableFile;
import net.atomarea.flowx.entities.Message;
import net.atomarea.flowx.entities.Transferable;
import net.atomarea.flowx.persistance.FileBackend;
import net.atomarea.flowx.services.AbstractConnectionManager;
import net.atomarea.flowx.services.XmppConnectionService;
import net.atomarea.flowx.ui.UiCallback;
import net.atomarea.flowx.utils.CryptoHelper;
import net.atomarea.flowx.utils.Xmlns;
import net.atomarea.flowx.xml.Element;
import net.atomarea.flowx.xmpp.OnIqPacketReceived;
import net.atomarea.flowx.xmpp.jid.Jid;
import net.atomarea.flowx.xmpp.stanzas.IqPacket;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class HttpUploadConnection implements Transferable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private boolean canceled = false;
	private boolean delayed = false;
	private Account account;
	private DownloadableFile file;
	private Message message;
	private String mime;
	private URL mGetUrl;
	private URL mPutUrl;
	private boolean mUseTor = false;

	private byte[] key = null;

	private long transmitted = 0;

	private InputStream mFileInputStream;

	public HttpUploadConnection(HttpConnectionManager httpConnectionManager) {
		this.mHttpConnectionManager = httpConnectionManager;
		this.mXmppConnectionService = httpConnectionManager.getXmppConnectionService();
		this.mUseTor = mXmppConnectionService.useTorToConnect();
	}

	@Override
	public boolean start() {
		return false;
	}

	@Override
	public int getStatus() {
		return STATUS_UPLOADING;
	}

	@Override
	public long getFileSize() {
		return file == null ? 0 : file.getExpectedSize();
	}

	@Override
	public int getProgress() {
		if (file == null) {
			return 0;
		}
		return (int) ((((double) transmitted) / file.getExpectedSize()) * 100);
	}

	@Override
	public void cancel() {
		this.canceled = true;
	}

	private void fail() {
		mHttpConnectionManager.finishUploadConnection(this);
		message.setTransferable(null);
		mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
		FileBackend.close(mFileInputStream);
	}

	public void init(Message message, boolean delay) {
		this.message = message;
		this.account = message.getConversation().getAccount();
		this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
		this.mime = this.file.getMimeType();
		this.delayed = delay;
		if (Config.ENCRYPT_ON_HTTP_UPLOADED
				|| message.getEncryption() == Message.ENCRYPTION_AXOLOTL
				|| message.getEncryption() == Message.ENCRYPTION_OTR) {
			this.key = new byte[48];
			mXmppConnectionService.getRNG().nextBytes(this.key);
			this.file.setKeyAndIv(this.key);
		}
		Pair<InputStream,Integer> pair;
		try {
			pair = AbstractConnectionManager.createInputStream(file, true);
		} catch (FileNotFoundException e) {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": could not find file to upload - "+e.getMessage());
			fail();
			return;
		}
		this.file.setExpectedSize(pair.second);
		this.mFileInputStream = pair.first;
		Jid host = account.getXmppConnection().findDiscoItemByFeature(Xmlns.HTTP_UPLOAD);
		IqPacket request = mXmppConnectionService.getIqGenerator().requestHttpUploadSlot(host,file,mime);
		mXmppConnectionService.sendIqPacket(account, request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element slot = packet.findChild("slot",Xmlns.HTTP_UPLOAD);
					if (slot != null) {
						try {
							mGetUrl = new URL(slot.findChildContent("get"));
							mPutUrl = new URL(slot.findChildContent("put"));
							if (!canceled) {
								new Thread(new FileUploader()).start();
							}
							return;
						} catch (MalformedURLException e) {
							//fall through
						}
					}
				}
				Log.d(Config.LOGTAG,account.getJid().toString()+": invalid response to slot request "+packet);
				fail();
			}
		});
		message.setTransferable(this);
		mXmppConnectionService.markMessage(message, Message.STATUS_UNSEND);
	}

	private class FileUploader implements Runnable {

		@Override
		public void run() {
			this.upload();
		}

		private void upload() {
			OutputStream os = null;
			HttpURLConnection connection = null;
			PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_upload_"+message.getUuid());
			try {
				wakeLock.acquire();
				Log.d(Config.LOGTAG, "uploading to " + mPutUrl.toString());
				if (mUseTor) {
					connection = (HttpURLConnection) mPutUrl.openConnection(mHttpConnectionManager.getProxy());
				} else {
					connection = (HttpURLConnection) mPutUrl.openConnection();
				}
				if (connection instanceof HttpsURLConnection) {
					mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, true);
				}
				connection.setRequestMethod("PUT");
				connection.setFixedLengthStreamingMode((int) file.getExpectedSize());
				connection.setRequestProperty("Content-Type", mime == null ? "application/octet-stream" : mime);
				connection.setRequestProperty("User-Agent",mXmppConnectionService.getIqGenerator().getIdentityName());
				connection.setDoOutput(true);
				connection.connect();
				os = connection.getOutputStream();
				transmitted = 0;
				int count = -1;
				byte[] buffer = new byte[4096];
				while (((count = mFileInputStream.read(buffer)) != -1) && !canceled) {
					transmitted += count;
					os.write(buffer, 0, count);
					mXmppConnectionService.updateConversationUi();
				}
				os.flush();
				os.close();
				mFileInputStream.close();
				int code = connection.getResponseCode();
				if (code == 200 || code == 201) {
					Log.d(Config.LOGTAG, "finished uploading file");
					if (key != null) {
						mGetUrl = new URL(mGetUrl.toString() + "#" + CryptoHelper.bytesToHex(key));
					}
					mXmppConnectionService.getFileBackend().updateFileParams(message, mGetUrl);
					mXmppConnectionService.getFileBackend().updateMediaScanner(file);
					message.setTransferable(null);
					message.setCounterpart(message.getConversation().getJid().toBareJid());
					if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
						mXmppConnectionService.getPgpEngine().encrypt(message, new UiCallback<Message>() {
							@Override
							public void success(Message message) {
								mXmppConnectionService.resendMessage(message,delayed);
							}

							@Override
							public void error(int errorCode, Message object) {
								Log.d(Config.LOGTAG,"pgp encryption failed");
								fail();
							}

							@Override
							public void userInputRequried(PendingIntent pi, Message object) {
								fail();
							}
						});
					} else {
						mXmppConnectionService.resendMessage(message, delayed);
					}
				} else {
					Log.d(Config.LOGTAG,"http upload failed because response code was "+code);
					fail();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Log.d(Config.LOGTAG,"http upload failed "+e.getMessage());
				fail();
			} finally {
				FileBackend.close(mFileInputStream);
				FileBackend.close(os);
				if (connection != null) {
					connection.disconnect();
				}
				wakeLock.release();
			}
		}
	}
}
