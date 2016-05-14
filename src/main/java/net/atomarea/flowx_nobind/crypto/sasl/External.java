package net.atomarea.flowx_nobind.crypto.sasl;

import android.util.Base64;
import java.security.SecureRandom;

import net.atomarea.flowx_nobind.entities.Account;
import net.atomarea.flowx_nobind.xml.TagWriter;

public class External extends SaslMechanism {

	public External(TagWriter tagWriter, Account account, SecureRandom rng) {
		super(tagWriter, account, rng);
	}

	@Override
	public int getPriority() {
		return 25;
	}

	@Override
	public String getMechanism() {
		return "EXTERNAL";
	}

	@Override
	public String getClientFirstMessage() {
		return Base64.encodeToString(account.getJid().toBareJid().toString().getBytes(),Base64.NO_WRAP);
	}
}
