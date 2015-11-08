package net.atomarea.flowx.xmpp;

import net.atomarea.flowx.crypto.axolotl.AxolotlService;

public interface OnKeyStatusUpdated {
	public void onKeyStatusUpdated(AxolotlService.FetchStatus report);
}
