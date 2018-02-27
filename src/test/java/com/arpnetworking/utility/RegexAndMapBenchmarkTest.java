package com.arpnetworking.utility;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Collections;
import java.util.regex.Pattern;

public class RegexAndMapBenchmarkTest {
    @Rule
    public TestRule benchmarkRun = new BenchmarkRule();

    @BenchmarkOptions(benchmarkRounds = 2000000, warmupRounds = 50000)
    @Test
    public void testRegexAndMap() {
        final String result = RegexAndMapReplacer.replaceAll(PATTERN.matcher(INPUT), INPUT, REPLACE, Collections.emptyMap());
    }

    @BenchmarkOptions(benchmarkRounds = 2000000, warmupRounds = 50000)
    @Test
    public void testRegex() {
        final String result = PATTERN.matcher(INPUT).replaceAll(REPLACE);
    }

    private static final String REPLACE = "this is a ${g1} pattern called ${g2}";
    private static Pattern PATTERN = Pattern.compile("(?<g1>test)/pattern/(?<g2>foo)");
    private static final String INPUT = "test/pattern/foo";
}
