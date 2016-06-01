package net.atomarea.flowx.xml;

import android.util.Log;

import net.atomarea.flowx.Config;
import net.atomarea.flowx.utils.XmlHelper;
import net.atomarea.flowx.xmpp.jid.InvalidJidException;
import net.atomarea.flowx.xmpp.jid.Jid;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Element {
	private final String name;
	private Hashtable<String, String> attributes = new Hashtable<>();
	private String content;
	protected List<Element> children = new ArrayList<>();

	public Element(String name) {
		this.name = name;
	}

	public Element(String name, String xmlns) {
		this.name = name;
		this.setAttribute("xmlns", xmlns);
	}

	public Element addChild(Element child) {
		this.content = null;
		children.add(child);
		return child;
	}

	public Element addChild(String name) {
		this.content = null;
		Element child = new Element(name);
		children.add(child);
		return child;
	}

	public Element addChild(String name, String xmlns) {
		this.content = null;
		Element child = new Element(name);
		child.setAttribute("xmlns", xmlns);
		children.add(child);
		return child;
	}

	public Element setContent(String content) {
		this.content = content;
		this.children.clear();
		return this;
	}

	public Element findChild(String name) {
		for (Element child : this.children) {
			if (child.getName().equals(name)) {
				return child;
			}
		}
		return null;
	}

	public String findChildContent(String name) {
		Element element = findChild(name);
		return element == null ? null : element.getContent();
	}

	public Element findChild(String name, String xmlns) {
		for (Element child : this.children) {
			if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
				return child;
			}
		}
		return null;
	}

	public String findChildContent(String name, String xmlns) {
		Element element = findChild(name,xmlns);
		return element == null ? null : element.getContent();
	}

	public boolean hasChild(final String name) {
		return findChild(name) != null;
	}

	public boolean hasChild(final String name, final String xmlns) {
		return findChild(name, xmlns) != null;
	}

	public List<Element> getChildren() {
		return this.children;
	}

	public Element setChildren(List<Element> children) {
		this.children = children;
		return this;
	}

	public final String getContent() {
		return content;
	}

	public Element setAttribute(String name, String value) {
		if (name != null && value != null) {
			this.attributes.put(name, value);
		}
		return this;
	}

	public Element setAttributes(Hashtable<String, String> attributes) {
		this.attributes = attributes;
		return this;
	}

	public String getAttribute(String name) {
		if (this.attributes.containsKey(name)) {
			return this.attributes.get(name);
		} else {
			return null;
		}
	}

	public Jid getAttributeAsJid(String name) {
		final String jid = this.getAttribute(name);
		if (jid != null && !jid.isEmpty()) {
			try {
				return Jid.fromString(jid);
			} catch (final InvalidJidException e) {
				Log.e(Config.LOGTAG, "could not parse jid " + jid);
				return null;
			}
		}
		return null;
	}

	public Hashtable<String, String> getAttributes() {
		return this.attributes;
	}

	public String toString() {
		StringBuilder elementOutput = new StringBuilder();
		if ((content == null) && (children.size() == 0)) {
			Tag emptyTag = Tag.empty(name);
			emptyTag.setAtttributes(this.attributes);
			elementOutput.append(emptyTag.toString());
		} else {
			Tag startTag = Tag.start(name);
			startTag.setAtttributes(this.attributes);
			elementOutput.append(startTag);
			if (content != null) {
				elementOutput.append(XmlHelper.encodeEntities(content));
			} else {
				for (Element child : children) {
					elementOutput.append(child.toString());
				}
			}
			Tag endTag = Tag.end(name);
			elementOutput.append(endTag);
		}
		return elementOutput.toString();
	}

	public final String getName() {
		return name;
	}

	public void clearChildren() {
		this.children.clear();
	}

	public void setAttribute(String name, long value) {
		this.setAttribute(name, Long.toString(value));
	}

	public void setAttribute(String name, int value) {
		this.setAttribute(name, Integer.toString(value));
	}

	public boolean getAttributeAsBoolean(String name) {
		String attr = getAttribute(name);
		return (attr != null && (attr.equalsIgnoreCase("true") || attr.equalsIgnoreCase("1")));
	}

	public String getNamespace() {
		return getAttribute("xmlns");
	}
}
