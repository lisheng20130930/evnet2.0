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

    public static abstract class TCB implements Observer{
        public void handle(Object usr, int mask){

        }
        public abstract boolean run(Object usr);
    }
}
