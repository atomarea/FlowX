package net.atomarea.flowx.entities;

import java.lang.Comparable;
import java.util.Locale;

import net.atomarea.flowx.xml.Element;

public class Presence implements Comparable {

	public enum Status {
		CHAT, ONLINE, AWAY, XA, DND, OFFLINE;

		public String toShowString() {
			switch(this) {
				case CHAT: return "chat";
				case AWAY: return "away";
				case XA:   return "xa";
				case DND:  return "dnd";
			}
			return null;
		}

		public static Status fromShowString(String show) {
			if (show == null) {
				return ONLINE;
			} else {
				switch (show.toLowerCase(Locale.US)) {
					case "away":
						return AWAY;
					case "xa":
						return XA;
					case "dnd":
						return DND;
					case "chat":
						return CHAT;
					default:
						return ONLINE;
				}
			}
		}
	}

	private final Status status;
	private ServiceDiscoveryResult disco;
	private final String ver;
	private final String hash;
	private final String message;

	private Presence(Status status, String ver, String hash, String message) {
		this.status = status;
		this.ver = ver;
		this.hash = hash;
		this.message = message;
	}

	public static Presence parse(String show, Element caps, String message) {
		final String hash = caps == null ? null : caps.getAttribute("hash");
		final String ver = caps == null ? null : caps.getAttribute("ver");
		return new Presence(Status.fromShowString(show), ver, hash, message);
	}

	public int compareTo(Object other) {
		return this.status.compareTo(((Presence)other).status);
	}

	public Status getStatus() {
		return this.status;
	}

	public boolean hasCaps() {
		return ver != null && hash != null;
	}

	public String getVer() {
		return this.ver;
	}

	public String getHash() {
		return this.hash;
	}

	public String getMessage() {
		return this.message;
	}

	public void setServiceDiscoveryResult(ServiceDiscoveryResult disco) {
		this.disco = disco;
	}

	public ServiceDiscoveryResult getServiceDiscoveryResult() {
		return disco;
	}
}
