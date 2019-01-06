package com.github.churtado.flink.external.twitter;

import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema;

public class TweetSerializer implements KeyedSerializationSchema<Tweet> {
    @Override
    public byte[] serializeKey(Tweet tweet) {
        return Long.toString(tweet.id).getBytes();
    }

    @Override
    public byte[] serializeValue(Tweet tweet) {

        String value = "{" + "\n" +
                "\"id\": \"" + tweet.id + "\"\n" +
                "\"created_at\": \"" + tweet.createdAt + "\"\n" +
                "\"timestamp\": \"" + tweet.timestamp + "\"\n" +
                "\"text\": \"" + tweet.text + "\"\n" +
                "}";

        return value.getBytes();
    }

    @Override
    public String getTargetTopic(Tweet tweet) {
        return null;
    }
}
