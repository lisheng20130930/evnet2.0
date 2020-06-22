package com.qp.harbor;

public class Provider {
    public static final int PERSISTENT = 0;
    public static final int EPHEMERAL = 1;
    private String path;
    private int type;
    private String content;

    public Provider(String path){
        this.path = path;
    }

    public String getPath(){
        return this.path;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent(){
        return this.content;
    }
}
