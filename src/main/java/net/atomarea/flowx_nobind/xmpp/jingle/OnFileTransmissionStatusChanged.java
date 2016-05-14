package net.atomarea.flowx_nobind.xmpp.jingle;

import net.atomarea.flowx_nobind.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	void onFileTransmitted(DownloadableFile file);

	void onFileTransferAborted();
}
