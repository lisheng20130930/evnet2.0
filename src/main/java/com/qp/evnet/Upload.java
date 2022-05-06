package com.qp.evnet;

import com.qp.utils.Logger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class Upload {
    public static final String UPLOAD_DIR = "./upload";
    private Map<String,String> mhdr = null;
    private String filed = null;
    private MuParser mp = null;
    private boolean isComplete;
    private String filename;
    private File pFile;

    public boolean upload_handleHeader(Map<String,String> map){
        String contentValue = map.get("Content-Type".toLowerCase());
        if(contentValue==null){
            Logger.log("[Trace@UPLOAD] Error. NULL contentType");
            return false;
        }
        Logger.log("[Trace@UPLOAD] content type:"+contentValue);
        if(!contentValue.contains("multipart/form-data;")){
            Logger.log("[Trace@UPLOAD] Error. not match content-type Value");
            return false;
        }
        int idx = contentValue.indexOf("boundary");
        if (idx==(-1)){
            Logger.log("[Trace@UPLOAD] Error. NOt found boundary");
            return false;
        }
        upload_initMu_Parser("--"+contentValue.substring(idx+9));
        mhdr = new HashMap<>();
        isComplete = false;
        return true;
    }

    protected void upload_initMu_Parser(String boundary){
        Logger.log("[upload_initMu_Parser] boundary===>"+boundary);
        MuParser.MuParserSettings settings = new MuParser.MuParserSettings();
        settings.on_header_field = (p, buffer, pos, len) -> {
            if(filed != null){
                Logger.log("[Req] filed not NULL, you should check HERE");
            }
            filed = new String(buffer.array(), pos, len);
            return 0;
        };
        settings.on_header_value = (p, buffer, pos, len) -> {
            if(null!=filed){
                mhdr.put(filed.toLowerCase(),new String(buffer.array(), pos, len));
                filed = null;
            }
            return 0;
        };
        settings.on_headers_complete = p -> {
            String contentValue = mhdr.get("Content-Disposition".toLowerCase());
            if(contentValue==null){
                Logger.log("[Trace@UPLOAD] Error. NOT Found Content-Disposition");
                return -1;
            }
            Logger.log("[Trace@UPLOAD] Content-Disposition:"+contentValue);
            int start = contentValue.indexOf("filename=\"");
            if(start == (-1)){
                Logger.log("[Trace@UPLOAD] Error. NOT Found filename");
                return -1;
            }
            int end = contentValue.indexOf("\"", start+10);
            if((end==(-1)) || end-start>=256){
                Logger.log("[Trace@UPLOAD] Error. or name too long");
                return (-1);
            }
            filename = contentValue.substring(start+10, end);
            int idx = filename.lastIndexOf('.');
            String S;
            if(idx == (-1)){
                S = ".docx";
            }else{
                S = filename.substring(idx);
            }
            filename = System.currentTimeMillis()+S;
            String pathname = upload_pathName(filename);
            pFile = new File(pathname);
            if(pFile.exists()){
                pFile.delete();
            }
            return 0;
        };
        settings.on_part_data = (p, buffer, pos, len) -> {
            try{
                FileUtils.writeByteArrayToFile(pFile, buffer.array(),pos,len,true);
                return 0;
            }catch (Exception e){
                e.printStackTrace();
            }
            return -1;
        };
        settings.on_part_data_end = p -> {
            pFile = null;
            return 0;
        };
        settings.on_body_end = p -> {
            isComplete = true;
            return 0;
        };
        mp = MuParser.createMuParser(boundary, settings);
    }

    public int upload_bodyContinue(ByteBuffer buffer, int pos, int len){
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer.array(),pos,len);
        return mp.multipart_parser_execute(byteBuffer);
    }

    public boolean bIsComplete(){
        return this.isComplete;
    }

    public void clear() {
        this.pFile = null;
        this.mhdr = null;
        this.mp = null;
    }

    public static String upload_pathName(String name){
        return UPLOAD_DIR+"/"+name;
    }

    public static boolean upload_checkURL(String szUrl){
        if(!szUrl.contains("/upload")){
            return false;
        }
        return true;
    }

    public String getUploadFileName() {
        return upload_pathName(filename);
    }
}
