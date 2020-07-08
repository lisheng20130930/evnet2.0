package com.qp.evnet;

import com.qp.utils.Logger;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Map;

public abstract class Thttpd implements Observer, Connection.Handler{
    protected final int MAX_CNN = 8000; /* max conn count */
    protected Map<SelectableChannel, Connection> clients = null;
    protected ServerSocketChannel acceptor = null;
    protected Observer listener = null;
    protected EventLoop loop = null;
    protected int timeout = 0xFFFF;
    protected int port = 0;
    protected int num = 0;

    public Thttpd(int thread, int timeout, int port){
        clients = new HashMap<SelectableChannel,Connection>();
        this.loop = new EventLoop(Math.max(1,thread),50000);
        this.timeout = Math.max(this.timeout,timeout);
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
        conn = new Connection(loop,socket,this, this.timeout);
        clients.put(socket,conn);
        num++;
        Logger.log("[THttpD] Conn("+conn.iID+") accepted, num="+num+" success");
    }

    private THandler newHttpReq(Connection conn){
        return new HttpReq(conn,new HttpReq.Delegate(){
            @Override
            public void reqReady(HttpReq req) {
                Logger.log("[THttpD] req("+req.iID+") parsed ready, szURL="+req.getURL()
                        +",bodySize="+req.getBody().limit());
                Logger.log("[THttpD] headers:"+req.getHeaders().toString());
                Connection conn = req.getConn();
                conn.setUsr(null);
                handle(req);
            }
        });
    }

    private boolean onWsFrameDefault(Connection conn, WsParser.WsFrame frame){
        boolean r = false;
        switch(frame.frameType){
            case WsParser.WsFrame.WS_PING_FRAME:
                {
                    WsParser.WsFrame rsp = new WsParser.WsFrame(WsParser.WsFrame.WS_PONG_FRAME, null);
                    r = conn.sendBuffer(rsp.toByteBuffer().array());
                }
                break;
            case WsParser.WsFrame.WS_CLOSING_FRAME:
                {
                    conn.close(0);
                    r = false;
                }
                break;
            default:
                break;
        }
        return r;
    }

    protected boolean upgradeWsChannel(HttpReq req){
        WsParser wsParser = new WsParser(req.getConn(), new WsParser.Delegate() {
            @Override
            public boolean onWsFrame(Connection conn, WsParser.WsFrame frame) {
                boolean r = false;
                if(frame.frameType== WsParser.WsFrame.WS_TEXT_FRAME) {
                    r = onWsMessage(conn,
                            new String(frame.payLoad.array(),frame.payLoad.position(),frame.payLoad.limit()));
                }else{
                    r = onWsFrameDefault(conn,frame);
                }
                return r;
            }
        });
        String rsp = wsParser.prepare(req.getHeaders());
        if(null==rsp){
            rsp = wsParser.upgrade();
        }
        if(null==rsp){
            wsParser.clear();
            return false;
        }
        req.getConn().setUsr(wsParser);
        Logger.log("[THttpD] upgradeWsChannel==>"+rsp);
        return req.getConn().sendBuffer(rsp.getBytes());
    }

    public void processBuffer(Connection conn, ByteBuffer buffer) {
        Logger.log("[THttpD] DATA Received===>size="+buffer.limit());
        THandler p = (THandler)conn.getUsr();
        if(null == p){
            p = newHttpReq(conn);
            conn.setUsr(p);
        }
        int r = p.handle(buffer);
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

    public int onSendComplete(Connection conn){
        Logger.log("[THttpD] Conn("+conn.iID+") onSendComplete");
        return 0;
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
            if(Signal.sig==Signal.SIG_EXIT){
                Logger.log("[THttpD] SIG_EXIT caught...");
                break;
            }else if(Signal.sig==Signal.SIG_HUP){
                Logger.log("[THttpD] SIG_HUP....");
                onHup();
                Signal.sig = 0;
            }
            loop.processEvents();
        }
        loop.eventDel(acceptor,NIOEvent.AE_ACCEPT);
        loop.clear();
        Logger.log("[THttpD] server stopped....");
        System.exit(0);
    }

    protected abstract void handle(HttpReq req);

    protected boolean onWsMessage(Connection conn, String message){
        return true;
    }

    protected void onHup(){
        return;
    }

    protected boolean sendWsMessage(Connection conn, String message){
        WsParser.WsFrame rsp = new WsParser.WsFrame(WsParser.WsFrame.WS_TEXT_FRAME, ByteBuffer.wrap(message.getBytes()));
        return conn.sendBuffer(rsp.toByteBuffer().array());
    }

    public interface THandler{
        int handle(ByteBuffer buffer);
    }
}
