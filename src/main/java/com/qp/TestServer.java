package com.qp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qp.evnet.*;
import com.qp.utils.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class TestServer extends Thttpd {
    public TestServer(int thread, int timeout, int port) {
        super(thread, timeout, port);
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
        THandler p = (THandler) conn.getUsr();
        if (p != null && p instanceof WsParser) {
            ((WsParser)p).clear();
            conn.setUsr(null);
            Logger.log("[TestServer] WebSocket closed");
        }
        super.onClosing(conn,code);
    }

    @Override
    protected boolean onWsMessage(Connection conn, String message){
        Logger.log("[TestServer] WebSocket onWsMessage==>"+message);
        sendWsMessage(conn,"{\"janus\":\"ack\"}");
        return super.onWsMessage(conn,message);
    }

    public static void main(String[] args){
        try {
            new TestServer(4, 45000,8188).run();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
