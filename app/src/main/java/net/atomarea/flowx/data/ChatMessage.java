package net.atomarea.flowx.data;

import java.io.Serializable;

/**
 * Created by Tom on 04.08.2016.
 */
public class ChatMessage implements Serializable {

    private String Data;
    private Object Heap;
    private Type Type;
    private boolean Sent;
    private long Time;

    public ChatMessage(String Data, Type Type, boolean Sent, long Time) {
        this.Data = Data;
        this.Type = Type;
        this.Sent = Sent;
        this.Time = Time;
        Heap = null;
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

    public enum Type {
        Text, Audio, Image, Video, File, Unknown
    }

}
