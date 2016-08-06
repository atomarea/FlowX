package net.atomarea.flowx.data;

import java.io.Serializable;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatMessage implements Serializable {

    private String ID;
    private String Data;
    private Object Heap;
    private Type Type;
    private State state;
    private boolean Sent;
    private long Time;

    public ChatMessage(String ID, String Data, Type Type, boolean Sent, long Time) {
        this.ID = ID;
        this.Data = Data;
        this.Type = Type;
        this.Sent = Sent;
        this.Time = Time;
        state = State.NotDelivered;
        Heap = null;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getData() {
        return Data;
    }

    public void setData(String data) {
        Data = data;
    }

    public Object getHeap() {
        return Heap;
    }

    public void setHeap(Object heap) {
        Heap = heap;
    }

    public ChatMessage.Type getType() {
        return Type;
    }

    public void setType(ChatMessage.Type type) {
        Type = type;
    }

    public boolean isSent() {
        return Sent;
    }

    public long getTime() {
        return Time;
    }

    public ChatMessage.State getState() {
        return state;
    }

    public void setState(ChatMessage.State state) {
        this.state = state;
    }

    public enum Type {
        Text, Audio, Image, Video, File, Unknown
    }

    public enum State {
        NotDelivered, DeliveredToServer, DeliveredToContact, ReadByContact
    }

}
