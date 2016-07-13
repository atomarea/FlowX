package net.atomarea.flowx.crypto.sasl;

import net.atomarea.flowx.entities.Account;
import net.atomarea.flowx.xml.TagWriter;

import java.security.SecureRandom;

public abstract class SaslMechanism {

	final protected TagWriter tagWriter;
	final protected Account account;
	final protected SecureRandom rng;

	protected enum State {
		INITIAL,
		AUTH_TEXT_SENT,
		RESPONSE_SENT,
		VALID_SERVER_RESPONSE,
	}

	public static class AuthenticationException extends Exception {
		public AuthenticationException(final String message) {
			super(message);
		}

		public AuthenticationException(final Exception inner) {
			super(inner);
		}
	}

	public static class InvalidStateException extends AuthenticationException {
		public InvalidStateException(final String message) {
			super(message);
		}

		public InvalidStateException(final State state) {
			this("Invalid state: " + state.toString());
		}
	}

	public SaslMechanism(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
		this.tagWriter = tagWriter;
		this.account = account;
		this.rng = rng;
	}

	/**
	 * The priority is used to pin the authentication mechanism. If authentication fails, it MAY be retried with another
	 * mechanism of the same priority, but MUST NOT be tried with a mechanism of lower priority (to prevent downgrade
	 * attacks).
	 * @return An arbitrary int representing the priority
	 */
	public abstract int getPriority();

	public abstract String getMechanism();
	public String getClientFirstMessage() {
		return "";
	}
	public String getResponse(final String challenge) throws AuthenticationException {
		return "";
	}
}
