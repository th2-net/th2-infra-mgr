/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr.util;

import java.util.List;
import java.util.Map;

public class Strings {

    public static boolean stringify(Object o) {
        if (o instanceof Map) {
            stringify((Map) o);
            return true;
        } else if (o instanceof List) {
            stringify((List) o);
            return true;
        }
        return false;
    }

    public static void stringify(List<Object> list) {
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (!stringify(value)) {
                list.set(i, value.toString());
            }
        }
    }

    public static void stringify(Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object value = e.getValue();
            if (!stringify(value) && value != null) {
                e.setValue(value.toString());
            }
        }
    }

    public static String formatHash(String hash) {
        return "[" + (hash == null ? "no-hash" : hash.substring(0, 8)) + "]";
    }
}
