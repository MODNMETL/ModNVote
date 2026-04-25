package com.modnmetl.modnvote.util;

import java.util.ArrayList;
import java.util.List;

public final class TextWrapUtil {

    private TextWrapUtil() {}

    public static List<String> wrap(String text, int maxLineLength) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxLineLength) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}