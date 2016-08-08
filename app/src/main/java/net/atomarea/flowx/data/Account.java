package net.atomarea.flowx.data;

import java.io.Serializable;

/**
 * Created by Tom on 04.08.2016.
 */
public class Account implements Serializable {

    private String Name;
    private String Status;
    private String XmppAddress;
    private long LastOnline;

    public Account(String Name, String XmppAddress, String Status, String LastOnline) {
        this.Name = (Name == null ? XmppAddress.split("@")[0] : Name);
        this.XmppAddress = XmppAddress;
        this.Status = Status;
        try {
            this.LastOnline = Long.valueOf(LastOnline);
        } catch (Exception e) {
            this.LastOnline = 0;
        }
    }

    public void setName(String name) {
        Name = name;
    }

    public String getName() {
        return Name;
    }

    public String getXmppAddress() {
        return XmppAddress;
    }

    public String getStatus() {
        return Status;
    }

    public void setStatus(String status) {
        Status = status;
    }

    public long getLastOnline() {
        return LastOnline;
    }

    public void setLastOnline(long lastOnline) {
        LastOnline = lastOnline;
    }
}
