package com.arpnetworking.utility;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

public class RegexAndMapReplacerTest {
    @Test
    public void testNoMatch() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "wont match";
        final String replace = "$0";
        final String expected = "wont match";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEscape() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "test";
        final String replace = "${\\avariable}"; // \a is an invalid escape sequence
        final String result = RegexAndMapReplacer.replaceAll(pattern.matcher(input), input, replace, Collections.emptyMap());
    }

    @Test
    public void testNumericWithClosingCurly() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "test";
        final String replace = "$0}";
        final String expected = "test}";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidReplacementTokenMissingOpen() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "test";
        final String replace = "$variable"; // replacement variable has no {
        final String result = RegexAndMapReplacer.replaceAll(pattern.matcher(input), input, replace, Collections.emptyMap());
    }

    @Test
    public void testGroup0Replace() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "test";
        final String replace = "$0";
        final String expected = "test";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test
    public void testSingleMatchFullStaticReplace() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "test";
        final String replace = "replace";
        final String expected = "replace";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test
    public void testSingleMatchPartialStaticReplace() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "test string";
        final String replace = "replace";
        final String expected = "replace string";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test
    public void testSingleMatchPartialStaticReplacePrefix() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "some test string";
        final String replace = "replace";
        final String expected = "some replace string";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test
    public void testSingleMatchPartialMultipleGroupNumberReplace() {
        final Pattern pattern = Pattern.compile("(test)/pattern/(foo)");
        final String input = "test/pattern/foo";
        final String replace = "this is a $1 pattern called $2";
        final String expected = "this is a test pattern called foo";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test
    public void testSingleMatchPartialMultipleGroupNameReplace() {
        final Pattern pattern = Pattern.compile("(?<g1>test)/pattern/(?<g2>foo)");
        final String input = "test/pattern/foo";
        final String replace = "this is a ${g1} pattern called ${g2}";
        final String expected = "this is a test pattern called foo";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test
    public void testSingleMatchPartialMultipleVariableReplace() {
        final Pattern pattern = Pattern.compile("test/pattern/foo");
        final String input = "test/pattern/foo";
        final String replace = "this is a ${g1} pattern called ${g2}";
        final String expected = "this is a test pattern called foo";
        testExpression(pattern, input, replace, expected, ImmutableMap.of("g1", "test", "g2", "foo"));
    }

    @Test
    public void testSingleMatchPartialMultipleVariableWithEscapeReplace() {
        final Pattern pattern = Pattern.compile("test/pattern/foo");
        final String input = "test/pattern/foo";
        final String replace = "this is a ${g1} pattern \\\\called\\\\ ${g2}";
        final String expected = "this is a test pattern \\called\\ foo";
        testExpression(pattern, input, replace, expected, ImmutableMap.of("g1", "test", "g2", "foo"));
    }

    @Test
    public void testSingleMatchPartialMultipleVariableWithEscapeTokenReplace() {
        final Pattern pattern = Pattern.compile("test/pattern/foo");
        final String input = "test/pattern/foo";
        final String replace = "this is a ${\\\\g1} pattern called ${g2}";
        final String expected = "this is a test pattern called foo";
        testExpression(pattern, input, replace, expected, ImmutableMap.of("\\g1", "test", "g2", "foo"));
    }

    @Test
    public void testSingleMatchPartialMultipleGroupNameOverridesVariablesReplace() {
        final Pattern pattern = Pattern.compile("(?<g1>test)/pattern/(?<g2>foo)");
        final String input = "test/pattern/foo";
        final String replace = "this is a ${g1} pattern called ${g2}";
        final String expected = "this is a test pattern called foo";
        testExpression(pattern, input, replace, expected, ImmutableMap.of("g1", "bad", "g2", "value"));
    }

    @Test
    public void testMultipleMatchFullStaticReplace() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "testtest";
        final String replace = "replace";
        final String expected = "replacereplace";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    @Test
    public void testMultipleMatchPartialStaticReplace() {
        final Pattern pattern = Pattern.compile("test");
        final String input = "test string test";
        final String replace = "replace";
        final String expected = "replace string replace";
        testExpression(pattern, input, replace, expected, Collections.emptyMap());
    }

    private void testExpression(final Pattern pattern, final String input, final String replace, final String expected,
            final Map<String, String> variables) {
        final String result = RegexAndMapReplacer.replaceAll(pattern.matcher(input), input, replace, variables);
        Assert.assertEquals(expected, result);
        try {
            final String stockResult = pattern.matcher(input).replaceAll(replace);
            Assert.assertEquals(expected, stockResult);
        } catch (final IllegalArgumentException ignored) {}
    }
}
