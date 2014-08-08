package com.signalfuse.codahale.metrics;

import static org.junit.Assert.assertEquals;

import java.util.SortedMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.signalfuse.metrics.MetricFactoryBuilder;
import com.signalfuse.metrics.connection.InjectedDataPointReceiverFactory;
import com.signalfuse.metrics.connection.StoredDataPointReceiver;
import com.signalfuse.metrics.datumhandler.DirectCallDatumHandler;
import com.signalfuse.metrics.metricbuilder.MetricFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class SignalFuseReporterTest {
    @Test
    public void testReporter() throws InterruptedException {
        StoredDataPointReceiver dbank = new StoredDataPointReceiver();
        assertEquals(0, dbank.addDataPoints.size());

        MetricFactoryBuilder builder = new MetricFactoryBuilder().usingToken("")
            .usingDatumHandlerFactory(new DirectCallDatumHandler.Factory());

        MetricFactory metricFactory = builder.usingDataPointReceiverFactory(
                new InjectedDataPointReceiverFactory(dbank)).build();

        MetricRegistry metricRegistery = new MetricRegistry();
        String name = "sf_reporter";
        MetricFilter filter = MetricFilter.ALL;
        TimeUnit rateUnit = TimeUnit.SECONDS;
        TimeUnit durationUnit = TimeUnit.SECONDS;
        final Semaphore S = new Semaphore(0);
        LockedSignalFuseReporter reporter = new LockedSignalFuseReporter(metricRegistery, name,
            filter, rateUnit, durationUnit, metricFactory, S);

        metricRegistery.register("gauge", new Gauge<Integer>() {
            public Integer getValue() {
                return 1;
            }
        });
        metricRegistery.counter("counter").inc();
        metricRegistery.counter("counter").inc();

        reporter.start(1, TimeUnit.MICROSECONDS);
        S.acquire(1);

        assertEquals(2, dbank.addDataPoints.size());
        assertEquals(2, (int) dbank.addDataPoints.get(1).getValue().getIntValue());
    }

    /**
     * A version of SignalFuseReporter that lets us know (via a Semaphore) when it's report() is
     * called, then stops itself
     */
    private static final class LockedSignalFuseReporter extends SignalFuseReporter {
        private final Semaphore S;

        private LockedSignalFuseReporter(MetricRegistry registry, String name,
                                         MetricFilter filter, TimeUnit rateUnit,
                                         TimeUnit durationUnit, MetricFactory metricFactory,
                                         Semaphore S) {
            super(registry, name, filter, rateUnit, durationUnit, metricFactory, MetricDetails.ALL);
            this.S = S;
        }

        @Override
        public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters,
                           SortedMap<String, Histogram> histograms,
                           SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
            super.report(gauges, counters, histograms, meters, timers);
            S.release();
            this.stop();
        }
    }
}
