package com.qp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qp.evnet.Connection;
import com.qp.evnet.EvActor;
import com.qp.evnet.HttpReq;
import com.qp.evnet.Thttpd;
import com.qp.utils.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class TestServer extends Thttpd {
    public TestServer(int timeout, int port) {
        super(timeout, port);
    }

    private void business(Map<String,Object> pRsp, JSONObject pReq){
        pRsp.put("code",200);
        pRsp.put("msg","test success");
        pRsp.put("echo",JSON.toJSON(pReq));
    }

    public void doBusiness(HttpReq req) {
        Map<String,Object> pRsp = (Map<String,Object>)req.getUsr();
        ByteBuffer buffer = req.getBody();
        String strReq = new String(buffer.array(),buffer.position(),buffer.limit());
        Logger.log("doBusiness called. strReq="+strReq);
        JSONObject pReq = null;
        try {
            pReq = JSON.parseObject(strReq);
        }catch (Exception e){
        }
        if(pReq==null){
            pRsp.put("code",700);
            pRsp.put("msg","bad params");
            return;
        }
        business(pRsp,pReq);
    }

    @Override
    public void onSendComplete(Connection conn){
        super.onSendComplete(conn);
        Logger.log("Response Completed handled");
    }

    public void onReqCompleted(HttpReq req) {
        String szRsp = JSON.toJSONString((Map)req.getUsr());
        String str = String.format("HTTP/1.1 200 OK\r\nContent-Length: %d\r\n\r\n%s",szRsp.length(),szRsp);
        boolean r = req.getConn().sendBuffer(str.getBytes());
        req.setUsr(null);
        if(r){
            Logger.log("Response send called...");
        }else{
            Logger.log("req send error");
        }
    }

    @Override
    public void handle(HttpReq req) {
        Map<String,Object> pRsp = new HashMap<String,Object>();
        req.setUsr(pRsp);
        loop.actorAdd(new EvActor.TCB() {
            @Override
            public void handle(Object usr, int mask) {
                onReqCompleted((HttpReq)usr);
            }
            @Override
            public void run(Object usr) {
                doBusiness((HttpReq)usr);
            }
        },req);
    }

    public static void main(String[] args){
        try {
            new TestServer(50000,58000).run();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
