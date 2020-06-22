package com.qp.harbor;

import com.alibaba.fastjson.JSON;

import java.util.List;

public class Consumer{
    private boolean hasChildren;
    private String path;
    private String content;
    private List<String> children;

    public Consumer(String path, boolean hasChildren){
        this.hasChildren = hasChildren;
        this.path = path;
    }

    public boolean isHasChildren() {
        return hasChildren;
    }

    public String getPath(){
        return this.path;
    }

    public String getContent(){
        return this.content;
    }

    public void setContent(String content){
        this.content = content;
    }

    public void setChildren(List<String> children) {
        this.children = children;
    }

    public List<String> getChildren() {
        return this.children;
    }

    @Override
    public String toString(){
        return JSON.toJSONString(this);
    }
}
