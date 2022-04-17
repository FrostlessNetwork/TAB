package me.neznamy.tab.api.chat.rgb.format;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/**
 * Formatter for &lt;#RRGGBB>
 */
public class KyoriFormat implements RGBFormatter {

    private final Pattern pattern = Pattern.compile("<#[0-9a-fA-F]{6}>");

    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public String reformat(String text) {
        String format = text;
        try {
            format = LegacyComponentSerializer.legacySection().serialize(mm.deserialize(text));
        } catch (Exception e) {
            //
        }
        return format;
    }
}