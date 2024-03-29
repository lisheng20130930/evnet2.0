package com.qp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qp.evnet.*;
import com.qp.utils.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class TestServer extends Thttpd implements Thttpd.WsHandler {
    public static final int CONNECTION_TIMEOUT = 50000;
    public static final int PORT = 28000;

    public TestServer(EventLoop loop) {
        super(loop,CONNECTION_TIMEOUT,PORT);
        this.wsHandler = this;
        // ADD YOUR CODE HERE
        // eG. REG TO CENTER provider OR consumer
        // eG. SUB MODULE API-GATEWAY FOR EXAMPLE
    }

    private void business(Map<String,Object> rsp, JSONObject pReq){
        rsp.put("code",200);
        rsp.put("msg","test success");
        rsp.put("echo",JSON.toJSON(pReq));
    }

    public Map<String,Object> doBusiness(HttpReq req) {
        Map<String,Object> rsp = new HashMap<String,Object>();
        ByteBuffer buffer = req.getBody();
        String strReq = new String(buffer.array(),buffer.position(),buffer.limit());
        Logger.log("[TestServer] doBusiness called. strReq="+strReq);
        JSONObject pReq = null;
        try {
            pReq = JSON.parseObject(strReq);
        }catch (Exception e){
            Logger.log("[TestServer] ==>"+e.getMessage());
        }
        if(pReq==null){
            rsp.put("code",700);
            rsp.put("msg","bad params");
            return rsp;
        }
        business(rsp,pReq);
        return rsp;
    }

    public void onReqCompleted(HttpReq req) {
        String rsp = JSON.toJSONString((Map)req.getUsr());
        String str = String.format("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n%s",rsp.getBytes().length,rsp);
        boolean r = req.getConn().sendBuffer(str.getBytes());
        req.setUsr(null);
        req.clear();
        long used = System.currentTimeMillis()-req.time;
        if(r){
            Logger.log("[TestServer] req("+req.iID+") success,used-time="+used);
        }else{
            Logger.log("[TestServer] req("+req.iID+") error,used-time="+used);
        }
    }

    @Override
    protected void handle(HttpReq req) {
        req.time = System.currentTimeMillis();
        if(req.isUpgrade()){
            boolean r = upgradeWsChannel(req);
            req.clear();
            if(!r){
                req.getConn().close(0);
            }
            long used = System.currentTimeMillis()-req.time;
            Logger.log("[TestServer] req ("+req.iID+") "
                    +"upgrade "+(r?"success":"error")+",used-time="+used);
            Logger.log("[TestServer] WebSocket established");
            return;
        }
        loop.actorAdd(new EvActor.TCB() {
            @Override
            public void handle(Object usr, int mask) {
                onReqCompleted((HttpReq)usr);
            }
            @Override
            public boolean run(Object usr) {
                Map r = doBusiness((HttpReq)usr);
                req.setUsr(r);
                return true;
            }
        },req);
    }

    @Override
    public void onClosing(Connection conn, int code) {
        Object p = conn.getUsr();
        if (p != null && p instanceof WsParser) {
            ((WsParser)p).clear();
            conn.setUsr(null);
            Logger.log("[TestServer] WebSocket closed");
        }
        super.onClosing(conn,code);
    }

    @Override
    public boolean onWsMessage(Connection conn, String message){
        Logger.log("[TestServer] WebSocket onWsMessage==>"+message);
        sendWsMessage(conn,"{\"janus\":\"ack\"}");
        return true;
    }

    public static void main(String[] args){
        EventLoop loop = new EventLoop(8,50000);
        TestServer web = new TestServer(loop);
        if(web.start()){
            while (true) {
                if(loop.sig_exit()){
                    Logger.log("SIG_EXIT caught...");
                    break;
                }
                loop.processEvents();
            }
            web.stop();
        }
        System.exit(0);
    }
}
