package com.qp.evnet;

public class Timer{
    public Observer handler;
    public Object usr;
    public long timerID;  /* time event identifier. */
    public long when_ms;  /* milliseconds */

    public Timer(long timerID, Observer handler, Object usr){
        this.handler = handler;
        this.timerID = timerID;
        this.usr = usr;
    }

    public void addMillisecondsToNow(long milliseconds){
        this.when_ms = System.currentTimeMillis()+milliseconds;
    }
}