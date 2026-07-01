package dev.grimstats.http.og;

import java.util.regex.Pattern;

/**
 * Rewrites the SPA's {@code index.html} {@code <head>} with per-route share metadata (Open Graph +
 * Twitter card) so links unfurl with a title, description and preview image on Discord and similar.
 *
 * <p>This must happen server-side: link crawlers do not execute the SPA's JavaScript, so any tags
 * React sets at runtime are invisible to them. Existing default tags are stripped first so re-serving
 * never accumulates duplicates.
 */
public final class MetaInjector {

    private MetaInjector() {
    }

    /** The resolved share metadata for one page. */
    public record Meta(String title, String description, String imageUrl, String pageUrl, String type) {
    }

    // Matches the <title> element and any existing description / og: / twitter: meta tags.
    private static final Pattern EXISTING = Pattern.compile(
            "[ \\t]*<title>.*?</title>\\s*"
                    + "|[ \\t]*<meta[^>]*(?:property=\"og:[^\"]*\"|name=\"twitter:[^\"]*\"|name=\"description\")[^>]*>\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static String inject(String html, Meta meta) {
        String cleaned = EXISTING.matcher(html).replaceAll("");
        int idx = indexOfIgnoreCase(cleaned, "</head>");
        if (idx < 0) {
            return html; // Malformed document; leave it untouched rather than risk corrupting it.
        }
        return cleaned.substring(0, idx) + build(meta) + cleaned.substring(idx);
    }

    private static String build(Meta m) {
        StringBuilder b = new StringBuilder();
        b.append("    <title>").append(esc(m.title())).append("</title>\n");
        b.append(tag("name", "description", m.description()));
        b.append(prop("og:site_name", "GrimStats"));
        b.append(prop("og:type", m.type()));
        b.append(prop("og:title", m.title()));
        b.append(prop("og:description", m.description()));
        b.append(prop("og:url", m.pageUrl()));
        b.append(prop("og:image", m.imageUrl()));
        b.append(prop("og:image:width", "1200"));
        b.append(prop("og:image:height", "630"));
        b.append(tag("name", "twitter:card", "summary_large_image"));
        b.append(tag("name", "twitter:title", m.title()));
        b.append(tag("name", "twitter:description", m.description()));
        b.append(tag("name", "twitter:image", m.imageUrl()));
        return b.toString();
    }

    private static String prop(String property, String content) {
        return "    <meta property=\"" + property + "\" content=\"" + esc(content) + "\" />\n";
    }

    private static String tag(String attr, String name, String content) {
        return "    <meta " + attr + "=\"" + name + "\" content=\"" + esc(content) + "\" />\n";
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
