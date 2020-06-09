package com.qp.evnet;

public abstract class EvActor implements Runnable{
    public TCB tcb = null;
    public Object usr = null;

    public EvActor(TCB tcb,Object usr){
        this.tcb = tcb;
        this.usr = usr;
    }

    public void clear(){
        this.tcb = null;
        this.usr = null;
    }

    public interface TCB extends Observer{
        void run(Object usr);
    }
}
