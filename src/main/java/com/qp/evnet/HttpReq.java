package com.qp.evnet;

import java.nio.ByteBuffer;

public class HttpReq{
    private Connection conn = null;
    private HttpParser parser = null;
    private Delegate delegate = null;
    private Object usr = null;
    private HttpParser.ParserSettings settings = null;
    private ByteBuffer body = null;
    private String szURL = null;
    public  long time = 0;

    public HttpReq(Connection conn, Delegate delegate){
        this.parser = new HttpParser(HttpParser.HTTP_REQUEST);
        this.conn = conn;
        this.delegate = delegate;
        this.settings = assignSettings();
        this.body = ByteBuffer.allocate(128);
    }

    private HttpParser.ParserSettings assignSettings(){
        HttpParser.ParserSettings settings = new HttpParser.ParserSettings();
        settings.on_message_complete = parser -> {
            if(onBodyComplete()){
                return 0;
            }
            return -1;
        };
        settings.on_body = (p, buffer, pos, len) -> {
            if(onBodyData(buffer,pos,len)){
                return 0;
            }
            return -1;
        };
        settings.on_url = (p, buffer, pos, len) -> {
            try {
                szURL = new String(buffer.array(), pos, len, "UTF-8");
            }catch (Exception e){
                e.printStackTrace();
                return -1;
            }
            return 0;
        };
        settings.on_message_begin = parser -> {
            if(parser.method == HttpParser.HttpMethod.HTTP_POST
                || parser.method == HttpParser.HttpMethod.HTTP_GET){
                return 0;
            }
            return -1;
        };
        return settings;
    }

    public int handle(ByteBuffer buffer){
        int used = parser.execute(settings,buffer);
        if(used != buffer.limit()){
            return 0;
        }
        return used;
    }

    public Connection getConn(){
        return conn;
    }

    public void setUsr(Object usr){
        this.usr = usr;
    }

    public Object getUsr(){
        return usr;
    }

    public boolean onBodyData(ByteBuffer buffer, int pos, int len){
        if(body.limit()+len>body.capacity()){
            ByteBuffer tmp = body;
            body = ByteBuffer.allocate(tmp.capacity()+len*2 + 1);
            body.put(tmp.array());
        }
        body.put(buffer.array(),pos,len);
        return true;
    }

    public boolean onBodyComplete() {
        body.flip();
        delegate.ReqReady(this);
        return true;
    }

    public ByteBuffer getBody(){
        return body;
    }

    public String getURL(){
        return szURL;
    }

    public void clear(){
        this.delegate = null;
        this.conn = null;
        this.parser = null;
        this.settings = null;
        this.usr = null;
        this.body = null;
    }

    public interface Delegate{
        void ReqReady(HttpReq req);
    }
}
