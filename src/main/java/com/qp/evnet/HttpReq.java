package com.qp.evnet;

import com.qp.utils.Logger;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class HttpReq implements Thttpd.THandler {
    private HttpParser.ParserSettings settings = null;
    private HashMap<String,String> headers = null;
    private String filed = null;
    private Connection conn = null;
    private HttpParser parser = null;
    private Delegate delegate = null;
    private Object usr = null;
    private ByteBuffer body = null;
    private String szURL = null;
    private static long iIDSeed = 0;
    public  String iID = null;
    public  long time = 0;

    public HttpReq(Connection conn, Delegate delegate){
        this.parser = new HttpParser(HttpParser.HTTP_REQUEST);
        this.headers = new HashMap<String,String>();
        this.conn = conn;
        this.delegate = delegate;
        this.settings = assignSettings();
        this.body = ByteBuffer.allocate(128);
        this.iID = conn.iID+"-"+(iIDSeed++);
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
                Logger.log("[Req] ===>"+e.getMessage());
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
        settings.on_header_field = (p, buffer, pos, len) -> {
            if(filed != null){
                Logger.log("[Req] filed not NULL, you should check HERE");
            }
            filed = new String(buffer.array(), pos, len);
            return 0;
        };
        settings.on_header_value = (p, buffer, pos, len) -> {
            if(null!=filed){
                headers.put(filed,new String(buffer.array(), pos, len));
                filed = null;
            }
            return 0;
        };
        return settings;
    }

    public boolean isUpgrade(){
        return parser.upgrade;
    }

    @Override
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
        if(body.position()+len>body.limit()){
            ByteBuffer tmp = body;
            body = ByteBuffer.allocate(tmp.position()+len*2 + 1);
            tmp.flip();
            body.put(tmp.array(),0,tmp.limit());
        }
        body.put(buffer.array(),pos,len);
        return true;
    }

    public boolean onBodyComplete() {
        body.flip();
        delegate.reqReady(this);
        return true;
    }

    public HashMap<String,String> getHeaders(){
        return headers;
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
        void reqReady(HttpReq req);
    }
}
