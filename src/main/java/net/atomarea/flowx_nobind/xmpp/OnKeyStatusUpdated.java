package net.atomarea.flowx_nobind.xmpp;

import net.atomarea.flowx_nobind.crypto.axolotl.AxolotlService;

public interface OnKeyStatusUpdated {
	public void onKeyStatusUpdated(AxolotlService.FetchStatus report);
}
