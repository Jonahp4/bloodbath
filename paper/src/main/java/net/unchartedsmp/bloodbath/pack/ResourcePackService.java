package net.unchartedsmp.bloodbath.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.unchartedsmp.bloodbath.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gets the 3D models to players with zero setup.
 *
 * <p>The pack is built into the plugin jar, and it can reach players three ways:
 * <ul>
 * <li><b>mirror</b>: a public copy of exactly this pack (by default the plugin's GitHub
 * repository). Every mirror is downloaded once at startup and only used if its SHA-1 matches the
 * pack inside the jar, byte for byte, so players can never be sent a different pack.</li>
 * <li><b>embedded</b>: a tiny HTTP server on its own port, pointing each player at the same host
 * name they used to connect. Needs that port open to the internet.</li>
 * <li><b>url</b>: a pack the owner hosts themselves (it may be a merged pack).</li>
 * </ul>
 * {@code mode: auto} (the default) prefers a verified mirror and falls back to the built-in
 * server; {@code embedded} is the other way round; {@code url} only sends the owner's link. When a
 * player's download fails, they're sent the pack again from the next source straight away, and the
 * built-in server is moved to the back of the line for everyone else this session.
 *
 * <p>The pack is also exported to {@code plugins/Bloodbath/Bloodbath-ResourcePack.zip} to upload
 * or merge elsewhere. The HTTP server only answers GET/HEAD for the one pack file (its name
 * contains the pack's hash, so an updated pack is never served from a stale cache) and runs on two
 * daemon threads.
 */
public final class ResourcePackService {
	/** Stable id, so re-sending replaces our pack without touching other server packs. */
	public static final UUID PACK_ID = UUID.nameUUIDFromBytes("bloodbath:resource-pack".getBytes(StandardCharsets.UTF_8));
	public static final String EXPORT_NAME = "Bloodbath-ResourcePack.zip";
	private static final long FAILURE_LOG_INTERVAL_MS = 10 * 60 * 1000L;
	/** Mirrors that don't match yet (a push still propagating) are checked again this often, this many times. */
	private static final long MIRROR_RETRY_TICKS = 20L * 60 * 10;
	private static final int MIRROR_ATTEMPTS = 4;
	private static final int MAX_MIRROR_BYTES = 64 * 1024 * 1024;

	/** Where a player is sent the pack from. */
	public enum Source {
		MIRROR("the mirror"), EMBEDDED("this server"), URL("resource-pack.url");

		private final String label;

		Source(String label) {
			this.label = label;
		}
	}

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
	private boolean urlActive;
	private String status = "off";
	private long lastFailureLog;

	/** A verified public copy of this exact pack, or null. */
	private volatile URI mirror;
	private volatile String mirrorStatus = "none";
	/** What {@link #mirror} was verified against (pack hash + candidates), so a reload can keep it. */
	private String mirrorKey = "";
	/** Bumped by every start/stop, so a mirror check from before a reload can't land afterwards. */
	private volatile int generation;
	/** A player couldn't download from the built-in server this session: it isn't reachable for them. */
	private volatile boolean embeddedFailed;
	private boolean opsTold;
	private final Map<UUID, Source> sentFrom = new HashMap<>();
	private final Map<UUID, EnumSet<Source>> failedFor = new HashMap<>();
	/** Players to send the pack to as soon as the mirror check finishes (main thread only). */
	private final Set<UUID> waiting = new HashSet<>();

	public ResourcePackService(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void start() {
		URI keptMirror = mirror;
		String keptKey = mirrorKey;
		stop();
		Settings settings = Settings.get();
		loadBundledPack();
		exportPack();
		if (!settings.packEnabled) {
			status = "disabled in config";
			return;
		}
		switch (settings.packMode) {
			case "auto", "embedded" -> {
				startServer(settings);
				List<URI> candidates = mirrorCandidates(settings);
				String key = zipSha1 + candidates;
				if (keptMirror != null && key.equals(keptKey)) {
					mirrorKey = keptKey; // a reload with the same pack and mirrors: still good
					mirrorStatus = "verified";
					mirror = keptMirror;
					flushWaiting();
				} else {
					verifyMirrors(candidates, key, generation, 1);
				}
				status = settings.packMode + (server != null ? ", built-in server on port " + port : ", built-in server off");
			}
			case "url" -> useUrl(settings);
			default -> {
				status = "unknown mode '" + settings.packMode + "'";
				plugin.getLogger().warning("resource-pack.mode must be 'auto', 'embedded' or 'url', not '" + settings.packMode
					+ "'. Not sending the pack.");
			}
		}
	}

	public void stop() {
		generation++;
		urlActive = false;
		mirror = null;
		mirrorKey = "";
		mirrorStatus = "none";
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

	// ---- the built-in server -------------------------------------------------------------------

	private void startServer(Settings settings) {
		if (zip.length == 0) {
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
			plugin.getLogger().info("Serving the resource pack on port " + port
				+ (settings.packPublicHost.isEmpty() ? " (players get the address they connected with)" : " as " + settings.packPublicHost));
		} catch (IOException | IllegalArgumentException e) {
			if (server != null) {
				server.stop(0);
				server = null;
			}
			if (executor != null) {
				executor.shutdownNow();
				executor = null;
			}
			plugin.getLogger().warning("Couldn't start the resource pack server on port " + settings.packPort + ": " + e.getMessage()
				+ (settings.packMode.equals("auto") ? ". Players get the pack from the mirror instead."
					: ". Pick a free port with resource-pack.port, use mode: auto, or host " + EXPORT_NAME + " yourself (mode: url)."));
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

	// ---- mirrors ------------------------------------------------------------------------------

	private List<URI> mirrorCandidates(Settings settings) {
		String version = plugin.getPluginMeta().getVersion();
		return settings.packMirrors.stream()
			.map(url -> parse(url.replace("{version}", version)))
			.filter(Objects::nonNull)
			.distinct()
			.toList();
	}

	/**
	 * Downloads each candidate in turn (off the main thread) and adopts the first that is
	 * byte-for-byte the pack in the jar. None yet: tries again later, since a freshly pushed file can
	 * take a few minutes to reach a CDN.
	 */
	private void verifyMirrors(List<URI> candidates, String key, int gen, int attempt) {
		if (candidates.isEmpty() || zip.length == 0) {
			mirrorStatus = "none configured";
			flushWaiting();
			return;
		}
		mirrorStatus = "checking";
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		checkMirror(client, candidates, 0, key, gen, attempt, new ArrayList<>());
	}

	private void checkMirror(HttpClient client, List<URI> candidates, int index, String key, int gen, int attempt, List<String> problems) {
		if (gen != generation) {
			return;
		}
		if (index >= candidates.size()) {
			mirrorStatus = "none match this pack (" + String.join("; ", problems) + ")";
			checkFinished(gen);
			if (attempt == 1) {
				plugin.getLogger().info("No resource pack mirror has this exact pack yet (" + String.join("; ", problems) + ")."
					+ (server != null ? " Using the built-in server on port " + port + "." : ""));
			}
			if (attempt < MIRROR_ATTEMPTS && plugin.isEnabled()) {
				try {
					Bukkit.getScheduler().runTaskLater(plugin, () -> {
						if (gen == generation) {
							verifyMirrors(candidates, key, gen, attempt + 1);
						}
					}, MIRROR_RETRY_TICKS);
				} catch (IllegalStateException | IllegalArgumentException e) {
					// The plugin is shutting down; nothing to retry for.
				}
			}
			return;
		}
		URI uri = candidates.get(index);
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(30))
			.header("User-Agent", "Bloodbath/" + plugin.getPluginMeta().getVersion())
			.GET()
			.build();
		client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
			if (gen != generation) {
				return;
			}
			String problem;
			if (error != null) {
				problem = uri.getHost() + ": " + error.getClass().getSimpleName();
			} else if (response.statusCode() != 200) {
				problem = uri.getHost() + ": HTTP " + response.statusCode();
			} else if (response.body().length > MAX_MIRROR_BYTES || !sha1(response.body()).equals(zipSha1)) {
				problem = uri.getHost() + ": a different pack";
			} else {
				mirrorKey = key;
				mirrorStatus = "verified";
				mirror = uri; // volatile: publishes the two above with it
				checkFinished(gen);
				plugin.getLogger().info("Players download the resource pack from " + uri + " (checked: identical to the pack in this jar)"
					+ (server != null ? "; the built-in server on port " + port + " is the fallback." : "."));
				return;
			}
			problems.add(problem);
			checkMirror(client, candidates, index + 1, key, gen, attempt, problems);
		});
	}

	// ---- url mode -----------------------------------------------------------------------------

	private void useUrl(Settings settings) {
		URI uri = parse(settings.packUrl);
		if (uri == null) {
			status = "no valid resource-pack.url";
			plugin.getLogger().warning("resource-pack.mode is 'url' but resource-pack.url isn't a valid http(s) URL. Not sending the pack.");
			return;
		}
		urlActive = true;
		status = "url, sending " + uri;
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

	private boolean available(Source source) {
		return switch (source) {
			case MIRROR -> mirror != null;
			case EMBEDDED -> server != null;
			case URL -> urlActive;
		};
	}

	/** The sources to try, best first. */
	private List<Source> order() {
		return switch (Settings.get().packMode) {
			case "url" -> List.of(Source.URL);
			case "embedded" -> embeddedFailed ? List.of(Source.MIRROR, Source.EMBEDDED) : List.of(Source.EMBEDDED, Source.MIRROR);
			default -> List.of(Source.MIRROR, Source.EMBEDDED);
		};
	}

	/** Where this player should get the pack from next: the best source they haven't failed on. */
	private Source pick(UUID player, boolean untriedOnly) {
		EnumSet<Source> failed = failedFor.getOrDefault(player, EnumSet.noneOf(Source.class));
		for (Source source : order()) {
			if (available(source) && !failed.contains(source)) {
				return source;
			}
		}
		if (untriedOnly) {
			return null;
		}
		// Everything failed for them before: start again from the top (a manual retry).
		failedFor.remove(player);
		for (Source source : order()) {
			if (available(source)) {
				return source;
			}
		}
		return null;
	}

	public boolean isActive() {
		return available(Source.MIRROR) || available(Source.EMBEDDED) || available(Source.URL);
	}

	public void sendOnJoin(Player player) {
		if (Settings.get().packEnabled) {
			// A moment after joining, so the prompt doesn't fight the world loading in.
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (player.isOnline()) {
					sendWhenReady(player);
				}
			}, 20L);
		}
	}

	/**
	 * Sends the pack now, or, while the mirror is still being checked (the first moments after a
	 * start), as soon as that's done, so nobody is sent a source that's about to be replaced.
	 */
	public void sendWhenReady(Player player) {
		if (mirrorStatus.equals("checking")) {
			waiting.add(player.getUniqueId());
		} else {
			send(player);
		}
	}

	/** From the mirror check's thread: send the pack to everyone who was waiting for it. */
	private void checkFinished(int gen) {
		if (!plugin.isEnabled()) {
			return;
		}
		try {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (gen == generation) {
					flushWaiting();
				}
			});
		} catch (IllegalStateException | IllegalArgumentException e) {
			// Shutting down: nobody to send anything to.
		}
	}

	private void flushWaiting() {
		List<UUID> ready = new ArrayList<>(waiting);
		waiting.clear();
		for (UUID id : ready) {
			Player player = Bukkit.getPlayer(id);
			if (player != null && player.isOnline()) {
				send(player);
			}
		}
	}

	/** Returns false if the pack is off or no address could be worked out for this player. */
	public boolean send(Player player) {
		return send(player, pick(player.getUniqueId(), false));
	}

	private boolean send(Player player, Source source) {
		if (source == null) {
			return false;
		}
		Settings settings = Settings.get();
		URI uri = uriFor(player, settings, source);
		if (uri == null) {
			logFailure("Couldn't work out an address to send " + player.getName() + " the resource pack from. "
				+ "Set resource-pack.public-host to your server's domain or IP, or use mode: auto.");
			return false;
		}
		ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
			.packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, hash()))
			.required(settings.packRequired)
			.prompt(settings.packPrompt)
			.replace(false)
			.build();
		sentFrom.put(player.getUniqueId(), source);
		player.sendResourcePacks(request);
		return true;
	}

	private URI uriFor(Player player, Settings settings, Source source) {
		return switch (source) {
			case URL -> parse(settings.packUrl);
			case MIRROR -> mirror;
			case EMBEDDED -> embeddedUri(player, settings);
		};
	}

	private URI embeddedUri(Player player, Settings settings) {
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

	/** The hash of the pack players are sent now. */
	public String hash() {
		return Settings.get().packMode.equals("url") ? remoteHash : zipSha1;
	}

	// ---- what players' games report --------------------------------------------------------------

	public void onStatus(Player player, UUID packId, PlayerResourcePackStatusEvent.Status status) {
		boolean ours = PACK_ID.equals(packId);
		PackState.status(player, ours, status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED,
			switch (status) {
				case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> true;
				default -> false;
			}, hash());
		if (!ours) {
			return;
		}
		Settings settings = Settings.get();
		UUID id = player.getUniqueId();
		switch (status) {
			case SUCCESSFULLY_LOADED -> failedFor.remove(id);
			case DECLINED -> player.sendMessage(settings.prefix
				.append(Component.text("No pack, no 3D weapons: they'll look like netherite swords. ", NamedTextColor.GRAY))
				.append(Component.text("[Get it]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/bloodbath pack"))));
			case FAILED_DOWNLOAD, INVALID_URL -> downloadFailed(player, settings);
			case FAILED_RELOAD -> logFailure(player.getName() + "'s game couldn't load the resource pack (another pack may conflict).");
			default -> {
			}
		}
	}

	private void downloadFailed(Player player, Settings settings) {
		UUID id = player.getUniqueId();
		Source from = sentFrom.getOrDefault(id, order().get(0));
		failedFor.computeIfAbsent(id, key -> EnumSet.noneOf(Source.class)).add(from);
		URI failedUri = uriFor(player, settings, from);
		if (from == Source.EMBEDDED) {
			embeddedFailed = true;
		}
		Source next = pick(id, true);
		logFailure(player.getName() + " couldn't download the resource pack from " + failedUri + ". " + switch (from) {
			case EMBEDDED -> "Port " + port + " isn't reachable from outside"
				+ (next == Source.MIRROR ? "; sending the mirror instead (nothing to do)." : ": open it, or use mode: auto.");
			case MIRROR -> "The mirror isn't reachable for them" + (next == Source.EMBEDDED ? "; trying the built-in server." : ".");
			case URL -> "Check that resource-pack.url is a direct download link.";
		});
		if (from == Source.EMBEDDED) {
			tellOps(next == Source.MIRROR);
		}
		if (next != null && send(player, next)) {
			player.sendMessage(settings.prefix.append(Component.text("Download failed; trying again from " + next.label + "...", NamedTextColor.GRAY)));
			return;
		}
		player.sendMessage(settings.prefix
			.append(Component.text("The Bloodbath resource pack couldn't be downloaded, so weapons look like netherite swords. ", NamedTextColor.GRAY))
			.append(Component.text("[Retry]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/bloodbath pack")))
			.append(Component.text(" "))
			.append(Component.text("[I have it installed]", NamedTextColor.DARK_RED)
				.hoverEvent(HoverEvent.showText(Component.text("Turn on the custom particles, boss model and HUD icons for you.\n"
					+ "Only if you installed the Bloodbath pack yourself.", NamedTextColor.GRAY)))
				.clickEvent(ClickEvent.runCommand("/bloodbath visuals on"))));
	}

	/** Once a session: tell online admins the built-in server can't be reached, and what happens now. */
	private void tellOps(boolean mirrorCovers) {
		if (opsTold) {
			return;
		}
		opsTold = true;
		Component message = Settings.get().prefix.append(Component.text(mirrorCovers
			? "A player couldn't download the pack from this server (port " + port + " isn't reachable from outside), "
				+ "so everyone gets it from the mirror instead. Nothing to do."
			: "Players can't download the pack from this server: port " + port + " isn't reachable from outside and there's "
				+ "no mirror to fall back on. Open the port, or set resource-pack.mode to auto (see config.yml).",
			mirrorCovers ? NamedTextColor.GRAY : NamedTextColor.RED));
		for (Player op : Bukkit.getOnlinePlayers()) {
			if (op.hasPermission("bloodbath.admin")) {
				op.sendMessage(message);
			}
		}
	}

	/** Drops what we know about a player's downloads when they leave. */
	public void forget(UUID player) {
		sentFrom.remove(player);
		failedFor.remove(player);
		waiting.remove(player);
	}

	private void logFailure(String message) {
		long now = System.currentTimeMillis();
		if (now - lastFailureLog >= FAILURE_LOG_INTERVAL_MS) {
			lastFailureLog = now;
			plugin.getLogger().warning(message);
		}
	}

	// ---- status ------------------------------------------------------------------------------

	public String status() {
		if (!Settings.get().packEnabled || Settings.get().packMode.equals("url") || status.startsWith("unknown")) {
			return status;
		}
		URI verified = mirror;
		String mirrorPart = switch (mirrorStatus) {
			case "verified" -> verified == null ? "mirror verified" : "mirror " + verified.getHost() + " (verified)";
			case "checking" -> "checking the mirror";
			default -> "no mirror: " + mirrorStatus;
		};
		return status + " · " + mirrorPart + (embeddedFailed ? " · built-in server unreachable for players" : "");
	}

	/** The pack's address for this player (what they'd be sent next), or null. */
	public URI address(Player player) {
		Source source = pick(player.getUniqueId(), true);
		return source == null ? null : uriFor(player, Settings.get(), source);
	}

	/** The verified mirror, or null. */
	public URI mirror() {
		return mirror;
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
