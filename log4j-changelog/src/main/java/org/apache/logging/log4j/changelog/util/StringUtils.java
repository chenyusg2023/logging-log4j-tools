/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.changelog.util;

import edu.umd.cs.findbugs.annotations.Nullable;

public final class StringUtils {

    private StringUtils() {}

    @Nullable
    public static String trimNullable(@Nullable final String input) {
        return input != null ? input.trim() : null;
    }

    public static boolean isBlank(@Nullable final String input) {
        return input == null || input.matches("\\s*");
    }

    public static String repeat(final String input, final int count) {
        if (count < 0) {
            final String message = String.format("was expecting `count >= 0`, found: %d", count);
            throw new IllegalArgumentException(message);
        }
        final int length = Math.multiplyExact(input.length(), count);
        final StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < count; i++) {
            stringBuilder.append(input);
        }
        return stringBuilder.toString();
    }

}
