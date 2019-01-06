package com.github.churtado.flink.external.postgres;

import com.github.churtado.flink.external.twitter.Tweet;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * This is an idempotent sink based on
 * the uid of tweets
 */
public class TwitterPostgresSink extends RichSinkFunction<Tweet> {

    private static final String UPSERT_CASE = "INSERT INTO tweets (id, text, created_at, timestamp_ms) " +
            "VALUES (?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET text=?, created_at=?, timestamp_ms=?";

    private PreparedStatement statement;
    private transient int batchCount = 0;
    private transient long lastBatchTime;
    Connection connection;

    @Override
    public void invoke(Tweet tweet, SinkFunction.Context context) throws Exception {

        statement.setLong(1, tweet.id);
        statement.setString(2, tweet.text);
        statement.setString(3, tweet.createdAt);
        statement.setLong(4, tweet.timestamp);
        statement.setString(5, tweet.text);
        statement.setString(6, tweet.createdAt);
        statement.setLong(7, tweet.timestamp);
        statement.addBatch();

        batchCount++;

        if (shouldExecuteBatch()) {
            statement.executeBatch();
            batchCount = 0;
            lastBatchTime = System.currentTimeMillis();
        }

        statement.executeBatch();

    }

    private boolean shouldExecuteBatch() {
        if (batchCount >=5) {
            return true;
        }
        return false;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        Class.forName("org.postgresql.Driver");
        connection =
                DriverManager.getConnection("jdbc:postgresql://localhost:5432/twitter?user=postgres&password=password");

        statement = connection.prepareStatement(UPSERT_CASE);
    }

    @Override
    public void close() throws SQLException {
        statement.close();
        connection.close();
    }
}
