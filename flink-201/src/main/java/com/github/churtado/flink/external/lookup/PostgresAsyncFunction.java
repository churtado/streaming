package com.github.churtado.flink.external.lookup;

import com.github.churtado.flink.utils.SensorReading;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class PostgresAsyncFunction extends RichAsyncFunction<SensorReading, Tuple2<String, String>> {

    QueryExecutor queryExecutor = null;
    private Connection connection;
    private final String select = "SELECT color FROM sensors WHERE name = ?";
    private PreparedStatement statement;

    @Override
    public void open(Configuration parameters) throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");

        // using pooled connections
        connection = DBCPDataSource.getConnection();
        statement = connection.prepareStatement(select);


        queryExecutor =  new QueryExecutor(connection, statement);
    }

    @Override
    public void close() throws SQLException {

        statement.close();
        connection.close();

    }

    @Override
    public void asyncInvoke(SensorReading input, ResultFuture<Tuple2<String, String>> resultFuture) throws Exception {

        String name = input.id;

        // get name from postgres table
        Future<String> color = queryExecutor.getColor(input.id);


        // set the callback to be executed once the request by the client is complete
        // the callback simply forwards the result to the result future
        CompletableFuture.supplyAsync(new Supplier<String>() {

            @Override
            public String get() {
                try {
                    return color.get();
                } catch (InterruptedException | ExecutionException e) {
                    // Normally handled explicitly.
                    return null;
                }
            }
        }).thenAccept( (String dbResult) -> {
            resultFuture.complete(Collections.singleton(new Tuple2<>(input.id, dbResult)));
        });

    }

    class QueryExecutor {

        private ExecutorService executor = Executors.newSingleThreadExecutor();
        private Connection connection;
        private PreparedStatement statement;

        public QueryExecutor(Connection connection, PreparedStatement statement) throws SQLException, ClassNotFoundException {
            this.connection = connection;
            this.statement = statement;
        }

        public Future<String> getColor(String name) throws Exception {
            return executor.submit(() -> {

                String result = "unknown";
                statement.setString(1, name);
                ResultSet rs = statement.executeQuery();
                while(rs.next()){
                    result = rs.getString("color");
                }

                rs.close();

                return result;
            });
        }
    }

    static class DBCPDataSource {

        private static BasicDataSource ds = new BasicDataSource();

        static {
            ds.setUrl("jdbc:postgresql://localhost:5432/twitter?user=postgres&password=password");
            ds.setUsername("postgres");
            ds.setPassword("password");
            ds.setMinIdle(5);
            ds.setMaxIdle(10);
            ds.setMaxOpenPreparedStatements(100);
        }

        public static Connection getConnection() throws SQLException {
            return ds.getConnection();
        }

        private DBCPDataSource(){ }
    }
}