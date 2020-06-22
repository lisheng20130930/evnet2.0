package com.qp.harbor;

import com.qp.utils.HttpUtils;
import com.qp.utils.Logger;

import java.util.*;

public class RPClient {
    private String getURL(String pathService){
        Consumer consumer = RegCenter.shared().getConsumer(pathService);
        if(null!=consumer&&consumer.isHasChildren()){
            List<String> list = consumer.getChildren();
            if(list.size()>0){
                int n = new Random().nextInt(list.size());
                return list.get(n);
            }
        }
        return null;
    }

    public String RPC(String pathService, String jsonStr){
        Logger.log("[RPClient] RPC CALL. pathService="+pathService+",params="+jsonStr);
        String result = null;
        String szURL = getURL(pathService);
        //szURL = "http://127.0.0.1:32000/api";
        if(szURL!=null){
            long tmp = System.currentTimeMillis();
            result = HttpUtils.sendPostDataByJson(szURL,jsonStr);
            Logger.log("[RPClient] used==>"+(System.currentTimeMillis()-tmp));
        }
        return result;
    }

    public static void main(String[] args){
        RegCenter.shared().consume("/mytest",true);
        Logger.log(new RPClient().RPC("/mytest","{\"cmd\": 5000}"));
        Logger.log(new RPClient().RPC("/mytest","{\"cmd\": 5000}"));
        Logger.log(new RPClient().RPC("/mytest","{\"cmd\": 5000}"));
    }
}
