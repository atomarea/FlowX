package net.atomarea.flowx.crypto.sasl;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.xml.TagWriter;

import java.security.SecureRandom;

public class Anonymous extends SaslMechanism {

	public Anonymous(TagWriter tagWriter, Account account, SecureRandom rng) {
		super(tagWriter, account, rng);
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@Override
	public String getMechanism() {
		return "ANONYMOUS";
	}

	@Override
	public String getClientFirstMessage() {
		return "";
	}
}
