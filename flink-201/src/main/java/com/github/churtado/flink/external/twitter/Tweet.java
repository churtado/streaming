package com.github.churtado.flink.external.twitter;

import java.io.Serializable;

public class Tweet implements Serializable {

    public Tweet(){ }

    public long id;
    public String text;
    public String createdAt;
    public long timestamp;

}
