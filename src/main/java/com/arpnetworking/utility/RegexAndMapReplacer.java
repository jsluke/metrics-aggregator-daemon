/**
 * Copyright 2018 Inscope Metrics, Inc
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
package com.arpnetworking.utility;

import scala.Int;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * A regex replacement utility that can also replace tokens not found in the regex.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public final class RegexAndMapReplacer {
    /**
     * Replaces all instances of $n (where n is 0-9) with regex match groups, ${var} with regex capture group or variable (from map) 'var'.
     *
     * @param matcher matcher to use
     * @param input input string to match against
     * @param replace replacement string
     * @param variables map of variables to include
     * @return
     */
    public static String replaceAll(final Matcher matcher, final String input, final String replace, final Map<String, String> variables) {

        matcher.reset();
        boolean found = matcher.find();
        if (found) {
            final StringBuilder builder = new StringBuilder();
            int lastMatchedIndex = 0;
            do {
                builder.append(input.substring(lastMatchedIndex, matcher.start()));
                lastMatchedIndex = matcher.end();
                appendReplacement(matcher, replace, builder, variables);
                found = matcher.find();
            } while (found);
            // Append left-over string after the matches
            if (lastMatchedIndex < input.length() - 1) {
                builder.append(input.substring(lastMatchedIndex, input.length()));
            }
            return builder.toString();
        }
        return input;
    }

    private static void appendReplacement(final Matcher matcher, final String replacement, final StringBuilder replacementBuilder,
                                          final Map<String, String> variables) {
        final StringBuilder tokenStringBuilder = new StringBuilder();
        boolean inEscape = false;
        boolean readingReplaceToken = false;
        boolean inReplaceBrackets = false;
        boolean replaceTokenNumeric = true; // assume token is numeric, any non-numeric character will set this to false
        for (int x = 0; x < replacement.length(); x++) {
            final char c = replacement.charAt(x);
            if (!inEscape && c == '\\') {
                inEscape = true;
                continue;
            }
            if (inEscape) {
                final StringBuilder builder;
                if (readingReplaceToken) {
                    builder = tokenStringBuilder;
                } else {
                    builder = replacementBuilder;
                }
                if (c == '\\' || c == '$' || c == '}') {
                    builder.append(c);
                    inEscape = false;
                } else {
                    throw new IllegalArgumentException("Improperly escaped '" + String.valueOf(c) + "' in replacement at col " + x + ": " + replacement);
                }
            } else if (readingReplaceToken) {
                if (c == '{') {
                    inReplaceBrackets = true;
                    continue;
                } else if (c == '}' && inReplaceBrackets) {
                        final String replaceToken = tokenStringBuilder.toString();
                        replacementBuilder.append(getReplacement(matcher, replaceToken, replaceTokenNumeric, variables));
                        tokenStringBuilder.setLength(0);
                        inReplaceBrackets = false;
                        readingReplaceToken = false;
                        continue;
                } else if (!Character.isDigit(c)) {
                    if (inReplaceBrackets) {
                        replaceTokenNumeric = false;
                        tokenStringBuilder.append(c);
                    } else {
                        final String replaceToken = tokenStringBuilder.toString();
                        if (replaceToken.isEmpty()) {
                            throw new IllegalArgumentException("Non-numeric replacements must be of the form ${val}. Missing '{' at col "
                                    + x + ": " + replacement);
                        }
                        replacementBuilder.append(getReplacement(matcher, replaceToken, replaceTokenNumeric, variables));
                        tokenStringBuilder.setLength(0);
                        readingReplaceToken = false;
                        x--; // We can't process the character because we are no longer in the numeric group syntax, we need to process the
                             // $n replacement and then evaluate this character again.
                        continue;
                    }
                } else {
                    tokenStringBuilder.append(c);
                }
            } else {
                if (c == '$') {
                    readingReplaceToken = true;
                } else {
                    replacementBuilder.append(c);
                }
            }
        }
        if (tokenStringBuilder.length() > 0 && replaceTokenNumeric) {
            final String replaceToken = tokenStringBuilder.toString();
            replacementBuilder.append(getReplacement(matcher, replaceToken, true, variables));
        }
    }

    private static String getReplacement(final Matcher matcher, final String replaceToken, final boolean numeric,
                                         final Map<String, String> variables) {
        if (numeric) {
            final int replaceGroup = Integer.parseInt(replaceToken);
            return matcher.group(replaceGroup);
        } else {
            try {
                return matcher.group(replaceToken);
            } catch (final IllegalArgumentException e) { // No group with this name
                return variables.getOrDefault(replaceToken, "");
            }
        }
    }

    private RegexAndMapReplacer () { }
}
