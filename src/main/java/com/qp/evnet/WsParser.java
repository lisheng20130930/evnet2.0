package com.qp.evnet;

import com.qp.utils.Logger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class WsParser implements Thttpd.THandler {
    private static final int BUFFER_SIZE = 256;
    private ByteBuffer gBuffer = null;
    private Delegate delegate = null;
    private Connection conn = null;
    private String protocol = null;
    private String version = null;
    private String host = null;
    private String key = null;

    public WsParser(Connection conn, Delegate delegate){
        this.gBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.delegate = delegate;
        this.conn = conn;
    }

    private Map<String,Object> getPayloadLength(byte[] inputFrame, int inputLength, int _frameType){
        int payloadFieldExtraBytes = 0;
        int frameType = _frameType;
        int payloadLength = 0;

        payloadLength = inputFrame[1] & 0x7F;
        payloadFieldExtraBytes = 0;
        if((payloadLength == 0x7E && inputLength < 4) || (payloadLength == 0x7F && inputLength < 10)) {
            frameType = 0xFF;
        }
        if(payloadLength == 0x7F && (inputFrame[3] & 0x80) != 0x0) {
            return null;
        }
        if (payloadLength == 0x7E) {
            payloadFieldExtraBytes = 2;
            payloadLength = inputFrame[2]<<16|inputFrame[2+1];
        }else if (payloadLength == 0x7F) {
            return null;
        }
        Map<String,Object> r = new HashMap<>();
        r.put("payloadFieldExtraBytes",payloadFieldExtraBytes);
        r.put("frameType",frameType);
        r.put("payloadLength",payloadLength);
        return r;
    }

    private Map<String,Object> readWsFrame(ByteBuffer buffer){
        Map<String,Object> map = new HashMap<>();
        if(buffer.position()<2){
            return map;
        }
        byte[] inputFrame = buffer.array();
        if ((inputFrame[0] & 0x70) != 0x0
            ||(inputFrame[0] & 0x80) != 0x80
            ||(inputFrame[1] & 0x80) != 0x80) {
            return null;
        }
        byte opcode = (byte)(inputFrame[0] & 0x0F);
        if (opcode == WsFrame.WS_TEXT_FRAME
                ||opcode == WsFrame.WS_BINARY_FRAME
                ||opcode == WsFrame.WS_CLOSING_FRAME
                ||opcode == WsFrame.WS_PING_FRAME
                ||opcode == WsFrame.WS_PONG_FRAME){
            Map<String,Object> r = getPayloadLength(inputFrame,buffer.position(),opcode);
            int payloadFieldExtraBytes = (Integer)r.get("payloadFieldExtraBytes");
            int payloadLength = (Integer)r.get("payloadLength");
            int frameType = (Integer)r.get("frameType");
            if(frameType == 0xFF){
                return map;
            }
            ByteBuffer payLoad = null;
            if (payloadLength > 0) {
                if (payloadLength + 6 + payloadFieldExtraBytes > buffer.position()){
                    return map;
                }
                int maskingKey = 2 + payloadFieldExtraBytes;
                int payLoadPos = 2 + payloadFieldExtraBytes + 4;
                for (int i = 0; i < payloadLength; i++) {
                    inputFrame[payLoadPos+i] = (byte)(((short)inputFrame[payLoadPos+i]) ^ (short)(inputFrame[maskingKey+i%4]));
                }
                payLoad = ByteBuffer.wrap(inputFrame,payLoadPos,payloadLength);
            }
            map.put("WsFrame",new WsFrame(frameType,payLoad));
            map.put("size",2+payloadFieldExtraBytes+4+payloadLength);
            return map;
        }
        return null;
    }

    private boolean processWsFrame() {
        boolean r = true;
        do{
            Map<String,Object> map = readWsFrame(gBuffer);
            if(null==map){
                r = false;
                break;
            }
            WsFrame frame = (WsFrame)map.get("WsFrame");
            if(null==frame){
                break;
            }
            int size = (Integer)map.get("size");
            ByteBuffer tmp = gBuffer;
            gBuffer = ByteBuffer.allocate(gBuffer.position());
            if(tmp.position()-size>0) {
                gBuffer.put(tmp.array(), size, tmp.position() - size);
            }
            r = delegate.onWsFrame(conn,frame);
            if(!r){
                break;
            }
        }while(true);
        return r;
    }

    private boolean append2Buffer(ByteBuffer buffer) {
        int len = buffer.limit()-buffer.position();
        int pos = buffer.position();
        if(gBuffer.position()+len>gBuffer.limit()){
            ByteBuffer tmp = gBuffer;
            gBuffer = ByteBuffer.allocate(tmp.position()+len + 1);
            tmp.flip();
            gBuffer.put(tmp.array(),0,tmp.limit());
        }
        gBuffer.put(buffer.array(),pos,len);
        return true;
    }

    @Override
    public int handle(ByteBuffer buffer){
        int size = buffer.limit();
        if(!append2Buffer(buffer)){
            Logger.log("[WsParser] append to buffer Error");
            return 0;
        }
        if(!processWsFrame()){
            Logger.log("[WsParser] processWsFrame Error");
            return 0;
        }
        return size;
    }

    public String upgrade() {
        String szKey = this.key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String rsp = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(szKey.getBytes("iso-8859-1"), 0, szKey.length());
            byte[] sha1Hash = md.digest();
            sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
            szKey = encoder.encode(sha1Hash);
            rsp = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n";
            if(this.protocol!=null) {
                rsp+="Sec-WebSocket-Protocol: " + this.protocol + "\r\n";
            }
            rsp+="Sec-WebSocket-Accept: "+szKey+"\r\n\r\n";
        } catch (Exception e) {
            Logger.log("[WsParser] error==>"+e.getMessage());
        }
        return rsp;
    }

    public boolean prepare(HashMap<String, String> headers) {
        this.host = headers.get("Host");
        this.key = headers.get("Sec-WebSocket-Key");
        this.protocol = headers.get("Sec-WebSocket-Protocol");
        this.version = headers.get("Sec-WebSocket-Version");
        Logger.log("[WsParser] prepared==>host="+host+",key="+key+",protocol="+protocol+",version="+version);
        if(!this.version.equals("13")){
            Logger.log("[WsParser] now we only support version 13");
            return false;
        }
        return true;
    }

    public void clear(){
        this.gBuffer.clear();
        this.gBuffer = null;
        this.conn = null;
        this.delegate = null;
        this.protocol = null;
        this.version = null;
        this.key = null;
        this.host = null;
    }

    public static class WsFrame{
        public static final byte WS_TEXT_FRAME   = (byte)0x01;
        public static final byte WS_BINARY_FRAME = (byte)0x02;
        public static final byte WS_PING_FRAME   = (byte)0x09;
        public static final byte WS_PONG_FRAME   = (byte)0x0A;
        public static final byte WS_CLOSING_FRAME= (byte)0x08;

        public int frameType;
        public ByteBuffer payLoad;

        public WsFrame(int frameType, ByteBuffer payLoad){
            this.frameType = frameType;
            this.payLoad = payLoad;
        }

        public ByteBuffer toByteBuffer(){
            int dataLength = payLoad==null?0:(payLoad.limit()-payLoad.position());
            int outLength = 0;
            if(dataLength<=125){
                outLength = 2;
            }else if(dataLength <= 0xFFFF){
                outLength = 4;
            }else{
                return null;
            }
            ByteBuffer r = ByteBuffer.allocate(outLength+dataLength);
            r.put((byte)(0x80 | frameType));
            if(outLength==2){
                r.put((byte)dataLength);
            }else{
                r.put((byte)126);
                r.putShort((short)dataLength);
            }
            if(payLoad!=null){
                r.put(payLoad.array(),payLoad.position(),payLoad.limit());
            }
            return r;
        }
    }

    public interface Delegate{
        boolean onWsFrame(Connection conn, WsFrame frame);
    }
}
