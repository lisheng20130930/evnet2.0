package com.qp.evnet;

import com.qp.utils.Logger;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

public class Connection implements Observer{
    private static final int FLG_RECV_ENABLED =  (0x01);
    private static final int FLG_SEND_ENABLED =  (0x02);
    private final static int BUFFER_SIZE = 10240;
    public SelectableChannel socket = null;
    private static final int TIMEOUT = 10*1000;
    private EventLoop loop = null;
    private Handler handler = null;
    private List<ByteBuffer> sendQueue = null;
    private static long iIDSeed = 0;
    public long iID = 0;
    private Object usr = null;
    private int flag = 0;

    public Connection(EventLoop loop, SelectableChannel fd, Handler handler){
        this.handler = handler;
        this.loop = loop;
        this.socket = fd;
        loop.eventAdd(socket, NIOEvent.AE_READ, this,socket);
        this.flag |= FLG_RECV_ENABLED;
        sendQueue = new LinkedList<ByteBuffer>();
        iID = iIDSeed++;
        loop.setTimer(iID,TIMEOUT,null,this);
    }

    public void setUsr(Object usr){
        this.usr = usr;
    }

    public Object getUsr(){
        return this.usr;
    }

    public void onReadAble() {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int r = 0;
        try {
            r = ((SocketChannel) socket).read(buffer);
        }catch (Exception e){
            Logger.log(e.getMessage());
        }
        if (r <= 0) {
            close(-1);
            return;
        }
        buffer.flip();
        loop.setTimer(iID,TIMEOUT,null,this);
        handler.processBuffer(this,buffer);
        buffer.clear();
    }

    public void onWriteAble() {
        ByteBuffer buffer = sendQueue.get(0);
        int r = 0;
        try {
            r = ((SocketChannel) socket).write(buffer);
        }catch (Exception e){
            Logger.log(e.getMessage());
        }
        if(r<=0){
            close(-1);
            return;
        }
        if(buffer.position()==buffer.limit()){
            sendQueue.remove(0);
            buffer.clear();
        }
        Logger.log("[Conn]"+r+"bytes send complete....");
        this.handler.onSendComplete(this);
        loop.setTimer(iID,TIMEOUT,null,this);
        if(sendQueue.size()==0){
            loop.eventDel(socket, NIOEvent.AE_WRITE);
            this.flag &=~FLG_SEND_ENABLED;
        }
    }

    public void handle(Object usr, int mask) {
        if(usr!=null && mask !=0){
            if ((mask & NIOEvent.AE_READ) != 0) {
                onReadAble();
            }
            if ((mask & NIOEvent.AE_WRITE) != 0) {
                onWriteAble();
            }
        }else{
            Logger.log("Conn("+iID+") expired..");
            close(-1);
        }
    }

    public boolean sendBuffer(byte[] bytes){
        if(socket == null){
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        sendQueue.add(buffer);
        boolean r = true;
        if((this.flag & FLG_SEND_ENABLED)==0){
            r = loop.eventAdd(socket, NIOEvent.AE_WRITE, this,socket);
            if(r) {
                this.flag |= FLG_SEND_ENABLED;
            }
        }
        return r;
    }

    public void close(int code){
        if(socket!=null){
            loop.killTimer(iID);
            loop.eventDel(socket, NIOEvent.AE_READ| NIOEvent.AE_WRITE);
            this.flag = 0;
            handler.onClosing(this,code);
            try {
                socket.close();
            }catch (Exception e){
                Logger.log(e.getMessage());
            }
            socket = null;
        }
    }

    public void clear(){
        this.loop = null;
        this.handler = null;
        this.sendQueue = null;
        this.usr = null;
    }

    public interface Handler {
        void processBuffer(Connection conn, ByteBuffer buffer);
        void onSendComplete(Connection conn);
        void onClosing(Connection conn, int code);
    }
}
