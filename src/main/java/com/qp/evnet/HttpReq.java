package com.qp.evnet;

import com.qp.utils.Logger;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class HttpReq{
    private HttpParser.ParserSettings settings = null;
    private HashMap<String,String> headers = null;
    private String filed = null;
    private Connection conn = null;
    private HttpParser parser = null;
    private Delegate delegate = null;
    private Object usr = null;
    private ByteBuffer body = null;
    private Upload upload = null;
    private String szURL = null;
    private static long iIDSeed = 0;
    public  String iID = null;
    public  long time = 0;

    public HttpReq(Connection conn, Delegate delegate){
        this.parser = new HttpParser(HttpParser.HTTP_REQUEST);
        this.headers = new HashMap<>();
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
            if(upload!=null){
                if(len!=upload.upload_bodyContinue(buffer, pos, len)){
                    return 0;
                }
            }else {
                if (onBodyData(buffer, pos, len)) {
                    return 0;
                }
            }
            return -1;
        };
        settings.on_url = (p, buffer, pos, len) -> {
            try {
                szURL = new String(buffer.array(), pos, len, "UTF-8");
                if(Upload.upload_checkURL(szURL)){
                    upload = new Upload();
                }
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
                headers.put(filed.toLowerCase(),new String(buffer.array(), pos, len));
                filed = null;
            }
            return 0;
        };
        settings.on_headers_complete = parser -> {
            if(upload!=null){
                if(!upload.upload_handleHeader(headers)){
                    Logger.log("[Req] upload_handleHeader error");
                    return -1;
                }
            }
            return 0;
        };
        return settings;
    }

    public boolean isFileUpload(){
        return upload!=null;
    }

    public String getUploadFileName(){
        if(isFileUpload()){
            return upload.getUploadFileName();
        }
        return null;
    }

    public boolean isUpgrade(){
        return parser.upgrade;
    }

    public boolean isUploadComplete(){
        if(upload!=null){
            return upload.bIsComplete();
        }
        return false;
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

    public String getMethod(){
        if(this.parser.method== HttpParser.HttpMethod.HTTP_POST){
            return "POST";
        }
        return "GET";
    }

    public Map<String, String> parameters() {
        Map<String, String> map = new HashMap<>();
        String str = null;
        if(isFileUpload() && isUploadComplete()){
            str = getUploadFileName();
            map.put("uploadPathName", str);
        }
        if ("GET".equals(getMethod())) {
            int index = getURL().indexOf('?');
            if (index != (-1)) {
                str = getURL().substring(index + 1);
            }
        } else {
            ByteBuffer b = getBody();
            str = new String(b.array(), b.position(), b.limit());
        }
        if (null == str || str.length() == 0) {
            return map;
        }
        String[] arrSplit = str.split("[&]");
        for (String strSplit : arrSplit) {
            String[] arrSplitEqual = null;
            arrSplitEqual = strSplit.split("[=]");
            if (arrSplitEqual.length > 1) {
                map.put(arrSplitEqual[0], URLDecoder.decode(arrSplitEqual[1]));
            } else {
                if (arrSplitEqual[0] != "") {
                    map.put(arrSplitEqual[0], "");
                }
            }
        }
        return map;
    }

    public String page(){
        String strPage=null;
        String strURL = getURL().trim();
        String[] arrSplit = strURL.split("[?]");
        if(strURL.length()>0){
            if(arrSplit.length>1){
                if(arrSplit[0]!=null){
                    strPage=arrSplit[0];
                }
            }
        }
        return (strPage==null)?strURL:strPage;
    }

    public ByteBuffer getBody(){
        return body;
    }

    public String getURL(){
        return szURL;
    }

    public void clear(){
        this.delegate = null;
        if(upload!=null){
            upload.clear();
        }
        this.conn = null;
        this.parser = null;
        this.settings = null;
        this.usr = null;
        this.body = null;
    }

    public String getIpAddr(){
        String[] HEADERS_TO_TRY = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR",
                "X-Real-IP"
        };
        for(String fieldName : HEADERS_TO_TRY) {
            String ip = getHeaders().get(fieldName.toLowerCase());
            if(isValid(ip)) {
                return getRealIp(ip);
            }
        }
        return this.getConn().getIp();
    }

    private String getRealIp(String ip) {
        if(ip.length() < 16) {
            return ip;
        }
        String[] ips = ip.split(",");
        for (String tempIp : ips) {
            if (!("unknown".equalsIgnoreCase(tempIp))) {
                return tempIp;
            }
        }
        return ip;
    }

    private boolean isValid(String ip) {
        if(ip == null) {
            return false;
        }
        if(ip.length() == 0) {
            return false;
        }
        if("unknown".equalsIgnoreCase(ip)) {
            return false;
        }
        return true;
    }

    public interface Delegate{
        void reqReady(HttpReq req);
    }
}
