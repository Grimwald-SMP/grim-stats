package dev.grimstats.http.og;

import dev.grimstats.data.model.PlayerHighlights;
import dev.grimstats.data.model.PlayerStats;
import dev.grimstats.data.model.StatsSnapshot;
import dev.grimstats.data.model.WorldInfo;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Renders Open Graph preview cards (1200x630 PNG) for link unfurls on Discord and other platforms.
 *
 * <p>Discord's crawler needs a raster image at an absolute URL; it does not render SVG. These cards
 * are drawn off-screen with Java2D (no display needed) using the dashboard's warm "grimwald" palette,
 * so the mod stays self-contained with no image assets or external services to render a preview.
 * Player cards additionally layer the real Minecraft face fetched from mc-heads.net, falling back to
 * a deterministic colored initial (mirroring the dashboard's avatars) if that lookup fails.
 */
public final class OgImageRenderer {

    // Standard Open Graph size; Discord shows this as a large summary image.
    private static final int W = 1200;
    private static final int H = 630;

    private static final Color BG = new Color(0x17120e);
    private static final Color PANEL = new Color(0x201812);
    private static final Color CHIP = new Color(0x2a2019);
    private static final Color STROKE = new Color(0x3a2f26);
    private static final Color ACCENT = new Color(0xff7a00);
    private static final Color TEXT = new Color(0xf4efe9);
    private static final Color MUTED = new Color(0xa89e93);

    static {
        // A game server has no display; force off-screen rendering before any AWT class initializes.
        System.setProperty("java.awt.headless", "true");
    }

    private OgImageRenderer() {
    }

    public static byte[] player(PlayerStats p, WorldInfo world) throws IOException {
        BufferedImage img = base();
        Graphics2D g = graphics(img);
        try {
            int m = 64;
            panel(g);
            brand(g, m + 48, m + 62, world);

            int av = 260;
            int ax = W - m - 48 - av;
            int ay = (H - av) / 2 + 24;
            drawAvatar(g, p.name(), p.uuid(), ax, ay, av);

            int tx = m + 48;
            g.setColor(TEXT);
            g.setFont(bold(76));
            g.drawString(fit(g, p.name(), ax - tx - 40), tx, m + 200);

            g.setColor(MUTED);
            g.setFont(plain(30));
            g.drawString(p.online() ? "Online now" : "Player statistics", tx, m + 248);

            PlayerHighlights h = p.highlights();
            String[][] chips = {
                    {"Play time", formatTicks(h.playTime())},
                    {"Player kills", formatNumber(h.playerKills())},
                    {"Deaths", formatNumber(h.deaths())},
                    {"Blocks mined", formatNumber(h.blocksMined())},
            };
            int cw = 262, ch = 92, gap = 22;
            int cy = H - m - 48 - (2 * ch + gap);
            for (int i = 0; i < chips.length; i++) {
                int col = i % 2;
                int row = i / 2;
                chip(g, tx + col * (cw + gap), cy + row * (ch + gap), cw, ch, chips[i][0], chips[i][1]);
            }
            return encode(img);
        } finally {
            g.dispose();
        }
    }

    public static byte[] leaderboard(StatsSnapshot snap) throws IOException {
        return summary(snap, "Leaderboards", "Player rankings and top stats", true);
    }

    /** Site / homepage card. {@code showTop} is false for private dashboards so no player data leaks. */
    public static byte[] site(StatsSnapshot snap, boolean showTop) throws IOException {
        String server = snap.world().serverName();
        boolean named = server != null && !server.isBlank() && !"unknown".equalsIgnoreCase(server);
        return summary(snap, named ? server : "GrimStats", "World and player statistics", showTop);
    }

    private static byte[] summary(StatsSnapshot snap, String title, String subtitle, boolean showTop)
            throws IOException {
        BufferedImage img = base();
        Graphics2D g = graphics(img);
        try {
            int m = 64;
            int tx = m + 48;
            panel(g);
            brand(g, tx, m + 62, snap.world());

            g.setColor(TEXT);
            g.setFont(bold(72));
            g.drawString(fit(g, title, W - 2 * tx + m), tx, m + 190);
            g.setColor(MUTED);
            g.setFont(plain(30));
            g.drawString(subtitle, tx, m + 238);

            if (showTop) {
                List<PlayerStats> top = snap.players().stream()
                        .sorted(Comparator.comparingLong((PlayerStats p) -> p.highlights().playTime()).reversed())
                        .limit(3)
                        .toList();
                int y = m + 300;
                int rank = 1;
                for (PlayerStats p : top) {
                    if (p.highlights().playTime() <= 0) {
                        break;
                    }
                    g.setFont(bold(34));
                    g.setColor(ACCENT);
                    g.drawString("#" + rank, tx, y);
                    g.setColor(TEXT);
                    g.setFont(plain(34));
                    g.drawString(fit(g, p.name(), 620), tx + 84, y);
                    String v = formatTicks(p.highlights().playTime());
                    g.setColor(MUTED);
                    g.drawString(v, W - tx - g.getFontMetrics().stringWidth(v), y);
                    y += 50;
                    rank++;
                }
            }

            int count = snap.players().size();
            g.setColor(MUTED);
            g.setFont(plain(26));
            // Sits on the panel's bottom edge, clear of the third row above.
            g.drawString(count + (count == 1 ? " player tracked" : " players tracked"), tx, H - m - 34);
            return encode(img);
        } finally {
            g.dispose();
        }
    }

    // ----- drawing helpers -----------------------------------------------------------

    private static BufferedImage base() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, W, H);
        g.dispose();
        return img;
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void panel(Graphics2D g) {
        int m = 64;
        RoundRectangle2D r = new RoundRectangle2D.Float(m, m, W - 2f * m, H - 2f * m, 40, 40);
        g.setColor(PANEL);
        g.fill(r);
        g.setColor(STROKE);
        g.setStroke(new BasicStroke(2));
        g.draw(r);
        g.setColor(ACCENT);
        g.fill(new RoundRectangle2D.Float(m, m + 40f, 10, H - 2f * m - 80, 10, 10));
    }

    private static void brand(Graphics2D g, int x, int y, WorldInfo world) {
        g.setColor(ACCENT);
        g.setFont(bold(26));
        g.drawString("GRIMSTATS", x, y);
        int bw = g.getFontMetrics().stringWidth("GRIMSTATS");
        String server = world == null ? null : world.serverName();
        if (server != null && !server.isBlank() && !"unknown".equalsIgnoreCase(server)) {
            g.setColor(MUTED);
            g.setFont(plain(26));
            g.drawString("•  " + server, x + bw + 20, y);
        }
    }

    private static void chip(Graphics2D g, int x, int y, int w, int h, String label, String value) {
        g.setColor(CHIP);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 18, 18));
        g.setColor(MUTED);
        g.setFont(plain(22));
        g.drawString(label.toUpperCase(), x + 22, y + 36);
        g.setColor(TEXT);
        g.setFont(bold(38));
        g.drawString(value, x + 22, y + 76);
    }

    private static void drawAvatar(Graphics2D g, String name, String uuid, int x, int y, int size) {
        RoundRectangle2D box = new RoundRectangle2D.Float(x, y, size, size, 24, 24);
        BufferedImage face = fetchFace(name, size);
        if (face != null) {
            Shape old = g.getClip();
            g.setClip(box);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(face, x, y, size, size, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setClip(old);
        } else {
            g.setColor(colorFor(uuid == null || uuid.isBlank() ? name : uuid));
            g.fill(box);
            g.setColor(Color.WHITE);
            g.setFont(bold(size / 2f));
            String initial = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(initial, x + (size - fm.stringWidth(initial)) / 2, y + size / 2 + fm.getAscent() / 2 - 8);
        }
        g.setColor(STROKE);
        g.setStroke(new BasicStroke(2));
        g.draw(box);
    }

    /** Best-effort Minecraft face by username (mc-heads resolves premium accounts); null on any failure. */
    private static BufferedImage fetchFace(String name, int size) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            String url = "https://mc-heads.net/avatar/"
                    + URLEncoder.encode(name.trim(), StandardCharsets.UTF_8) + "/" + size;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                return null;
            }
            return ImageIO.read(new ByteArrayInputStream(resp.body()));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] encode(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static Font bold(float size) {
        return new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size));
    }

    private static Font plain(float size) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
    }

    /** Truncates with an ellipsis so text fits within {@code maxWidth} px in the graphics' current font. */
    private static String fit(Graphics2D g, String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= maxWidth) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (fm.stringWidth(sb.toString() + s.charAt(i) + "…") > maxWidth) {
                break;
            }
            sb.append(s.charAt(i));
        }
        return sb + "…";
    }

    /** Deterministic hue from a string, matching the dashboard's colored-initial fallback. */
    private static Color colorFor(String input) {
        int hash = 0;
        for (int i = 0; i < input.length(); i++) {
            hash = (hash << 5) - hash + input.charAt(i);
        }
        float hue = (Math.abs(hash) % 360) / 360f;
        return Color.getHSBColor(hue, 0.5f, 0.6f);
    }

    public static String formatNumber(long v) {
        return String.format("%,d", v);
    }

    public static String formatTicks(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        long h = seconds / 3600;
        long mm = (seconds % 3600) / 60;
        if (h > 0) {
            return h + "h " + mm + "m";
        }
        return mm + "m " + (seconds % 60) + "s";
    }
}
