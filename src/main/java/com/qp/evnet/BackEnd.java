package com.qp.evnet;

import com.qp.utils.Logger;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

public class BackEnd {
    private Selector selector = null;
    public BackEnd(){
        try {
            selector = Selector.open();
        }catch (Exception e){
            Logger.log("[BackEnd] ==>"+e.getMessage());
        }
    }

    public Collection<FiredEvent> poll(long timeout) throws Exception{
        Set<FiredEvent> fireds = new HashSet<FiredEvent>();
        if (selector.select(timeout) > 0) {
            Set<SelectionKey> set = selector.selectedKeys();
            Iterator<SelectionKey> it = set.iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                int mask = 0;
                if(!key.isValid()){
                    continue;
                }
                if ((key.interestOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT
                    && key.isAcceptable()) {
                    mask |= NIOEvent.AE_ACCEPT;
                }
                if ((key.interestOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ
                    && key.isReadable()) {
                    mask |= NIOEvent.AE_READ;
                }
                if ((key.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE
                    && key.isWritable()) {
                    mask |= NIOEvent.AE_WRITE;
                }
                fireds.add(new FiredEvent(key.channel(),mask));
                it.remove();
            }
        }
        return fireds;
    }

    public boolean addEvent(SelectableChannel socket, int mask){
        try {
            int flag = 0;
            if ((mask & NIOEvent.AE_ACCEPT) != 0) {
                flag |= SelectionKey.OP_ACCEPT;
            }
            if ((mask & NIOEvent.AE_READ) != 0) {
                flag |= SelectionKey.OP_READ;
            }
            if ((mask & NIOEvent.AE_WRITE) != 0) {
                flag |= SelectionKey.OP_WRITE;
            }
            SelectionKey key = socket.keyFor(selector);
            if (key == null) {
                key = socket.register(selector, flag);
            } else {
                key.interestOps(key.interestOps()|flag);
            }
            return true;
        }catch (Exception e){
            Logger.log("[BackEnd] ==>"+e.getMessage());
        }
        return false;
    }

    public void removeEvent(SelectableChannel socket, int mask) {
        SelectionKey key = socket.keyFor(selector);
        if(key == null){
            return;
        }
        int flg = key.interestOps();
        if ((mask & NIOEvent.AE_ACCEPT) != 0) {
            flg &= ~SelectionKey.OP_ACCEPT;
        }
        if ((mask & NIOEvent.AE_READ) != 0) {
            flg &= ~SelectionKey.OP_READ;
        }
        if ((mask & NIOEvent.AE_WRITE) != 0) {
            flg &= ~SelectionKey.OP_WRITE;
        }
        key.interestOps(flg);

        if(flg==0) {
            key.cancel();
        }
    }

    public static class FiredEvent{
        public SelectableChannel socket;
        public int mask;

        public FiredEvent(SelectableChannel socket, int mask){
            this.socket = socket;
            this.mask = mask;
        }
    }
}
