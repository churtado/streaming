package com.github.churtado.flink.utils;

import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;

import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * In the context of source functions that run in parallel,
 * so for example source functions that run in 2 instances
 * on a kafka topic with 3 partitions, it could happen that
 * one of the parallel instances doesn't receive input for
 * a long time. If that's the case, that means that the source
 * function isn't emitting timestamps and watermarks, and could
 * therefore stall the whole application, especially if there
 * are shuffle operations involved downstream. So, a source
 * function can designate itself as idle, until new data is
 * coming in. To do that use
 *
 * SourceContext.markAsTemporarilyIdle()
 *
 * Look up how it works in the documentation, as you may need
 * it in smaller projects
 */
public class SensorSource extends RichParallelSourceFunction<SensorReading> {

    private volatile boolean running = true;
    private static final int numSensors = 10;

    @Override
    public void run(final SourceContext<SensorReading> sourceContext) throws Exception {

        // initialize random number generator
        final Random rand = new Random();

        // I'm guessing this is to pool all 10 sensors under 2 threads
        final ScheduledExecutorService exec = Executors.newScheduledThreadPool(10);

        try{
            while (running) {
                for (int i = 0; i < numSensors; i++) {

                    // update the timestamp
                    long cur = System.currentTimeMillis();

                    // update the sensor value
                    Double reading = ThreadLocalRandom.current().nextDouble(30.0, 105.0);

                    // sensor id
                    String id = "sensor_" + i;
                    SensorReading event = new SensorReading();
                    event.id = id;
                    event.timestamp = Calendar.getInstance().getTimeInMillis();
                    event.temperature = reading;

                    exec.schedule(() -> {
                        sourceContext.collect(event);
                    }, 600, TimeUnit.MILLISECONDS);
                }
                Thread.sleep(500);
            }
        } finally {
            exec.shutdownNow();
        }


    }

    @Override
    public void cancel() {
        running = false;
    }
}
