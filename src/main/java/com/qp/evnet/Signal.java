package com.qp.evnet;

import com.qp.utils.Logger;
import sun.misc.SignalHandler;

public class Signal implements SignalHandler {
    public static final int SIG_EXIT = 5;
    public static final int SIG_HUP  = 6;
    public static volatile int sig = 0;
    private EventLoop loop = null;

    public Signal(EventLoop loop) {
        this.loop = loop;
        this.setupHandler();
    }

    private void setupHandler() {
        this.handleSignal("TERM");
        this.handleSignal("INT");
        this.handleSignal("HUP");
    }

    public void handleSignal(final String signalName) {
        try {
            sun.misc.Signal.handle(new sun.misc.Signal(signalName), this);
        } catch (IllegalArgumentException e) {
            Logger.log("[Signal] ===>"+e.getMessage());
        }
    }

    public void handle(sun.misc.Signal signal) {
        if(signal.getName().equals("TERM")
                ||signal.getName().equals("INT")){
            sig = SIG_EXIT;
        }else{
            sig = SIG_HUP;
        }
        loop.async();
    }
}
