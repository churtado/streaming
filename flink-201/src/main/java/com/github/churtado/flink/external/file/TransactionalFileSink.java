package com.github.churtado.flink.external.file;

import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.sink.TwoPhaseCommitSinkFunction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TransactionalFileSink extends TwoPhaseCommitSinkFunction<Tuple2<String, Double>, String, Void> {

    BufferedWriter transactionWriter;
    String targetPath;
    String tempPath;

    /**
     * Use default {@link ListStateDescriptor} for internal state serialization. Helpful utilities for using this
     * constructor are {@link TypeInformation#of(Class)}, {@link TypeHint} and
     * {@link TypeInformation#of(TypeHint)}. Example:
     * <pre>
     * {@code
     * TwoPhaseCommitSinkFunction(TypeInformation.of(new TypeHint<State<TXN, CONTEXT>>() {}));
     * }
     * </pre>
     *
     * @param transactionSerializer {@link TypeSerializer} for the transaction type of this sink
     * @param contextSerializer     {@link TypeSerializer} for the context type of this sink
     */
    public TransactionalFileSink(String targetPath, String tempPath, TypeSerializer<String> transactionSerializer, TypeSerializer<Void> contextSerializer) {
        super(transactionSerializer, contextSerializer);
        this.targetPath = targetPath;
        this.tempPath = tempPath;
    }

    /** Creates a temporary file for a transaction into which the records are
     * written.
     */
    @Override
    protected String beginTransaction() throws Exception {

        // path of transaction file is build from current time and task index
        String timeNow = LocalDateTime.now(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long taskIdx = this.getRuntimeContext().getIndexOfThisSubtask();
        String transactionFile = timeNow+"-"+taskIdx;

        // create transaction file and writer
        Path tFilePath = Paths.get(tempPath+"/"+transactionFile);
        Files.createFile(tFilePath);
        this.transactionWriter = Files.newBufferedWriter(tFilePath);
        System.out.println("Creating transaction File: " + tFilePath);

        // name of transaction file is returned to later identify the transaction
        return transactionFile;
    }

    /**
     * Write record into the current transaction file.
     * @param transaction
     * @param value
     * @param context
     * @throws Exception
     */
    @Override
    protected void invoke(String transaction, Tuple2<String, Double> value, Context context) throws Exception {
        transactionWriter.write(value.toString());
        transactionWriter.write("\n");
    }

    /**
     * Flush and close the current transaction file
     * @param transaction
     * @throws Exception
     */
    @Override
    protected void preCommit(String transaction) throws Exception {
        transactionWriter.flush();
        transactionWriter.close();
    }

    /**
     * Commit a transaction by moving the pre-committed transaction file
     * to the target directory
     * @param transaction
     */
    @Override
    protected void commit(String transaction) {
        Path tFilePath = Paths.get(tempPath+"/"+transaction);
        // check if the file exists to ensure that the commit is idempotent
        if(Files.exists(tFilePath)){
            Path cFilePath = Paths.get(targetPath+"/"+transaction);
            try {
                Files.move(tFilePath, cFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    /**
     * Aborts a transaction by deleting the transaction file
     * @param transaction
     */
    @Override
    protected void abort(String transaction) {
        Path tFilePath = Paths.get(tempPath+"/"+transaction);
        if (Files.exists(tFilePath)) {
            try {
                Files.delete(tFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
