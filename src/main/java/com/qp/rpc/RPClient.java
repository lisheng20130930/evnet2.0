package com.qp.rpc;

import com.qp.utils.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RPClient {
    private String url = null;

    private static RPClient build(String pathService){
        Consumer consumer = RegCenter.shared().getConsumer(pathService);
        if(null!=consumer&&consumer.isHasChildren()){
            List<String> list = consumer.getChildren();
            if(list.size()>0){
                int n = new Random().nextInt(list.size());
                return new RPClient(list.get(n));
            }
        }
        return null;
    }

    private static RPClient getRPClient(String pathService){
        return build(pathService);
    }

    private void close(){
        //HERE RETURN TO POOL
    }

    public static Map<String,String> RPC(String pathService,Map<String,String> params){
        Map<String,String> result = null;
        RPClient rpClient = RPClient.getRPClient("/mytest");
        if(rpClient!=null){
            result = rpClient.RPC(params);
            rpClient.close();
        }
        return result;
    }

    private RPClient(String url){
        this.url = url;
    }

    public Map<String,String> RPC(Map<String,String> params){
        Logger.log("[RPClient] RPC CALL. URL="+this.url+",params="+params);
        return new HashMap<>();
    }

    public static void main(String[] args){
        RegCenter.shared().consume("/mytest",true);
        Map<String,String> params = new HashMap<>();
        Map<String,String> result = RPClient.RPC("/mytest",params);
        System.out.println(result);
    }
}
