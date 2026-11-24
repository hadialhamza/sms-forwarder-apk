package com.slothunter.sms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter engine dedicated to identifying incoming IVAC SMS messages.
 * Uses a resilient dual-condition check purely on SMS body text.
 */
public class IvacFilter {

    // Condition 1: Case-insensitive keyword check for "ivacbd"
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("(?i).*ivacbd.*");

    // Condition 2: Word-number density check for spelled-out numbers
    // Supports any non-letter delimiters including underscores '_', hyphens '-', spaces, etc.
    // without erroneously matching substrings inside words (e.g. 'someone' -> not matching 'one')
    private static final Pattern WORD_NUMBER_PATTERN = Pattern.compile(
            "(?i)(?:^|[^a-zA-Z])(zero|one|two|three|four|five|six|seven|eight|nine)(?=[^a-zA-Z]|$)"
    );

    // Minimum occurrences of spelled-out digits required to qualify as IVAC OTP
    private static final int WORD_NUMBER_THRESHOLD = 4;

    /**
     * Checks if the message body passes either of the IVAC filter conditions.
     *
     * @param body incoming SMS text body
     * @return true if accepted as IVAC SMS, false otherwise
     */
    public static boolean isIvacMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return false;
        }

        // Condition 1: Check for keyword "ivacbd"
        if (KEYWORD_PATTERN.matcher(body).matches()) {
            return true;
        }

        // Condition 2: Word-number density check
        int wordCount = countWordNumbers(body);
        return wordCount >= WORD_NUMBER_THRESHOLD;
    }

    /**
     * Counts the occurrences of spelled-out digit names in the text.
     */
    public static int countWordNumbers(String text) {
        if (text == null) {
            return 0;
        }
        Matcher matcher = WORD_NUMBER_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
