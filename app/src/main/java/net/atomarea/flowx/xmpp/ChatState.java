package net.atomarea.flowx.xmpp;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;

import java.util.List;
import java.util.Map;

/**
 * Created by Tom on 08.08.2016.
 */
public class ChatState implements ExtensionElement {

    public static final String NAMESPACE = "urn:xmpp:fx:chatstate";
    public static final String ELEMENT = "chatstate";

    private String xmppAddress;
    private State state;

    public ChatState(String xmppAddress, State state) {
        this.xmppAddress = xmppAddress;
        this.state = state;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    public String getXmppAddress() {
        return xmppAddress;
    }

    public State getState() {
        return state;
    }

    @Override
    public CharSequence toXML() {
        return "<" + ELEMENT + " xmlns='" + NAMESPACE + "' xmppaddress='" + xmppAddress + "' state='" + state.name() + "' />";
    }

    public static class Provider extends EmbeddedExtensionProvider<ExtensionElement> {
        @Override
        protected ExtensionElement createReturnExtension(String currentElement, String currentNamespace, Map attributeMap, List content) {
            return new ChatState(String.valueOf(attributeMap.get("xmppaddress")), State.valueOf(String.valueOf(attributeMap.get("state"))));
        }
    }

    public enum State {
        Idle, Writing, RecordingAudio, TakingPicture, RecordingVideo
    }

}
