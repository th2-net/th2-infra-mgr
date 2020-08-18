package com.exactpro.th2.schema.schemaeditorbe.util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Stringifier {

    public static boolean stringify(Object o) {
        if (o instanceof Map) {
            stringify((Map) o);
            return true;
        }
        else
        if (o instanceof List) {
            stringify((List) o);
            return true;
        }
        return false;
    }

    public static void stringify(List<Object> list) {
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (!stringify(value))
                list.set(i, value.toString());
        }
    }

    public static void stringify(Map<String, Object> map) {
        Iterator<Map.Entry<String, Object>> i = map.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Object> e = i.next();
            Object value = e.getValue();
            if (!stringify(value)) {
                e.setValue(value.toString());
            }
        }
    }
}
