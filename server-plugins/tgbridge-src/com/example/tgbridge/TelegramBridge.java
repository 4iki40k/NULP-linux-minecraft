package com.example.tgbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Two-way Minecraft <-> Telegram chat bridge for Bukkit/Cauldron 1.7.10 (Crucible).
 * Pure Bukkit API + Telegram Bot HTTP API (long-poll getUpdates + sendMessage). No extra deps
 * beyond json-simple, which is already on the Crucible classpath.
 */
public class TelegramBridge extends JavaPlugin implements Listener {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private String token;
    private String chatId;
    private boolean enabledOk;
    private volatile boolean running;
    private Thread pollThread;
    private String lang = "uk"; // config "lang": uk (default) | en
    // Space-race milestones: which world names were already reached (persisted to discovered.txt).
    private final java.util.Set<String> discovered = java.util.Collections
            .synchronizedSet(new java.util.HashSet<String>());
    private java.io.File discoveredFile;

    /** Localized message templates (English + Ukrainian). %s placeholders filled by callers. */
    private String tr(String key) {
        boolean uk = !"en".equalsIgnoreCase(lang);
        switch (key) {
            case "server_started":
                return uk ? "✅ Сервер запущено" : "✅ Server started";
            case "server_stopped":
                return uk ? "⛔ Сервер зупинено" : "⛔ Server stopped";
            case "joined":
                return uk ? "➕ %s зайшов" : "➕ %s joined";
            case "left":
                return uk ? "➖ %s вийшов" : "➖ %s left";
            case "death":
                return uk ? "💀 %s" : "💀 %s";
            case "achievement":
                return uk ? "🏆 %s отримав ачівку: %s" : "🏆 %s earned achievement: %s";
            case "claude":
                return uk ? "🤖 Клод: %s" : "🤖 Claude: %s";
            default:
                return key;
        }
    }

    /** Rich "server started" announcement: address (config "server-address" domain overrides the
     *  auto-detected public IP) + system specs (CPU model/cores, RAM) + MC version/slots. Private
     *  server, so nothing is hidden. */
    private String buildStartInfo() {
        boolean uk = !"en".equalsIgnoreCase(lang);
        StringBuilder sb = new StringBuilder(tr("server_started"));
        String addr = getConfig().getString("server-address", "").trim();
        if (addr.isEmpty()) addr = detectPublicIp();
        int port = getServer().getPort();
        if (addr != null && !addr.isEmpty()) sb.append("\n🌐 ").append(addr).append(":").append(port);
        int cores = Runtime.getRuntime().availableProcessors();
        String cpu = readFirst("/proc/cpuinfo", "model name");
        long ramMb = readMemTotalMb();
        sb.append("\n🖥 ").append(cpu != null ? cpu : (uk ? "CPU невідомий" : "unknown CPU"));
        sb.append(" ×").append(cores);
        if (ramMb > 0) sb.append(", ").append(Math.round(ramMb / 1024.0)).append(" GB RAM");
        sb.append("\n🎮 ").append(getServer().getVersion());
        sb.append(" · ").append(uk ? "слотів" : "slots").append(" ").append(getServer().getMaxPlayers());
        return sb.toString();
    }

    private String detectPublicIp() {
        for (String url : new String[] { "https://api.ipify.org", "https://checkip.amazonaws.com" }) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), UTF8));
                String ip = r.readLine();
                r.close();
                if (ip != null && !ip.trim().isEmpty()) return ip.trim();
            } catch (Exception ignore) {}
        }
        return "";
    }

    /** First value after a key line in a /proc file (e.g. "model name" in /proc/cpuinfo). */
    private String readFirst(String path, String key) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(path), UTF8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith(key)) {
                    int colon = line.indexOf(':');
                    if (colon >= 0) {
                        r.close();
                        return line.substring(colon + 1).trim();
                    }
                }
            }
            r.close();
        } catch (Exception ignore) {}
        return null;
    }

    private long readMemTotalMb() {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new java.io.FileInputStream("/proc/meminfo"), UTF8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal")) {
                    r.close();
                    String[] p = line.replaceAll("[^0-9]", " ").trim().split("\\s+");
                    if (p.length > 0 && !p[0].isEmpty()) return Long.parseLong(p[0]) / 1024; // kB -> MB
                }
            }
            r.close();
        } catch (Exception ignore) {}
        return 0;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        token = getConfig().getString("bot-token", "").trim();
        chatId = getConfig().getString("chat-id", "").trim();
        lang = getConfig().getString("lang", "uk");
        boolean joinLeave = getConfig().getBoolean("announce-join-leave", true);
        loadDiscovered();

        if (token.isEmpty() || token.startsWith("PUT_YOUR") || chatId.isEmpty() || chatId.startsWith("PUT_YOUR")) {
            getLogger().warning("bot-token / chat-id not set in plugins/TelegramBridge/config.yml — "
                    + "the bridge is loaded but INACTIVE until you fill them in and run /tg reload.");
            enabledOk = false;
            getServer().getPluginManager().registerEvents(this, this);
            return;
        }
        enabledOk = true;
        getServer().getPluginManager().registerEvents(this, this);
        startPolling();
        if (joinLeave) {
            // build the rich message off-thread (it does a public-IP HTTP lookup)
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    sendToTelegram(buildStartInfo());
                }
            });
        }
        getLogger().info("TelegramBridge active (chat-id " + chatId + ").");
    }

    @Override
    public void onDisable() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        if (enabledOk && getConfig().getBoolean("announce-join-leave", true)) {
            sendToTelegram(tr("server_stopped"));
        }
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd,
            String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("claude")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /claude <message>");
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(args[i]);
            }
            broadcastFromClaude(sb.toString());
            return true;
        }
        if (!cmd.getName().equalsIgnoreCase("tg")) {
            return false;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            running = false;
            if (pollThread != null) {
                pollThread.interrupt();
            }
            reloadConfig();
            onEnable();
            sender.sendMessage(ChatColor.GREEN + "[TelegramBridge] reloaded (active=" + enabledOk + ")");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "[TelegramBridge] active=" + enabledOk + ". Use /tg reload.");
        return true;
    }

    /**
     * Single entry point used by the operator/agent: one /claude &lt;msg&gt; shows the message
     * BOTH in-game (broadcast) and in Telegram. broadcastMessage does not fire
     * AsyncPlayerChatEvent, so it is not double-sent to Telegram.
     */
    private void broadcastFromClaude(String text) {
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "[Claude] " + ChatColor.RESET + text);
        if (enabledOk) {
            sendToTelegram(String.format(tr("claude"), text));
        }
    }

    // ---- Minecraft -> Telegram ------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!enabledOk) {
            return;
        }
        sendToTelegram(stripColor(e.getPlayer().getName()) + ": " + stripColor(e.getMessage()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (enabledOk && getConfig().getBoolean("announce-join-leave", true)) {
            sendToTelegram(String.format(tr("joined"), e.getPlayer().getName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (enabledOk && getConfig().getBoolean("announce-join-leave", true)) {
            sendToTelegram(String.format(tr("left"), e.getPlayer().getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (!enabledOk || !getConfig().getBoolean("announce-deaths", true)) {
            return;
        }
        String msg = e.getDeathMessage();
        if (msg == null || msg.isEmpty()) {
            msg = e.getEntity()
                .getName() + " died";
        }
        sendToTelegram(String.format(tr("death"), stripColor(msg)));
        // tell the player where they died so they can recover their stuff
        if (getConfig().getBoolean("death-coords", true)) {
            try {
                org.bukkit.entity.Player victim = e.getEntity();
                org.bukkit.Location l = victim.getLocation();
                boolean uk = !"en".equalsIgnoreCase(lang);
                String where = (uk ? "💀 Ти помер тут: " : "💀 You died at: ")
                        + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " (" + l.getWorld().getName() + ")";
                victim.sendMessage(ChatColor.GRAY + where);
            } catch (Throwable ignore) {}
        }
    }

    /** Online players as a List (Bukkit 1.7.10 returns Player[]; newer returns Collection). */
    @SuppressWarnings("unchecked")
    private java.util.List<Player> onlinePlayers() {
        java.util.List<Player> list = new java.util.ArrayList<Player>();
        try {
            Object res = Bukkit.class.getMethod("getOnlinePlayers").invoke(null);
            if (res instanceof Player[]) {
                for (Player p : (Player[]) res) list.add(p);
            } else if (res instanceof Iterable) {
                for (Object o : (Iterable<Object>) res) list.add((Player) o);
            }
        } catch (Throwable ignore) {}
        return list;
    }

    // ---- Space-race milestones: announce the FIRST player to reach each new world (GC planet) ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        if (!enabledOk || !getConfig().getBoolean("announce-space-milestones", true)) return;
        try {
            String world = e.getPlayer()
                    .getWorld()
                    .getName();
            // log the actual world name so the admin can see GC's real dim names and tune the config
            getLogger().info("[milestone] " + e.getPlayer().getName() + " entered world '" + world + "'");
            // skip the everyday worlds; everything else (GC planets/dims) is a "space" destination
            if (isMundaneWorld(world)) return;
            if (discovered.contains(world)) return; // someone already got there first
            discovered.add(world);
            saveDiscovered();
            String who = stripColor(e.getPlayer().getName());
            String dest = friendlyWorld(world);
            boolean uk = !"en".equalsIgnoreCase(lang);
            String msg = uk
                    ? ("🚀 " + who + " ПЕРШИМ дістався: " + dest + "!")
                    : ("🚀 " + who + " is FIRST to reach " + dest + "!");
            Bukkit.broadcastMessage(ChatColor.GOLD + msg);
            sendToTelegram(msg);
        } catch (Throwable t) {
            getLogger().warning("[milestone] failed: " + t);
        }
    }

    private boolean isMundaneWorld(String name) {
        String n = name.toLowerCase();
        // overworld / nether / end and their variants are not "space" milestones
        return n.equals("world") || n.contains("nether") || n.contains("the_end") || n.endsWith("_end")
                || n.equals("overworld") || n.equals("dim0") || n.equals("dim-1") || n.equals("dim1");
    }

    /** Friendly destination name: config "space-names.<world>", else built-in GC defaults, else raw. */
    private String friendlyWorld(String world) {
        String cfg = getConfig().getString("space-names." + world, null);
        if (cfg != null && !cfg.isEmpty()) return cfg;
        boolean uk = !"en".equalsIgnoreCase(lang);
        String n = world.toLowerCase();
        if (n.contains("moon") || n.contains("-28")) return uk ? "Місяць" : "the Moon";
        if (n.contains("mars") || n.contains("-29")) return uk ? "Марс" : "Mars";
        if (n.contains("asteroid") || n.contains("-30")) return uk ? "Астероїди" : "the Asteroids";
        if (n.contains("venus")) return uk ? "Венера" : "Venus";
        if (n.contains("space") && n.contains("station")) return uk ? "Космічна станція" : "a Space Station";
        return world; // unknown — show the raw world name (and it's logged above)
    }

    private void loadDiscovered() {
        try {
            discoveredFile = new java.io.File(getDataFolder(), "discovered.txt");
            if (discoveredFile.exists()) {
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(new java.io.FileInputStream(discoveredFile), UTF8));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) discovered.add(line);
                }
                r.close();
            }
        } catch (Throwable ignore) {}
    }

    private void saveDiscovered() {
        try {
            if (discoveredFile == null) return;
            discoveredFile.getParentFile()
                    .mkdirs();
            OutputStream os = new java.io.FileOutputStream(discoveredFile);
            synchronized (discovered) {
                for (String w : discovered) {
                    os.write((w + "\n").getBytes(UTF8));
                }
            }
            os.close();
        } catch (Throwable ignore) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAchievement(PlayerAchievementAwardedEvent e) {
        if (!enabledOk || !getConfig().getBoolean("announce-achievements", true)) {
            return;
        }
        sendToTelegram(String.format(tr("achievement"), e.getPlayer()
            .getName(), e.getAchievement()));
    }

    private void sendToTelegram(final String text) {
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    String url = api("sendMessage") + "?chat_id=" + enc(chatId)
                            + "&disable_web_page_preview=true&text=" + enc(text);
                    httpGet(url);
                } catch (Exception ex) {
                    getLogger().warning("sendMessage failed: " + ex.getMessage());
                }
            }
        });
    }

    // ---- Telegram -> Minecraft (long polling) ---------------------------------

    private void startPolling() {
        running = true;
        pollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long offset = 0;
                while (running) {
                    try {
                        String url = api("getUpdates") + "?timeout=25&offset=" + offset;
                        String resp = httpGet(url);
                        Object parsed = JSONValue.parse(resp);
                        if (!(parsed instanceof JSONObject)) {
                            continue;
                        }
                        JSONObject root = (JSONObject) parsed;
                        if (!Boolean.TRUE.equals(root.get("ok"))) {
                            getLogger().warning("getUpdates not ok: " + resp);
                            Thread.sleep(5000);
                            continue;
                        }
                        JSONArray result = (JSONArray) root.get("result");
                        for (Object o : (List<?>) result) {
                            JSONObject upd = (JSONObject) o;
                            offset = ((Number) upd.get("update_id")).longValue() + 1;
                            handleUpdate(upd);
                        }
                    } catch (InterruptedException ie) {
                        return;
                    } catch (Exception ex) {
                        getLogger().warning("getUpdates error: " + ex.getMessage());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    }
                }
            }
        }, "TelegramBridge-poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void handleUpdate(JSONObject upd) {
        Object msgO = upd.get("message");
        if (!(msgO instanceof JSONObject)) {
            return;
        }
        JSONObject msg = (JSONObject) msgO;
        JSONObject chat = (JSONObject) msg.get("chat");
        if (chat == null || !String.valueOf(chat.get("id")).equals(chatId)) {
            return; // ignore other chats
        }
        Object textO = msg.get("text");
        if (textO == null) {
            return;
        }
        String text = textO.toString();
        if (text.startsWith("/")) {
            handleTgCommand(text.trim());
            return; // commands don't get broadcast as chat
        }
        JSONObject from = (JSONObject) msg.get("from");
        String name = from != null && from.get("first_name") != null
                ? from.get("first_name").toString()
                : "TG";
        // If this message is a reply, surface what it replies to (author + snippet).
        String replyCtx = "";
        Object replyO = msg.get("reply_to_message");
        if (replyO instanceof JSONObject) {
            JSONObject rep = (JSONObject) replyO;
            JSONObject rfrom = (JSONObject) rep.get("from");
            String rname = rfrom != null && rfrom.get("first_name") != null
                    ? rfrom.get("first_name").toString()
                    : "?";
            Object rtextO = rep.get("text");
            String rtext = rtextO != null ? rtextO.toString() : "(non-text)";
            if (rtext.length() > 50) {
                rtext = rtext.substring(0, 50) + "...";
            }
            replyCtx = ChatColor.DARK_GRAY + " [reply to " + rname + ": " + rtext + "]" + ChatColor.RESET;
        }
        final String line = ChatColor.AQUA + "[TG] " + ChatColor.RESET
                + ChatColor.GRAY + name + replyCtx + ": " + ChatColor.WHITE + text;
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage(line);
            }
        });
    }

    /** Handle a Telegram-side command ({@code /online}, {@code /tps}, {@code /help}). */
    private void handleTgCommand(String text) {
        // strip a possible @BotName suffix and arguments
        String cmd = text.split("\\s+")[0].toLowerCase();
        int at = cmd.indexOf('@');
        if (at >= 0) cmd = cmd.substring(0, at);
        boolean uk = !"en".equalsIgnoreCase(lang);
        if (cmd.equals("/online") || cmd.equals("/list") || cmd.equals("/who")) {
            // query online players on the main thread
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    StringBuilder sb = new StringBuilder();
                    int n = 0;
                    for (Player p : onlinePlayers()) {
                        if (n++ > 0) sb.append(", ");
                        sb.append(stripColor(p.getName()));
                    }
                    String msg = uk
                            ? ("👥 Онлайн (" + n + "): " + (n == 0 ? "нікого" : sb))
                            : ("👥 Online (" + n + "): " + (n == 0 ? "nobody" : sb));
                    sendToTelegram(msg);
                }
            });
        } else if (cmd.equals("/tps")) {
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    String t = formatTps();
                    sendToTelegram((uk ? "⏱ TPS: " : "⏱ TPS: ") + t);
                }
            });
        } else if (cmd.equals("/help") || cmd.equals("/start") || cmd.equals("/commands")) {
            sendToTelegram(uk
                    ? "🤖 Команди: /online — хто онлайн · /tps — продуктивність · /help — це повідомлення"
                    : "🤖 Commands: /online — who's online · /tps — performance · /help — this message");
        }
        // unknown commands are silently ignored
    }

    /** Server TPS via Spigot/Crucible getTPS() (reflection); "n/a" if unavailable. */
    private String formatTps() {
        try {
            Object srv = Bukkit.getServer();
            Object tps = srv.getClass().getMethod("getTPS").invoke(srv);
            if (tps instanceof double[]) {
                double[] a = (double[]) tps;
                double now = a.length > 0 ? Math.min(20.0, a[0]) : 20.0;
                return String.format("%.1f", now);
            }
        } catch (Throwable ignore) {}
        return "n/a";
    }

    // ---- helpers --------------------------------------------------------------

    private String api(String method) {
        return "https://api.telegram.org/bot" + token + "/" + method;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripColor(String s) {
        return ChatColor.stripColor(s);
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(35000);
        c.setRequestProperty("User-Agent", "TelegramBridge/1.0");
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, UTF8));
            String ln;
            while ((ln = r.readLine()) != null) {
                sb.append(ln);
            }
            r.close();
        }
        if (code < 200 || code >= 400) {
            throw new IOException("HTTP " + code + ": " + sb);
        }
        return sb.toString();
    }
}
