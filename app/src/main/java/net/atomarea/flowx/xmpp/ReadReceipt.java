package net.atomarea.flowx.xmpp;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;

import java.util.List;
import java.util.Map;

/**
 * Created by Tom on 07.08.2016.
 */
public class ReadReceipt implements ExtensionElement {

    public static final String NAMESPACE = "urn:xmpp:fx:read";
    public static final String ELEMENT = "read";

    private String id;

    public ReadReceipt(String id) {
        this.id = id;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    public String getID() {
        return id;
    }

    @Override
    public CharSequence toXML() {
        return "<" + ELEMENT + " xmlns='" + NAMESPACE + "' id='" + id + "' />";
    }

    public static class Provider extends EmbeddedExtensionProvider<ExtensionElement> {
        @Override
        protected ExtensionElement createReturnExtension(String currentElement, String currentNamespace, Map attributeMap, List content) {
            return new ReadReceipt(String.valueOf(attributeMap.get("id")));
        }
    }
}