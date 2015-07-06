package eu.siacs.conversations.http;

import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;

public class HttpConnection implements Downloadable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private URL mUrl;
	private Message message;
	private DownloadableFile file;
	private int mStatus = Downloadable.STATUS_UNKNOWN;
	private boolean acceptedAutomatically = false;
	private int mProgress = 0;
	private long mLastGuiRefresh = 0;

	public HttpConnection(HttpConnectionManager manager) {
		this.mHttpConnectionManager = manager;
		this.mXmppConnectionService = manager.getXmppConnectionService();
	}

	@Override
	public boolean start() {
		if (mXmppConnectionService.hasInternetConnection()) {
			if (this.mStatus == STATUS_OFFER_CHECK_FILESIZE) {
				checkFileSize(true);
			} else {
				new Thread(new FileDownloader(true)).start();
			}
			return true;
		} else {
			return false;
		}
	}

	public void init(Message message) {
		this.message = message;
		this.message.setDownloadable(this);
		try {
			mUrl = new URL(message.getBody());
			String[] parts = mUrl.getPath().toLowerCase().split("\\.");
			String lastPart = parts.length >= 1 ? parts[parts.length - 1] : null;
			String secondToLast = parts.length >= 2 ? parts[parts.length -2] : null;
			if ("pgp".equals(lastPart) || "gpg".equals(lastPart)) {
				this.message.setEncryption(Message.ENCRYPTION_PGP);
			} else if (message.getEncryption() != Message.ENCRYPTION_OTR) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
			}
			String extension;
			if (Arrays.asList(VALID_CRYPTO_EXTENSIONS).contains(lastPart)) {
				extension = secondToLast;
			} else {
				extension = lastPart;
			}
			message.setRelativeFilePath(message.getUuid()+"."+extension);
			this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
			String reference = mUrl.getRef();
			if (reference != null && reference.length() == 96) {
				this.file.setKey(CryptoHelper.hexToBytes(reference));
			}

			if (this.message.getEncryption() == Message.ENCRYPTION_OTR
					&& this.file.getKey() == null) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
					}
			checkFileSize(false);
		} catch (MalformedURLException e) {
			this.cancel();
		}
	}

	private void checkFileSize(boolean interactive) {
		new Thread(new FileSizeChecker(interactive)).start();
	}

	public void cancel() {
		mHttpConnectionManager.finishConnection(this);
		message.setDownloadable(null);
		mXmppConnectionService.updateConversationUi();
	}

	private void finish() {
		Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		intent.setData(Uri.fromFile(file));
		mXmppConnectionService.sendBroadcast(intent);
		message.setDownloadable(null);
		mHttpConnectionManager.finishConnection(this);
		mXmppConnectionService.updateConversationUi();
		if (acceptedAutomatically) {
			mXmppConnectionService.getNotificationService().push(message);
		}
	}

	private void changeStatus(int status) {
		this.mStatus = status;
		mXmppConnectionService.updateConversationUi();
	}

	private void setupTrustManager(final HttpsURLConnection connection,
			final boolean interactive) {
		final X509TrustManager trustManager;
		final HostnameVerifier hostnameVerifier;
		if (interactive) {
			trustManager = mXmppConnectionService.getMemorizingTrustManager();
			hostnameVerifier = mXmppConnectionService
				.getMemorizingTrustManager().wrapHostnameVerifier(
						new StrictHostnameVerifier());
		} else {
			trustManager = mXmppConnectionService.getMemorizingTrustManager()
				.getNonInteractive();
			hostnameVerifier = mXmppConnectionService
				.getMemorizingTrustManager()
				.wrapHostnameVerifierNonInteractive(
						new StrictHostnameVerifier());
		}
		try {
			final SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new X509TrustManager[]{trustManager},
					mXmppConnectionService.getRNG());

			final SSLSocketFactory sf = sc.getSocketFactory();
			final String[] cipherSuites = CryptoHelper.getOrderedCipherSuites(
					sf.getSupportedCipherSuites());
			if (cipherSuites.length > 0) {
				sc.getDefaultSSLParameters().setCipherSuites(cipherSuites);

			}

			connection.setSSLSocketFactory(sf);
			connection.setHostnameVerifier(hostnameVerifier);
		} catch (final KeyManagementException | NoSuchAlgorithmException ignored) {
		}
	}

	private class FileSizeChecker implements Runnable {

		private boolean interactive = false;

		public FileSizeChecker(boolean interactive) {
			this.interactive = interactive;
		}

		@Override
		public void run() {
			long size;
			try {
				size = retrieveFileSize();
			} catch (SSLHandshakeException e) {
				changeStatus(STATUS_OFFER_CHECK_FILESIZE);
				HttpConnection.this.acceptedAutomatically = false;
				HttpConnection.this.mXmppConnectionService.getNotificationService().push(message);
				return;
			} catch (IOException e) {
				cancel();
				return;
			}
			file.setExpectedSize(size);
			if (size <= mHttpConnectionManager.getAutoAcceptFileSize()) {
				HttpConnection.this.acceptedAutomatically = true;
				new Thread(new FileDownloader(interactive)).start();
			} else {
				changeStatus(STATUS_OFFER);
				HttpConnection.this.acceptedAutomatically = false;
				HttpConnection.this.mXmppConnectionService.getNotificationService().push(message);
			}
		}

		private long retrieveFileSize() throws IOException,
						SSLHandshakeException {
							changeStatus(STATUS_CHECKING);
							HttpURLConnection connection = (HttpURLConnection) mUrl
								.openConnection();
							connection.setRequestMethod("HEAD");
							if (connection instanceof HttpsURLConnection) {
								setupTrustManager((HttpsURLConnection) connection, interactive);
							}
							connection.connect();
							String contentLength = connection.getHeaderField("Content-Length");
							if (contentLength == null) {
								throw new IOException();
							}
							try {
								return Long.parseLong(contentLength, 10);
							} catch (NumberFormatException e) {
								throw new IOException();
							}
		}

	}

	private class FileDownloader implements Runnable {

		private boolean interactive = false;

		public FileDownloader(boolean interactive) {
			this.interactive = interactive;
		}

		@Override
		public void run() {
			try {
				changeStatus(STATUS_DOWNLOADING);
				download();
				updateImageBounds();
				finish();
			} catch (SSLHandshakeException e) {
				changeStatus(STATUS_OFFER);
			} catch (IOException e) {
				cancel();
			}
		}

		private void download() throws SSLHandshakeException, IOException {
			HttpURLConnection connection = (HttpURLConnection) mUrl
				.openConnection();
			if (connection instanceof HttpsURLConnection) {
				setupTrustManager((HttpsURLConnection) connection, interactive);
			}
			connection.connect();
			BufferedInputStream is = new BufferedInputStream(
					connection.getInputStream());
			file.getParentFile().mkdirs();
			file.createNewFile();
			OutputStream os = file.createOutputStream();
			if (os == null) {
				throw new IOException();
			}
			long transmitted = 0;
			long expected = file.getExpectedSize();
			int count = -1;
			byte[] buffer = new byte[1024];
			while ((count = is.read(buffer)) != -1) {
				transmitted += count;
				os.write(buffer, 0, count);
				updateProgress((int) ((((double) transmitted) / expected) * 100));
			}
			os.flush();
			os.close();
			is.close();
		}

		private void updateImageBounds() {
			message.setType(Message.TYPE_FILE);
			mXmppConnectionService.getFileBackend().updateFileParams(message, mUrl);
			mXmppConnectionService.updateMessage(message);
		}

	}

	public void updateProgress(int i) {
		this.mProgress = i;
		if (SystemClock.elapsedRealtime() - this.mLastGuiRefresh > Config.PROGRESS_UI_UPDATE_INTERVAL) {
			this.mLastGuiRefresh = SystemClock.elapsedRealtime();
			mXmppConnectionService.updateConversationUi();
		}
	}

	@Override
	public int getStatus() {
		return this.mStatus;
	}

	@Override
	public long getFileSize() {
		if (this.file != null) {
			return this.file.getExpectedSize();
		} else {
			return 0;
		}
	}

	@Override
	public int getProgress() {
		return this.mProgress;
	}
}
