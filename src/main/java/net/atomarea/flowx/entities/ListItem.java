package net.atomarea.flowx.entities;

import java.util.List;

import net.atomarea.flowx.xmpp.jid.Jid;

public interface ListItem extends Comparable<ListItem> {
	String getDisplayName();

	String getDisplayJid();

	Jid getJid();

	List<Tag> getTags();

	final class Tag {
		private final String name;
		private final int color;

		public Tag(final String name, final int color) {
			this.name = name;
			this.color = color;
		}

		public int getColor() {
			return this.color;
		}

		public String getName() {
			return this.name;
		}
	}

	boolean match(final String needle);
}
