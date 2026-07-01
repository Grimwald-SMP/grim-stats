package dev.grimstats.http.og;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaInjectorTest {

    private static final String HTML = """
            <!doctype html>
            <html lang="en"><head>
            <meta charset="UTF-8" />
            <title>GrimStats</title>
            <meta name="description" content="default" />
            <meta property="og:title" content="default" />
            <meta name="twitter:card" content="summary_large_image" />
            </head><body><div id="root"></div></body></html>
            """;

    @Test
    void injectsResolvedTagsAndReplacesDefaults() {
        MetaInjector.Meta meta = new MetaInjector.Meta(
                "ThisIsRoc • GrimStats", "Online on Server", "http://host:8765/og/player/u.png",
                "http://host:8765/players/u", "profile");
        String out = MetaInjector.inject(HTML, meta);

        assertTrue(out.contains("<title>ThisIsRoc • GrimStats</title>"));
        assertTrue(out.contains("property=\"og:image\" content=\"http://host:8765/og/player/u.png\""));
        assertTrue(out.contains("property=\"og:type\" content=\"profile\""));
        assertTrue(out.contains("name=\"twitter:card\" content=\"summary_large_image\""));
        // The default title/description/og tags must not survive (no duplicates).
        assertFalse(out.contains(">GrimStats</title>"));
        assertFalse(out.contains("content=\"default\""));
        assertEquals(1, count(out, "<title>"), "exactly one title element");
        assertEquals(1, count(out, "property=\"og:title\""));
    }

    @Test
    void escapesHtmlSpecialCharsInValues() {
        MetaInjector.Meta meta = new MetaInjector.Meta(
                "a<b>&\"c", "d & e", "http://h/x.png", "http://h/", "website");
        String out = MetaInjector.inject(HTML, meta);
        assertTrue(out.contains("<title>a&lt;b&gt;&amp;&quot;c</title>"));
        assertTrue(out.contains("content=\"d &amp; e\""));
        // No raw unescaped injection of the angle brackets from the value.
        assertFalse(out.contains("a<b>"));
    }

    @Test
    void leavesDocumentUntouchedWithoutHead() {
        String noHead = "<html><body>hi</body></html>";
        MetaInjector.Meta meta = new MetaInjector.Meta("t", "d", "i", "u", "website");
        assertEquals(noHead, MetaInjector.inject(noHead, meta));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
