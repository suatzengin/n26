package com.n26.interview.analytics.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

@RestController
@RequestMapping(value = "/analytics")
public class AnalyticsController {

    private static final int MAX_SECONDS_PAST = 60;
    private static final NumberFormat FORMATTER = new DecimalFormat("#0.00");

    private AtomicReference<LocalDateTime> historicalTimeStart = new AtomicReference<>(LocalDateTime.now().minusSeconds(MAX_SECONDS_PAST));
    private final Object lock = new Object();
    private double[] sums = new double[MAX_SECONDS_PAST];
    private int[] count = new int[MAX_SECONDS_PAST];
    private Double[] maxs = new Double[MAX_SECONDS_PAST];
    private Double[] mins = new Double[MAX_SECONDS_PAST];

    {
        Arrays.fill(maxs, Double.MIN_VALUE);
        Arrays.fill(mins, Double.MAX_VALUE);
    }

    @RequestMapping(value = "/transactions", method = RequestMethod.POST)
    public int postTransaction(@RequestBody TransactionRequest request) {
        LocalDateTime currentTime = toLocalDateTime(request.getTimestamp());

        if (!isTimestampValid(currentTime)) return HttpStatus.NO_CONTENT.value();

        addTransaction(request.getAmount(), request.getTimestamp());

        return HttpStatus.CREATED.value();
    }

    @RequestMapping(value = "/statistics", method = RequestMethod.GET)
    @ResponseBody
    public StatisticsResponse getStatistics() {
        cleanOldValues(LocalDateTime.now());

        StatisticsResponse response;
        synchronized (lock) {
            response = new StatisticsResponse(format(DoubleStream.of(sums).sum()), getAverage(), getMax(), getMin(), IntStream.of(count).sum());
        }
        return response;
    }

    private boolean isTimestampValid(LocalDateTime time) {
        LocalDateTime current = LocalDateTime.now();

        return ((time.isBefore(current)) && time.until(current, ChronoUnit.SECONDS) <= MAX_SECONDS_PAST);
    }

    private void addTransaction(double amount, long timestamp) {
        LocalDateTime currentTime = toLocalDateTime(timestamp);
        cleanOldValues(currentTime);

        int index = (int) historicalTimeStart.get().until(currentTime, ChronoUnit.SECONDS);
        synchronized (lock) {
            sums[index] += amount;
            count[index]++;
            if (amount > maxs[index]) maxs[index] = amount;
            if (amount < mins[index]) mins[index] = amount;
        }
    }

    private void cleanOldValues(LocalDateTime currentTime) {
        long seconds = historicalTimeStart.get().until(currentTime, ChronoUnit.SECONDS);
        if (seconds-MAX_SECONDS_PAST >= MAX_SECONDS_PAST) {
            synchronized (lock) {
                Arrays.fill(sums, 0);
                Arrays.fill(count, 0);
                Arrays.fill(maxs, Double.MIN_VALUE);
                Arrays.fill(mins, Double.MAX_VALUE);
                historicalTimeStart.set(currentTime.minusSeconds(MAX_SECONDS_PAST-1));
            }
        } else if (seconds >= MAX_SECONDS_PAST) {
            double[] newSums = new double[MAX_SECONDS_PAST];
            int[] newCounts = new int[MAX_SECONDS_PAST];
            Double[] newMaxs = new Double[MAX_SECONDS_PAST];
            Double[] newMins = new Double[MAX_SECONDS_PAST];
            Arrays.fill(newMaxs, Double.MIN_VALUE);
            Arrays.fill(newMins, Double.MAX_VALUE);

            int indexToStart = (int) seconds-MAX_SECONDS_PAST+1;
            int lastIndex = sums.length-indexToStart;
            System.arraycopy(sums, indexToStart, newSums, 0, lastIndex);
            System.arraycopy(count, indexToStart, newCounts, 0, lastIndex);
            System.arraycopy(maxs, indexToStart, newMaxs, 0, lastIndex);
            System.arraycopy(mins, indexToStart, newMins, 0, lastIndex);
            synchronized (lock) {
                sums = newSums;
                count = newCounts;
                maxs = newMaxs;
                mins = newMins;
                historicalTimeStart.updateAndGet(d -> d.plusSeconds(indexToStart));
            }
        }
    }

    private double format(Double value) {
        return Double.parseDouble(FORMATTER.format(value));
    }

    private double getAverage() {
        int totalCount = IntStream.of(count).sum();
        return totalCount != 0 ? format(DoubleStream.of(sums).sum()/totalCount) : 0;
    }

    private double getMax() {
        double maxValue = Collections.max(Arrays.asList(maxs));
        return maxValue != Double.MIN_VALUE ? maxValue : 0;
    }

    private double getMin() {
        double minValue = Collections.min(Arrays.asList(mins));
        return minValue != Double.MAX_VALUE ? minValue : 0;
    }

    private LocalDateTime toLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), TimeZone.getDefault().toZoneId());
    }
}
