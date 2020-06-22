package com.qp.rpc;

import com.qp.utils.Logger;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用来服务组内部RPC的模块
 * 组成：1个中心服(ZOOKEEPER) + N个互相协作的服务
 * 通过nginx反向代理这个服务组来给外网提供Api
 */
public class RegCenter implements Watcher {
    private static RegCenter sInst = new RegCenter();
    private Map<String,Consumer> consumers = null;
    private Map<String,Provider> providers = null;
    private ZooKeeper zooKeeper = null;

    private RegCenter(){
        consumers = new HashMap<>();
        providers = new HashMap<>();
        setup();
    }

    public static RegCenter shared(){
        return sInst;
    }

    @Override
    public void process(WatchedEvent event) {
        Logger.log("[RegCenter======>] receive the event:"+event);
        if(Event.KeeperState.SyncConnected == event.getState()) {
            switch (event.getType()){
                case NodeDataChanged:
                case NodeChildrenChanged:{
                    processConsumer(event.getPath());
                }
            }
        }else{
            if(Event.KeeperState.Expired==event.getState()){
                close();
                setup();
            }
        }
    }

    private void processConsumer(String path) {
        Consumer consumer = consumers.get(path);
        if(null==consumer){
            return;
        }
        doConsume(consumer);
    }

    private void close(){
        if(zooKeeper != null) {
            try {
                zooKeeper.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            zooKeeper = null;
        }
    }

    private void doProvide(Provider provider) {
        try {
            zooKeeper.setData(provider.getPath(), provider.getContent().getBytes(), -1);
        }catch (Exception e){
            Logger.log("[RegCenter] ==>"+e.getMessage());
        }
    }

    private void doConsume(Consumer consumer){
        try {
            byte[] dataBytes = zooKeeper.getData(consumer.getPath(), true, new Stat());
            consumer.setContent(new String(dataBytes));
            if(consumer.isHasChildren()){
                List<String> children = zooKeeper.getChildren(consumer.getPath(), true);
                consumer.setChildren(children);
            }
        }catch (Exception e){
            Logger.log("[RegCenter] ==>"+e.getMessage());
        }
    }

    private void onEstablished() {
        for(Consumer consumer : consumers.values()){
            doConsume(consumer);
        }
        for(Provider provider : providers.values()){
            doProvide(provider);
        }
    }

    private void setup(){
        boolean established = false;
        Logger.log("[RegCenter] zookeeper setup enter");
        while(!established) {
            try {
                zooKeeper = new ZooKeeper("zk01:2181", 5000, this);
                while (zooKeeper.getState() != ZooKeeper.States.CONNECTED) ;
                established = true;
            } catch (Exception e) {
                Logger.log("[RegCenter] zookeeper error==>" + e.getMessage());
            }
            if(!established){
                try {
                    Thread.sleep(5000);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        Logger.log("[RegCenter] zookeeper success");
        onEstablished();
    }

    public boolean consume(String path,boolean hasChildren){
        if(zooKeeper.getState() != ZooKeeper.States.CONNECTED){
            return false;
        }
        Consumer consumer = new Consumer(path,hasChildren);
        consumers.put(path,consumer);
        doConsume(consumer);
        return true;
    }

    public Consumer getConsumer(String path){
        Consumer consumer = consumers.get(path);
        if(null==consumer){
            return null;
        }
        return consumer;
    }

    public boolean provide(String path, int type, String content){
        if(zooKeeper.getState() != ZooKeeper.States.CONNECTED){
            return false;
        }
        Provider provider = new Provider(path);
        provider.setType(type);
        provider.setContent(content);
        providers.put(path,provider);
        doProvide(provider);
        return true;
    }

    public static void main(String[] args){
        RegCenter.shared().provide("/mytest", Provider.PERSISTENT,"good");
        RegCenter.shared().consume("/mytest",true);
        System.out.println(RegCenter.shared().getConsumer("/mytest"));
        while(true){
            try{
                Thread.currentThread().sleep(Integer.MAX_VALUE);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
