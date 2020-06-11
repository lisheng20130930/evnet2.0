package com.qp.evnet;

import com.qp.utils.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Map;

public abstract class Thttpd implements Observer, Connection.Handler,HttpReq.Delegate{
    protected final int MAX_CNN = 8000; /* max conn count */
    protected Map<SelectableChannel, Connection> clients = null;
    protected ServerSocketChannel acceptor = null;
    protected EventLoop loop = null;
    protected int port = 0;
    private int num = 0;

    public Thttpd(int thread, int timeout, int port){
        clients = new HashMap<SelectableChannel,Connection>();
        this.loop = new EventLoop(Math.max(1,thread),timeout);
        this.port = port;
    }

    private void onAccepted(SelectableChannel socket) throws Exception{
        Connection conn = clients.get(socket);
        if(conn != null){
            Logger.log("[THttpD] Error!!!!! socket has connection!");
            socket.close();
            return;
        }
        socket.configureBlocking(false);
        conn = new Connection(loop,socket,this);
        clients.put(socket,conn);
        num++;
        Logger.log("[THttpD] Conn("+conn.iID+") accepted, num="+num+" success");
    }

    public void reqReady(HttpReq req) {
        Logger.log("[THttpD] req("+req.iID+") parsed ready, szURL="+req.getURL()+",bodySize="+req.getBody().limit());
        Connection conn = req.getConn();
        conn.setUsr(null);
        handle(req);
    }

    public void processBuffer(Connection conn, ByteBuffer buffer) {
        Logger.log("[THttpD] DATA Received===>size="+buffer.limit());
        HttpReq req = (HttpReq)conn.getUsr();
        if(null == req){
            req = new HttpReq(conn,this);
            conn.setUsr(req);
        }
        int r = req.handle(buffer);
        if(r != buffer.limit()){
            conn.close(-1);
        }
    }

    public void onClosing(Connection conn, int code) {
        conn.clear();
        clients.remove(conn.socket);
        num--;
        Logger.log("[THttpD] Conn("+conn.iID+") on Closing, num="+num+", code="+code);
    }

    public void onSendComplete(Connection conn){
        Logger.log("[THttpD] Conn("+conn.iID+") onSendComplete");
    }

    public void handle(Object usr, int mask) {
        try {
            SelectableChannel client = acceptor.accept();
            if(num >= MAX_CNN){
                Logger.log("[THttpD] Error: conn out of balance, num="+num);
                client.close();
                return;
            }
            onAccepted(client);
        } catch (Exception e) {
            Logger.log("[THttpD] ===>"+e.getMessage());
        }
    }

    public void run() throws Exception{
        acceptor = ServerSocketChannel.open();
        acceptor.configureBlocking(false);
        InetSocketAddress address = new InetSocketAddress(port);
        acceptor.socket().bind(address);
        loop.eventAdd(acceptor, NIOEvent.AE_ACCEPT, this,acceptor);
        Logger.log("[THttpD] server started. port="+port+".....");
        while (true) {
            if(Signal.sig==Signal.SIG_INT){
                Logger.log("[THttpD] SIG_INT caught.......");
                break;
            }
            loop.processEvents();
        }
        loop.eventDel(acceptor,NIOEvent.AE_ACCEPT);
        loop.clear();
        Logger.log("[THttpD] server stopped....");
        System.exit(0);
    }

    public abstract void handle(HttpReq req);
}
