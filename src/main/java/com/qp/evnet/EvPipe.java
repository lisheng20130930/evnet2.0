package com.qp.evnet;

import com.qp.utils.Logger;

import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;

public class EvPipe{
    private Pipe.SourceChannel sourceChannel;
    private Pipe.SinkChannel sinkChannel;
    private EventLoop loop = null;
    private Pipe pipe = null;

    public EvPipe(EventLoop loop){
        this.loop = loop;
        try {
            this.pipe = Pipe.open();
            setup();
        }catch (Exception e){
            Logger.log("[EvPipe] ==>"+e.getMessage());
        }
    }

    private void write(){
        ByteBuffer buffer = ByteBuffer.wrap("dummy".getBytes());
        try {
            int r = 0;
            do{
                r=sinkChannel.write(buffer);
            }while(r<=0);
        }catch (Exception e){
            Logger.log("[EvPipe] ==>"+e.getMessage());
        }
    }

    private void readAll(){
        ByteBuffer buffer = ByteBuffer.allocate(64);
        try {
            int r = 0;
            do {
                r = sourceChannel.read(buffer);
                buffer.flip();
            }while(r>0);
        }catch (Exception e){
            Logger.log("[EvPipe] ==>"+e.getMessage());
        }
    }

    private void setup(){
        sourceChannel = pipe.source();
        sinkChannel = pipe.sink();
        try {
            sourceChannel.configureBlocking(false);
            sinkChannel.configureBlocking(false);
        }catch (Exception e){
            Logger.log("[EvPipe] ==>"+e.getMessage());
        }
        loop.eventAdd(sourceChannel, NIOEvent.AE_READ, new Observer() {
            public void handle(Object usr, int mask) {
                SelectableChannel fd = (SelectableChannel)usr;
                if((mask & NIOEvent.AE_READ)!=0){
                    readAll();
                }
            }
        }, sourceChannel);
    }

    public void close(){
        try {
            sinkChannel.close();
            sourceChannel.close();
        }catch (Exception e){
            Logger.log("[EvPipe] ==>"+e.getMessage());
        }
    }

    public void async(){
        write();
    }
}
