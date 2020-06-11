package com.qp.evnet;

import com.qp.utils.Logger;

import java.nio.channels.SelectableChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventLoop {
    private Map<SelectableChannel, NIOEvent> events = null;
    private List<EvActor> finished = null;
    private ExecutorService pool = null;
    private Map<Long,Timer> timers = null;
    private Signal signal = null;
    private EvPipe pipe = null;
    private BackEnd backEnd = null;
    private int _timeout = 500;
    private int _nthread = 2;

    public EventLoop(int nthread, int timeout){
        this._nthread = Math.max(_nthread,nthread);
        this._timeout = Math.max(_timeout,timeout);
        events = new HashMap<SelectableChannel, NIOEvent>();
        pool = Executors.newFixedThreadPool(_nthread);
        finished = new LinkedList<EvActor>();
        timers = new HashMap<Long,Timer>();
        backEnd = new BackEnd();
        pipe = new EvPipe(this);
        signal = new Signal(this);
    }

    private void processNIOEvents(long milliseconds){
        try {
            Collection<BackEnd.FiredEvent> fireds = backEnd.poll(milliseconds);
            if (fireds.size() == 0) {
                return;
            }
            Iterator<BackEnd.FiredEvent> it = fireds.iterator();
            while (it.hasNext()) {
                BackEnd.FiredEvent fired = it.next();
                NIOEvent event = events.get(fired.socket);
                int mask = fired.mask;
                if (event == null) {
                    continue;
                }
                if ((event.mask & mask & NIOEvent.AE_ACCEPT) != 0) {
                    if (null != event.acceptHandler) {
                        event.acceptHandler.handle(event.getUsr(), mask);
                    }
                }
                if ((event.mask & mask & NIOEvent.AE_READ) != 0) {
                    if (null != event.readHandler) {
                        event.readHandler.handle(event.getUsr(), mask);
                    }
                }
                if ((event.mask & mask & NIOEvent.AE_WRITE) != 0) {
                    if (null != event.writeHandler) {
                        event.writeHandler.handle(event.getUsr(), mask);
                    }
                }
            }
            fireds.clear();
        }catch (Exception e){
            Logger.log(e.getMessage());
        }
    }

    private long nearestMillionSeconds(){
        long milliseconds = _timeout;
        Timer nearest = null;
        for(Timer timer : timers.values()){
            if (nearest==null || timer.when_ms < nearest.when_ms) {
                nearest = timer;
            }
        }
        if(nearest!=null){
            long now = System.currentTimeMillis();
            if(nearest.when_ms>now){
                milliseconds = Math.min(nearest.when_ms-now,_timeout);
            }
        }
        Logger.log("Select milliseconds="+milliseconds);
        return milliseconds;
    }

    public void processActors() {
        LinkedList<EvActor> tmp = null;
        synchronized (finished){
            tmp = new LinkedList<EvActor>(finished);
            finished.clear();
        }
        int processed = 0;
        while(tmp.size()>0){
            EvActor actor = tmp.remove(0);
            actor.tcb.handle(actor.usr,0);
            actor.clear();
            processed ++;
        }
        if(processed>0) {
            Logger.log("[EventLoop]==>" + processed + " Actor handled...");
        }
        tmp.clear();
    }

    public void processEvents(){
        processNIOEvents(nearestMillionSeconds());
        processTimeEvents();
        processActors();
    }

    public void clear(){
        try {
            pool.shutdownNow();
        }catch (Exception e){
            Logger.log(e.getMessage());
        }
        events.clear();
        timers.clear();
        pipe.close();
    }

    public void async(){
        pipe.async();
    }

    public boolean eventAdd(SelectableChannel socket, int mask, Observer handler, Object usr){
        if(!backEnd.addEvent(socket, mask)){
            return false;
        }

        NIOEvent event = events.get(socket);
        if(null==event){
            event = new NIOEvent(socket,usr);
        }

        if((mask& NIOEvent.AE_ACCEPT)!=0){
            event.mask |= NIOEvent.AE_ACCEPT;
            event.acceptHandler = handler;
        }

        if((mask & NIOEvent.AE_READ) !=0){
            event.mask |= NIOEvent.AE_READ;
            event.readHandler = handler;
        }

        if((mask & NIOEvent.AE_WRITE) !=0){
            event.mask |= NIOEvent.AE_WRITE;
            event.writeHandler = handler;
        }

        events.put(socket,event);
        return true;
    }

    public void eventDel(SelectableChannel socket, int mask){
        NIOEvent event = events.get(socket);
        if(null==event||((event.mask&mask)==0)){
            return;
        }

        backEnd.removeEvent(socket, mask);

        if((mask& NIOEvent.AE_ACCEPT)!=0){
            event.mask &=~NIOEvent.AE_ACCEPT;
            event.acceptHandler = null;
        }

        if((mask& NIOEvent.AE_READ)!=0){
            event.mask &=~NIOEvent.AE_READ;
            event.readHandler = null;
        }

        if((mask& NIOEvent.AE_WRITE)!=0){
            event.mask &=~NIOEvent.AE_WRITE;
            event.writeHandler = null;
        }

        if(event.mask == 0){
            event.clear();
            events.remove(socket);
        }
    }

    public void actorAdd(EvActor.TCB tcb, Object usr) {
        pool.submit(new EvActor(tcb,usr) {
            public void run() {
                boolean r = tcb.run(usr);
                if(r){
                    synchronized(finished) {
                        finished.add(this);
                    }
                    async();
                }
            }
        });
    }

    private int processTimeEvents() {
        List<Long> fireds = new LinkedList<Long>();
        long now = System.currentTimeMillis();

        for(Timer timer : timers.values()){
            if(timer.when_ms<=now){
                fireds.add(timer.timerID);
            }
        }
        int processed = 0;
        Iterator<Long> it = fireds.iterator();
        while (it.hasNext()) {
            Long timerID = it.next();
            Timer timer = timers.remove(timerID);
            if (timer == null) {
                continue;
            }
            timer.handler.handle(timer.usr,0);
        }
        fireds.clear();
        return processed;
    }

    public void setTimer(long timerID, long milliseconds, Object usr, Observer handler){
        Timer timer = timers.get(timerID);
        if(null==timer){
            timer = new Timer(timerID,handler,usr);
            timers.put(timerID,timer);
        }
        timer.addMillisecondsToNow(milliseconds);
    }

    public void killTimer(long timerID){
        Timer timer = timers.remove(timerID);
        if(timer!=null){
            timer.clear();
        }
    }
}
