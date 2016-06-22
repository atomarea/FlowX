package net.atomarea.flowx.utils;

public class XmlHelper {
	public static String encodeEntities(String content) {
		content = content.replace("&", "&amp;");
		content = content.replace("<", "&lt;");
		content = content.replace(">", "&gt;");
		content = content.replace("\"", "&quot;");
		content = content.replace("'", "&apos;");
		content = content.replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "");
		return content;
	}
}
