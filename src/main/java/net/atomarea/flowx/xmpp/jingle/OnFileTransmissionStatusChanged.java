package net.atomarea.flowx.xmpp.jingle;

import net.atomarea.flowx.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	void onFileTransmitted(DownloadableFile file);

	void onFileTransferAborted();
}
