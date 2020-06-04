package com.qp.evnet;

public interface Observer {
    void handle(Object usr, int mask);
}
