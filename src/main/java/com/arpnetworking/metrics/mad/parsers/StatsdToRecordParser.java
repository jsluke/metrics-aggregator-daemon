/**
 * Copyright 2017 Inscope Metrics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.metrics.mad.parsers;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.metrics.common.parsers.Parser;
import com.arpnetworking.metrics.common.parsers.exceptions.ParsingException;
import com.arpnetworking.metrics.mad.model.DefaultMetric;
import com.arpnetworking.metrics.mad.model.DefaultRecord;
import com.arpnetworking.metrics.mad.model.Record;
import com.arpnetworking.tsdcore.model.MetricType;
import com.arpnetworking.tsdcore.model.Quantity;
import com.arpnetworking.tsdcore.model.Unit;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Parses Statsd data as a {@link Record}.
 *
 * There are two Important differences compared to traditional statsd server
 * implementations. First, each counter or meter value, which is a delta,
 * is treated as a sample for that metric. Second, sets are not supported at
 * this time because they would need to be pushed down to our bucketing and
 * aggregation layer as a first-class metric type.
 *
 * Except for the differences described above this parser supports both the
 * traditional and Data Dog variants of the statsd protocol as defined here:
 *
 * https://github.com/b/statsd_spec
 * https://docs.datadoghq.com/guides/dogstatsd/
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class StatsdToRecordParser implements Parser<List<Record>, ByteBuffer> {

    /**
     * Parses a statsd datagram.
     *
     * @param datagram a datagram
     * @return A list of {@link DefaultRecord.Builder}
     * @throws ParsingException if the datagram is not parsable as statsd formatted message
     */
    public List<Record> parse(final ByteBuffer datagram) throws ParsingException {
        // CHECKSTYLE.OFF: IllegalInstantiation - This is the recommended way
        final String datagramAsString = new String(datagram.array(), Charsets.UTF_8);
        final ImmutableList.Builder<Record> recordListBuilder = ImmutableList.builder();
        for (final String line : LINE_SPLITTER.split(datagramAsString)) {
            // CHECKSTYLE.ON: IllegalInstantiation
            final Matcher matcher = STATSD_PATTERN.matcher(line);
            if (!matcher.matches()) {
                throw new ParsingException("Invalid statsd line", line.getBytes(Charsets.UTF_8));
            }

            // Parse the name
            final String name = parseName(datagram, matcher.group("NAME"));

            // Parse the _metricType
            final StatsdType type = parseStatsdType(datagram, matcher.group("TYPE"));

            // Parse the value
            final Number value = parseValue(datagram, matcher.group("VALUE"), type);

            // Parse the sample rate
            final Optional<Double> sampleRate = parseSampleRate(datagram, matcher.group("SAMPLERATE"), type);

            // Parse the tags
            final ImmutableMap<String, String> annotations = parseTags(matcher.group("TAGS"));

            // Enforce sampling
            if (sampleRate.isPresent() && sampleRate.get().compareTo(1.0) != 0) {
                if (sampleRate.get().compareTo(0.0) == 0) {
                    return Collections.emptyList();
                }
                if (Double.compare(_randomSupplier.get().nextDouble(), sampleRate.get()) > 0) {
                    return Collections.emptyList();
                }
            }

            recordListBuilder.add(createRecord(name, value, type, annotations));
        }

        return recordListBuilder.build();
    }

    private StatsdType parseStatsdType(final ByteBuffer datagram, final @Nullable String statsdTypeAsString) throws ParsingException {
        @Nullable final StatsdType type = StatsdType.fromToken(statsdTypeAsString);
        if (type == null) {
            throw new ParsingException("Type not found or unsupported", datagram.array());
        }
        return type;
    }

    private String parseName(final ByteBuffer datagram, @Nullable final String name) throws ParsingException {
        if (Strings.isNullOrEmpty(name)) {
            throw new ParsingException("Name not found or empty", datagram.array());
        }
        return name;
    }

    private Number parseValue(
            final ByteBuffer datagram,
            @Nullable final String valueAsString,
            final StatsdType type) throws ParsingException {
        try {
            if (Objects.equals(StatsdType.METERS, type) && valueAsString == null) {
                return 1;
            } else if (valueAsString == null) {
                throw new ParsingException("Value required but not specified", datagram.array());
            } else {
                return NUMBER_FORMAT.get().parse(valueAsString);
            }
        } catch (final ParseException e) {
            throw new ParsingException("Value is not a number", datagram.array(), e);
        }
    }

    private ImmutableMap<String, String> parseTags(@Nullable final String taqsAsString) {
        final ImmutableMap.Builder<String, String> annotations = ImmutableMap.builder();
        if (null != taqsAsString) {
            for (final String keyValue : taqsAsString.split(",")) {
                final int pivot = keyValue.indexOf(':');
                annotations.put(
                        keyValue.substring(0, pivot),
                        keyValue.substring(pivot + 1));
            }
        }
        return annotations.build();
    }

    private Optional<Double> parseSampleRate(
            final ByteBuffer datagram,
            @Nullable final String sampleRateAsString,
            final StatsdType type) throws ParsingException {
        try {
            if (sampleRateAsString != null) {
                if (SAMPLED_STATSD_TYPES.contains(type)) {
                    final Double sampleRate = Double.valueOf(sampleRateAsString);
                    if (sampleRate.compareTo(1.0) > 0 || sampleRate.compareTo(0.0) < 0) {
                        throw new ParsingException("Invalid sample rate", datagram.array());
                    }
                    return Optional.of(sampleRate);
                } else {
                    throw new ParsingException("Sample rate not support for this _metricType", datagram.array());
                }
            } else {
                return Optional.empty();
            }
        } catch (final NumberFormatException e) {
            throw new ParsingException("Sample rate is not a number", datagram.array(), e);
        }
    }

    private Record createRecord(
            final String name,
            final Number value,
            final StatsdType type,
            final ImmutableMap<String, String> annotations) {
        return ThreadLocalBuilder.build(
                DefaultRecord.Builder.class,
                b1 -> b1.setDimensions(annotations)
                        .setId(UUID.randomUUID().toString())
                        .setMetrics(ImmutableMap.of(
                                name,
                                ThreadLocalBuilder.build(
                                        DefaultMetric.Builder.class,
                                        b2 -> b2.setValues(
                                                ImmutableList.of(
                                                        ThreadLocalBuilder.build(
                                                                Quantity.Builder.class,
                                                                b3 -> b3.setValue(value.doubleValue())
                                                                        .setUnit(type.getUnit()))))
                                        .setType(type.getMetricType()))))
                        .setTime(new DateTime(_clock.millis(), DateTimeZone.UTC)));
    }

    /**
     * Public constructor.
     */
    public StatsdToRecordParser() {
        _clock = Clock.systemUTC();
        _randomSupplier = ThreadLocalRandom::current;
    }

    /* package private */ StatsdToRecordParser(
            final Clock clock,
            final Supplier<Random> randomSupplier) {
        _clock = clock;
        _randomSupplier = randomSupplier;
    }

    private final Clock _clock;
    private final Supplier<Random> _randomSupplier;

    private static final ImmutableSet<StatsdType> SAMPLED_STATSD_TYPES = ImmutableSet.of(
            StatsdType.COUNTER,
            StatsdType.HISTOGRAM,
            StatsdType.TIMER);
    private static final Splitter LINE_SPLITTER = Splitter.on('\n').omitEmptyStrings();
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT = ThreadLocal.withInitial(NumberFormat::getInstance);
    private static final Pattern STATSD_PATTERN = Pattern.compile(
            "^(?<NAME>[^:@|]+):(?<VALUE>[^|]+)\\|(?<TYPE>[^|]+)(\\|@(?<SAMPLERATE>[^|]+))?(\\|#(?<TAGS>.+))?$");

    private enum StatsdType {
        COUNTER("c", MetricType.COUNTER, null),
        GAUGE("g", MetricType.GAUGE, null),
        HISTOGRAM("h", MetricType.TIMER, null),
        METERS("m", MetricType.COUNTER, null),
        // NOTE: Sets are not supported as per class Javadoc.
        //SET("s", null),
        TIMER("ms", MetricType.TIMER, Unit.MILLISECOND);

        private final String _token;
        private final MetricType _metricType;
        private @Nullable final Unit _unit;

        private static final Map<String, StatsdType> TOKEN_TO_TYPE = Maps.newHashMap();

        /* package private */ StatsdType(
                final String token,
                final MetricType metricType,
                @Nullable final Unit unit) {
            _token = token;
            _metricType = metricType;
            _unit = unit;
        }

        public MetricType getMetricType() {
            return _metricType;
        }

        public @Nullable Unit getUnit() {
            return _unit;
        }

        public static StatsdType fromToken(final String token) {
            return TOKEN_TO_TYPE.get(token);
        }

        static {
            for (final StatsdType statsdType : values()) {
                TOKEN_TO_TYPE.put(statsdType._token, statsdType);
            }
        }
    }}
