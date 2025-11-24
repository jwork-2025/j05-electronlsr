package com.gameengine.recording;

import java.util.ArrayList;
import java.util.List;

public final class RecordingJson {
    private RecordingJson() {}

    public static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        if (c < 0) return null;
        int start = c + 1;
        
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        if (start >= json.length()) return null;
        
        int end = start;
        char firstChar = json.charAt(start);
        
        if (firstChar == '[' || firstChar == '{') {
            // Handle Array or Object
            int depth = 0;
            for (; end < json.length(); end++) {
                char ch = json.charAt(end);
                if (ch == '[' || ch == '{') depth++;
                else if (ch == ']' || ch == '}') {
                    depth--;
                    if (depth == 0) {
                        end++; // Include the closing bracket
                        return json.substring(start, end);
                    }
                }
            }
        } else if (firstChar == '"') {
            // Handle String
            end++;
            while (end < json.length()) {
                if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') {
                    end++;
                    return json.substring(start, end);
                }
                end++;
            }
        } else {
            // Handle Number/Boolean/Null (read until comma or closing brace)
            for (; end < json.length(); end++) {
                char ch = json.charAt(end);
                if (ch == ',' || ch == '}') {
                    return json.substring(start, end).trim();
                }
            }
        }
        return null;
    }

    public static String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length()-1);
        }
        return s;
    }

    public static double parseDouble(String s) {
        if (s == null) return 0.0;
        try { return Double.parseDouble(stripQuotes(s)); } catch (Exception e) { return 0.0; }
    }

    public static String[] splitTopLevel(String arr) {
        List<String> out = new ArrayList<>();
        int depth = 0; int start = 0;
        for (int i = 0; i < arr.length(); i++) {
            char ch = arr.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') depth--;
            else if (ch == ',' && depth == 0) {
                out.add(arr.substring(start, i));
                start = i + 1;
            }
        }
        if (start < arr.length()) out.add(arr.substring(start));
        return out.stream().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    public static String extractArray(String json, int startIdx) {
        int i = startIdx;
        if (i >= json.length() || json.charAt(i) != '[') return "";
        int depth = 1;
        int begin = i + 1;
        i++;
        for (; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(begin, i);
                }
            }
        }
        return "";
    }
}
