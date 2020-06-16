package com.qp;

import org.apache.zookeeper.*;

public class ZKHelper implements Watcher {
    private ZooKeeper zooKeeper = null;

    @Override
    public void process(WatchedEvent event) {
        System.out.println("[ZKHelper======>] receive the event:"+event);
        if(Event.KeeperState.SyncConnected == event.getState()) {
            switch (event.getType()){
                case NodeCreated:
                case NodeDeleted:
                case NodeDataChanged:{
                    try {
                        zooKeeper.exists("/evnet", true);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }else{
            if(Event.KeeperState.Expired==event.getState()){
                try{
                    zooKeeper.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
                setup();
            }
        }
    }

    public void setup(){
        ZKHelper helper = new ZKHelper();
        try {
            zooKeeper = new ZooKeeper("zk01:2181",5000,helper);
            while(zooKeeper.getState()!=ZooKeeper.States.CONNECTED);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("zookeeper session established");
        /*try {
            zooKeeper.setData("/evnet", (System.currentTimeMillis()+"").getBytes(), -1);
        }catch (Exception e){
            e.printStackTrace();
        } */
        try {
            zooKeeper.exists("/evnet", true);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void close(){
        if(zooKeeper != null) {
            try {
                zooKeeper.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            zooKeeper = null;
        }
    }

    public static void main(String[] args){
        new ZKHelper().setup();
        while(true){
            try{
                Thread.currentThread().sleep(Integer.MAX_VALUE);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
