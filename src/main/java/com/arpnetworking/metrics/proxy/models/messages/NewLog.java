/**
 * Copyright 2014 Groupon.com
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

package com.arpnetworking.metrics.proxy.models.messages;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;

import java.nio.file.Path;

/**
 * Message class to inform clients of a new log.
 *
 * @author Mohammed Kamel (mkamel at groupon dot com)
 */
@Loggable
public final class NewLog {
    /**
     * Public constructor.
     *
     * @param log name of the new log path
     */
    public NewLog(final Path log) {
        _log = log;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Log", _log)
                .toString();
    }

    private final Path _log;

    public Path getLog() {
        return _log;
    }
}
