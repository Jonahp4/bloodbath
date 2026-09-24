package net.unchartedsmp.bloodbath.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gets the 3D models to players with zero setup.
 *
 * <p>The pack is built into the plugin jar. In {@code embedded} mode (the default) the plugin
 * serves it itself from a tiny HTTP server on its own port and points each player at the same host
 * name they used to connect, so it works on a home server or VPS without configuring a URL. In
 * {@code url} mode it sends a pack you host yourself. Either way the pack is also exported to
 * {@code plugins/Bloodbath/Bloodbath-ResourcePack.zip} to upload or merge elsewhere.
 *
 * <p>The HTTP server only answers GET/HEAD for the one pack file (its name contains the pack's
 * hash, so an updated pack is never served from a stale cache) and runs on two daemon threads.
 */
public final class ResourcePackService {
	/** Stable id, so re-sending replaces our pack without touching other server packs. */
	public static final UUID PACK_ID = UUID.nameUUIDFromBytes("bloodbath:resource-pack".getBytes(StandardCharsets.UTF_8));
	public static final String EXPORT_NAME = "Bloodbath-ResourcePack.zip";
	private static final long FAILURE_LOG_INTERVAL_MS = 10 * 60 * 1000L;

	private final JavaPlugin plugin;
	private byte[] zip = new byte[0];
	private String zipSha1 = "";
	private HttpServer server;
	private ExecutorService executor;
	private volatile String path = "/";
	/** The port the embedded server actually listens on (resource-pack.port, or a free one for 0). */
	private int port;
	private final AtomicInteger downloads = new AtomicInteger();
	/** url mode: the hash of the hosted file, from config or computed at startup. */
	private volatile String remoteHash = "";
	private boolean active;
	private String status = "off";
	private long lastFailureLog;

	public ResourcePackService(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void start() {
		stop();
		Settings settings = Settings.get();
		loadBundledPack();
		exportPack();
		if (!settings.packEnabled) {
			status = "disabled in config";
			return;
		}
		switch (settings.packMode) {
			case "embedded" -> startServer(settings);
			case "url" -> useUrl(settings);
			default -> {
				status = "unknown mode '" + settings.packMode + "'";
				plugin.getLogger().warning("resource-pack.mode must be 'embedded' or 'url', not '" + settings.packMode + "'. Not sending the pack.");
			}
		}
	}

	public void stop() {
		active = false;
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		status = "off";
	}

	private void loadBundledPack() {
		try (InputStream in = plugin.getResource("resourcepack.zip")) {
			if (in == null) {
				plugin.getLogger().severe("resourcepack.zip is missing from the plugin jar; weapons will look like plain netherite swords.");
				return;
			}
			zip = in.readAllBytes();
			zipSha1 = sha1(zip);
			path = "/bloodbath-" + zipSha1.substring(0, 12) + ".zip";
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Couldn't read the bundled resource pack", e);
		}
	}

	private void exportPack() {
		if (zip.length == 0) {
			return;
		}
		File out = new File(plugin.getDataFolder(), EXPORT_NAME);
		try {
			if (out.isFile() && out.length() == zip.length && sha1(Files.readAllBytes(out.toPath())).equals(zipSha1)) {
				return;
			}
			Files.createDirectories(out.toPath().getParent());
			Files.write(out.toPath(), zip);
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Couldn't export " + EXPORT_NAME, e);
		}
	}

	private void startServer(Settings settings) {
		if (zip.length == 0) {
			status = "no pack in jar";
			return;
		}
		try {
			InetSocketAddress address = settings.packBind.isEmpty() || settings.packBind.equals("0.0.0.0")
				? new InetSocketAddress(settings.packPort)
				: new InetSocketAddress(settings.packBind, settings.packPort);
			server = HttpServer.create(address, 32);
			executor = Executors.newFixedThreadPool(2, runnable -> {
				Thread thread = new Thread(runnable, "Bloodbath pack server");
				thread.setDaemon(true);
				return thread;
			});
			server.setExecutor(executor);
			server.createContext("/", this::handle);
			server.start();
			port = server.getAddress().getPort();
			active = true;
			status = "serving on port " + port;
			plugin.getLogger().info("Serving the resource pack on port " + port
				+ (settings.packPublicHost.isEmpty() ? " (players get the address they connected with)" : " as " + settings.packPublicHost));
		} catch (IOException | IllegalArgumentException e) {
			stop();
			status = "couldn't open port " + settings.packPort;
			plugin.getLogger().warning("Couldn't start the resource pack server on port " + settings.packPort + ": " + e.getMessage()
				+ ". Pick a free port with resource-pack.port, or host " + EXPORT_NAME + " yourself and use mode: url.");
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		try {
			String method = exchange.getRequestMethod();
			boolean head = "HEAD".equals(method);
			if (!head && !"GET".equals(method)) {
				exchange.sendResponseHeaders(405, -1);
				return;
			}
			if (!path.equals(exchange.getRequestURI().getPath())) {
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			byte[] body = zip;
			exchange.getResponseHeaders().set("Content-Type", "application/zip");
			exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
			if (head) {
				exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
				exchange.sendResponseHeaders(200, -1);
				return;
			}
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
			downloads.incrementAndGet();
		} finally {
			exchange.close();
		}
	}

	private void useUrl(Settings settings) {
		URI uri = parse(settings.packUrl);
		if (uri == null) {
			status = "no valid resource-pack.url";
			plugin.getLogger().warning("resource-pack.mode is 'url' but resource-pack.url isn't a valid http(s) URL. Not sending the pack.");
			return;
		}
		active = true;
		status = "sending " + uri;
		if (!settings.packSha1.isEmpty()) {
			remoteHash = settings.packSha1;
			return;
		}
		// No hash configured: download it once in the background so clients can cache it properly.
		remoteHash = "";
		ResourcePackInfo.resourcePackInfo().id(PACK_ID).uri(uri).computeHashAndBuild().whenComplete((info, error) -> {
			if (error != null) {
				plugin.getLogger().warning("Couldn't download " + uri + " to hash it (" + error.getMessage()
					+ "). Sending it without a hash; set resource-pack.sha1 to fix caching.");
			} else {
				remoteHash = info.hash();
			}
		});
	}

	private static URI parse(String url) {
		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null ? uri : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ---- sending -----------------------------------------------------------------------------

	public void sendOnJoin(Player player) {
		if (active) {
			// A moment after joining, so the prompt doesn't fight the world loading in.
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (player.isOnline()) {
					send(player);
				}
			}, 20L);
		}
	}

	/** Returns false if the pack is off or no address could be worked out for this player. */
	public boolean send(Player player) {
		if (!active) {
			return false;
		}
		Settings settings = Settings.get();
		URI uri = uriFor(player, settings);
		if (uri == null) {
			logFailure("Couldn't work out an address to send " + player.getName() + " the resource pack from. "
				+ "Set resource-pack.public-host to your server's domain or IP.");
			return false;
		}
		String hash = settings.packMode.equals("url") ? remoteHash : zipSha1;
		ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
			.packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, hash))
			.required(settings.packRequired)
			.prompt(settings.packPrompt)
			.replace(false)
			.build();
		player.sendResourcePacks(request);
		return true;
	}

	private URI uriFor(Player player, Settings settings) {
		if (settings.packMode.equals("url")) {
			return parse(settings.packUrl);
		}
		String configured = settings.packPublicHost;
		if (configured.startsWith("http://") || configured.startsWith("https://")) {
			// A base URL, e.g. behind a reverse proxy.
			return parse(configured.replaceAll("/+$", "") + path);
		}
		String host = configured;
		if (host.isEmpty()) {
			InetSocketAddress virtual = player.getVirtualHost();
			host = virtual == null ? "" : virtual.getHostString();
		}
		if (host == null || host.isEmpty()) {
			host = Bukkit.getIp();
		}
		host = clean(host);
		if (host.isEmpty() || host.equals("0.0.0.0")) {
			return null;
		}
		boolean hasPort = host.startsWith("[") ? host.contains("]:") : host.indexOf(':') > 0 && host.indexOf(':') == host.lastIndexOf(':');
		String authority = hasPort ? host : host + ":" + port;
		return parse("http://" + authority + path);
	}

	/** Strips Forge's "\0FML\0" handshake marker and a trailing dot, and brackets IPv6 literals. */
	private static String clean(String host) {
		String out = host.trim();
		int nul = out.indexOf('\0');
		if (nul >= 0) {
			out = out.substring(0, nul);
		}
		if (out.endsWith(".")) {
			out = out.substring(0, out.length() - 1);
		}
		if (out.indexOf(':') != out.lastIndexOf(':') && !out.startsWith("[")) {
			out = "[" + out + "]";
		}
		return out;
	}

	public void onStatus(Player player, UUID packId, PlayerResourcePackStatusEvent.Status status) {
		if (!PACK_ID.equals(packId)) {
			return;
		}
		Settings settings = Settings.get();
		switch (status) {
			case DECLINED -> player.sendMessage(settings.prefix
				.append(Component.text("No pack, no 3D weapons: they'll look like netherite swords. ", NamedTextColor.GRAY))
				.append(Component.text("[Get it]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/bloodbath pack"))));
			case FAILED_DOWNLOAD, INVALID_URL -> {
				player.sendMessage(settings.prefix.append(Component.text(
					"The Bloodbath resource pack couldn't be downloaded, so weapons look like netherite swords.", NamedTextColor.GRAY)));
				URI uri = uriFor(player, settings);
				logFailure(player.getName() + " couldn't download the resource pack from " + uri + ". "
					+ (settings.packMode.equals("embedded")
						? "Is port " + settings.packPort + " open to the internet? If not, open it, or host "
							+ EXPORT_NAME + " yourself (mode: url)."
						: "Check that resource-pack.url is a direct download link."));
			}
			case FAILED_RELOAD -> logFailure(player.getName() + "'s game couldn't load the resource pack (another pack may conflict).");
			default -> {
			}
		}
	}

	private void logFailure(String message) {
		long now = System.currentTimeMillis();
		if (now - lastFailureLog >= FAILURE_LOG_INTERVAL_MS) {
			lastFailureLog = now;
			plugin.getLogger().warning(message);
		}
	}

	// ---- status ------------------------------------------------------------------------------

	public boolean isActive() {
		return active;
	}

	public String status() {
		return status;
	}

	/** The pack's address for this player (what they'd be sent), or null. */
	public URI address(Player player) {
		return active ? uriFor(player, Settings.get()) : null;
	}

	public int downloads() {
		return downloads.get();
	}

	public String sha1() {
		return zipSha1;
	}

	public int size() {
		return zip.length;
	}

	private static String sha1(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
