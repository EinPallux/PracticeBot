package com.pallux.practicebot.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling colors including HEX colors and gradients
 */
public class ColorUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[a-fA-F0-9]{6}):(#[a-fA-F0-9]{6})>(.*?)</gradient>");

    /**
     * Translates color codes and HEX colors in a string
     * Supports both legacy color codes (&) and HEX colors (#RRGGBB)
     * Also supports gradient format: <gradient:#START:#END>text</gradient>
     */
    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // First, handle gradients
        text = processGradients(text);

        // Then use MiniMessage to parse everything else
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Process gradient tags in text
     */
    private static String processGradients(String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String startColor = matcher.group(1);
            String endColor = matcher.group(2);
            String content = matcher.group(3);

            String gradientText = applyGradient(content, startColor, endColor);
            matcher.appendReplacement(result, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Apply gradient to text
     */
    private static String applyGradient(String text, String startHex, String endHex) {
        if (text.isEmpty()) return "";

        Color startColor = hexToColor(startHex);
        Color endColor = hexToColor(endHex);

        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(c);
                continue;
            }

            float ratio = (float) i / (float) (length - 1);
            Color blended = blendColors(startColor, endColor, ratio);
            result.append(colorToHex(blended)).append(c);
        }

        return result.toString();
    }

    /**
     * Convert HEX string to Color
     */
    private static Color hexToColor(String hex) {
        hex = hex.replace("#", "");
        return new Color(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        );
    }

    /**
     * Convert Color to HEX string
     */
    private static String colorToHex(Color color) {
        return String.format("<#%02x%02x%02x>", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Blend two colors
     */
    private static Color blendColors(Color start, Color end, float ratio) {
        int r = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
        int g = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
        int b = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));
        return new Color(r, g, b);
    }

    /**
     * Strip all color codes from text
     */
    public static String stripColor(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceAll("<[^>]*>", "").replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Convert Component to plain text
     */
    public static String toPlainText(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component).replaceAll("§[0-9a-fk-or]", "");
    }
}