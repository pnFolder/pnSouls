package ru.privatenull.pnsouls.support.text;

/**
 * Turns pnFolder color markup into plain legacy text ({@code §} codes). It uses no Adventure or MiniMessage,
 * so it behaves the same from Minecraft 1.16.5 on, where those classes are not part of the server.
 * Understands {@code &c}, {@code &l} and the like, {@code &#RRGGBB} and the {@code &x&R&R&G&G&B&B} form.
 */
public final class ColorUtil {

    private static final char SECTION = '§';

    private ColorUtil() {
    }

    /** Legacy text with every color marker converted; text without markers is returned as it is. */
    public static String colorize(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length();) {
            char marker = value.charAt(index);
            if (marker != '&' && marker != SECTION) {
                result.append(marker);
                index++;
                continue;
            }
            if (isAmpersandHex(value, index)) {
                appendHex(result, value.substring(index + 2, index + 8));
                index += 8;
                continue;
            }
            if (isLegacyHex(value, index)) {
                StringBuilder hex = new StringBuilder(6);
                for (int part = index + 3; part <= index + 13; part += 2) hex.append(value.charAt(part));
                appendHex(result, hex.toString());
                index += 14;
                continue;
            }
            if (index + 1 < value.length() && isCode(value.charAt(index + 1))) {
                result.append(SECTION).append(Character.toLowerCase(value.charAt(index + 1)));
                index += 2;
                continue;
            }
            result.append(marker);
            index++;
        }
        return result.toString();
    }

    /** A color prefix followed by a value; the prefix is converted on its own so digits in the value stay text. */
    public static String paint(String color, Object value) {
        return colorize(color) + value;
    }

    private static void appendHex(StringBuilder result, String hex) {
        result.append(SECTION).append('x');
        for (int index = 0; index < 6; index++) result.append(SECTION).append(Character.toLowerCase(hex.charAt(index)));
    }

    private static boolean isAmpersandHex(String value, int index) {
        if (value.charAt(index) != '&' || index + 8 > value.length() || value.charAt(index + 1) != '#') return false;
        for (int part = index + 2; part < index + 8; part++) {
            if (!isHex(value.charAt(part))) return false;
        }
        return true;
    }

    private static boolean isLegacyHex(String value, int index) {
        if (index + 13 >= value.length() || Character.toLowerCase(value.charAt(index + 1)) != 'x') return false;
        for (int part = index + 2; part <= index + 12; part += 2) {
            if ((value.charAt(part) != '&' && value.charAt(part) != SECTION)
                    || !isHex(value.charAt(part + 1))) return false;
        }
        return true;
    }

    private static boolean isCode(char value) {
        char code = Character.toLowerCase(value);
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f' || code >= 'k' && code <= 'o' || code == 'r';
    }

    private static boolean isHex(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }
}
