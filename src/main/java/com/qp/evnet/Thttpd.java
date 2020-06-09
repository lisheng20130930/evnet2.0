package com.qp.evnet;

import com.qp.utils.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Map;

public abstract class Thttpd implements Observer, Connection.Handler,HttpReq.Delegate{
    protected static final int N_THREAD = 2;
    protected Map<SelectableChannel, Connection> clients = null;
    protected ServerSocketChannel acceptor = null;
    protected EventLoop loop = null;
    private int counter = 0;
    protected int port = 0;

    public Thttpd(int timeout, int port){
        clients = new HashMap<SelectableChannel,Connection>();
        this.loop = new EventLoop(N_THREAD,timeout);
        this.port = port;
    }

    private void onAccepted(SelectableChannel socket) throws Exception{
        Connection conn = clients.get(socket);
        if(conn != null){
            Logger.log("Error!!!!! socket has connection!");
            socket.close();
            return;
        }
        socket.configureBlocking(false);
        conn = new Connection(loop,socket,this);
        clients.put(socket,conn);
        Logger.log("Conn("+conn.iID+") accepted success");
    }

    public void ReqReady(HttpReq req) {
        counter++;
        Logger.log("Request("+counter+") parsed Ready...szURL="+req.getURL());
        Connection conn = req.getConn();
        conn.setUsr(null);
        handle(req);
    }

    public void processBuffer(Connection conn, ByteBuffer buffer) {
        Logger.log("DATA Received===>size="+buffer.limit());
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
        Logger.log("Conn("+conn.iID+") on Closing.. code"+code);
        conn.clear();
        clients.remove(conn.socket);
    }

    public void onSendComplete(Connection conn){
        Logger.log("onSendComplete");
    }

    public void handle(Object usr, int mask) {
        try {
            SelectableChannel client = acceptor.accept();
            onAccepted(client);
        } catch (Exception e) {
            Logger.log(e.getMessage());
        }
    }

    public void run() throws Exception{
        acceptor = ServerSocketChannel.open();
        acceptor.configureBlocking(false);
        InetSocketAddress address = new InetSocketAddress(port);
        acceptor.socket().bind(address);
        loop.eventAdd(acceptor, NIOEvent.AE_ACCEPT, this,acceptor);
        Logger.log("server started. port="+port+".....");
        while (true) {
            if(Signal.sig==Signal.SIG_INT){
                Logger.log("SIG_INT caught.......");
                break;
            }
            loop.processEvents();
        }
        loop.eventDel(acceptor,NIOEvent.AE_ACCEPT);
        loop.clear();
        Logger.log("server stopped....");
        System.exit(0);
    }

    public abstract void handle(HttpReq req);
}
