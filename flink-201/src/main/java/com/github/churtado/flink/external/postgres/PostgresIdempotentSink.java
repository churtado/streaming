package com.github.churtado.flink.external.postgres;

import com.github.churtado.flink.external.twitter.Tweet;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

/**
 * This sink is similar to the twitter
 * postgres sink, just some added logic
 * using prepared statements for a different
 * approach
 *
 * I think this approach is slower, doesn't
 * take advantage of postgres capabilities
 * and doesn't batch statements, but is nonetheless
 * quite useful as a generic starting point using
 * any other jdbc databases.
 */
public class PostgresIdempotentSink extends RichSinkFunction<Tweet> {

    private static final String INSERT_STATEMENT = "INSERT INTO tweets (id, text, created_at, timestamp_ms)" +
            "VALUES (?, ?, ?, ?)";
    private static final String UPDATE_STATEMENT = "UPDATE tweets SET text=?, created_at=?, timestamp_ms=? WHERE id=?";

    private PreparedStatement insertStmt;
    private PreparedStatement updateStmt;
    Connection connection;

    @Override
    public void open(Configuration parameters) throws Exception {
        Class.forName("org.postgresql.Driver");
        connection =
                DriverManager.getConnection(
                        "jdbc:postgresql://localhost:5432/twitter?user=postgres&password=password",
                        new Properties()
                        );

        insertStmt = connection.prepareStatement(INSERT_STATEMENT);
        updateStmt = connection.prepareStatement(UPDATE_STATEMENT);
    }

    @Override
    public void invoke(Tweet tweet, SinkFunction.Context context) throws Exception {

        // set parameters for update statement and execute it
        updateStmt.setString(1, tweet.text);
        updateStmt.setString(2, tweet.createdAt);
        updateStmt.setLong(3, tweet.timestamp);
        updateStmt.setLong(4, tweet.id);
        updateStmt.execute();

        // execute insert statement if update statement did not update any row
        if (updateStmt.getUpdateCount() == 0) {
            // set parameters for insert statement
            insertStmt.setLong(1, tweet.id);
            insertStmt.setString(2, tweet.text);
            insertStmt.setString(3, tweet.createdAt);
            insertStmt.setLong(4, tweet.timestamp);
            // execute insert statement
            insertStmt.execute();
        }

    }

    @Override
    public void close() throws SQLException {
        insertStmt.close();
        updateStmt.close();
        connection.close();
    }

}
