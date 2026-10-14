package com.cappycommunity.cappyguard.discord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, zero-dependency Discord Embed JSON Builder.
 */
public final class DiscordEmbed {

    private String title;
    private String description;
    private int color = 0x3498DB; // Default blue
    private String authorName;
    private String authorIconUrl;
    private String thumbnailUrl;
    private String footerText;
    private String footerIconUrl;
    private boolean timestamp = true;
    private final List<Field> fields = new ArrayList<>();

    public static class Field {
        public final String name;
        public final String value;
        public final boolean inline;

        public Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }

    public DiscordEmbed setTitle(String title) {
        this.title = title;
        return this;
    }

    public DiscordEmbed setDescription(String description) {
        this.description = description;
        return this;
    }

    public DiscordEmbed setColor(int color) {
        this.color = color;
        return this;
    }

    public DiscordEmbed setColorHex(String hex) {
        if (hex != null && !hex.isEmpty()) {
            try {
                String cleanHex = hex.replace("#", "");
                this.color = Integer.parseInt(cleanHex, 16);
            } catch (NumberFormatException ignored) {}
        }
        return this;
    }

    public DiscordEmbed setAuthor(String name, String iconUrl) {
        this.authorName = name;
        this.authorIconUrl = iconUrl;
        return this;
    }

    public DiscordEmbed setThumbnail(String url) {
        this.thumbnailUrl = url;
        return this;
    }

    public DiscordEmbed setFooter(String text, String iconUrl) {
        this.footerText = text;
        this.footerIconUrl = iconUrl;
        return this;
    }

    public DiscordEmbed setTimestamp(boolean timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public DiscordEmbed addField(String name, String value, boolean inline) {
        if (name != null && value != null) {
            this.fields.add(new Field(name, value, inline));
        }
        return this;
    }

    /**
     * Serializes this embed into Discord-compatible JSON payload string.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");

        if (title != null) {
            sb.append("\"title\":").append(escape(title)).append(",");
        }
        if (description != null) {
            sb.append("\"description\":").append(escape(description)).append(",");
        }
        sb.append("\"color\":").append(color).append(",");

        if (authorName != null) {
            sb.append("\"author\":{");
            sb.append("\"name\":").append(escape(authorName));
            if (authorIconUrl != null) {
                sb.append(",\"icon_url\":").append(escape(authorIconUrl));
            }
            sb.append("},");
        }

        if (thumbnailUrl != null) {
            sb.append("\"thumbnail\":{");
            sb.append("\"url\":").append(escape(thumbnailUrl));
            sb.append("},");
        }

        if (footerText != null) {
            sb.append("\"footer\":{");
            sb.append("\"text\":").append(escape(footerText));
            if (footerIconUrl != null && !footerIconUrl.isEmpty()) {
                sb.append(",\"icon_url\":").append(escape(footerIconUrl));
            }
            sb.append("},");
        }

        if (timestamp) {
            sb.append("\"timestamp\":").append(escape(Instant.now().toString())).append(",");
        }

        if (!fields.isEmpty()) {
            sb.append("\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                Field f = fields.get(i);
                sb.append("{")
                  .append("\"name\":").append(escape(f.name)).append(",")
                  .append("\"value\":").append(escape(f.value)).append(",")
                  .append("\"inline\":").append(f.inline)
                  .append("}");
                if (i < fields.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append("],");
        }

        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
