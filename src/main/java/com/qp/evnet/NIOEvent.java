package com.qp.evnet;

import java.nio.channels.SelectableChannel;

public class NIOEvent {
    public static final int AE_READ     = 0x01;
    public static final int AE_WRITE    = 0x02;
    public static final int AE_ACCEPT   = 0x04;

    public Observer acceptHandler;
    public Observer readHandler;
    public Observer writeHandler;
    public int mask;
    private Object usr;
    SelectableChannel socket;

    public NIOEvent(SelectableChannel socket, Object usr){
        this.socket = socket;
        this.usr = usr;
    }

    public Object getUsr() {
        return usr;
    }
}
