package com.github.churtado.flink.external.twitter;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;

import javax.annotation.Nullable;

/**
 * One thing to look out for:
 * Note that the watermarks of a source instance cannot make progress if a partition becomes
 * inactive and does not provide messages. As a consequence, a single inactive partition can
 * stall a whole application because the application’s watermarks do not make progress.
 */

public class TwitterTimestampAssigner implements AssignerWithPeriodicWatermarks<ObjectNode> {

    Long bound = 60L * 1000L;       // 1 min in ms
    Long maxTs = Long.MIN_VALUE;    // the maximum observed timestamp

    @Nullable
    @Override
    // get the max value minus one minute
    public Watermark getCurrentWatermark() {
        return new Watermark(maxTs - bound);
    }

    // return the timestamp
    @Override
    public long extractTimestamp(ObjectNode tweet, long l) {

        Long timestamp = tweet.get("value").get("timestamp_ms").asLong();

        maxTs = Long.max(timestamp, maxTs);

        return timestamp;
    }
}







