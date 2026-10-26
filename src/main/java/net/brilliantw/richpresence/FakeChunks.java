package net.brilliantw.richpresence;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class FakeChunks {
	public static final int MAX_RANGE = 64;
	private static final int PLACEHOLDERS_PER_STEP = 64;
	private static final long CLEANUP_MS = 10 * 60 * 1000;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "brilliant-fake-chunks");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<String, Set<Long>> INDEX = new ConcurrentHashMap<>();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
	private static final Queue<Loaded> READY = new ConcurrentLinkedQueue<>();
	private record Loaded(String world, long pos, byte[] data) {}

	private static int serverRadius = -1;
	private static int applied = -1;
	private static int ticks;
	private static long cleanedAt;
	private static volatile long diskBytes = -1;
	private static long diskAt;

	private FakeChunks() {}

	public static boolean enabled() {
		return ModSettings.get().keepChunks && RichPresenceModClient.featuresActive();
	}

	private static boolean saving() {
		return enabled() && ModSettings.get().keepChunksSave;
	}

	private static int clientRadius() {
		int range = Math.min(MAX_RANGE, ModSettings.get().keepChunksRange);
		return range > 0 ? range : Minecraft.getInstance().options.renderDistance().get();
	}

	public static int viewDistance(int vanilla) {
		return enabled() ? Math.max(vanilla, clientRadius()) : vanilla;
	}

	private static int wanted(int server) {
		return enabled() ? Math.max(server, clientRadius()) : server;
	}

	public static int storageRadius(int server) {
		serverRadius = server;
		applied = wanted(server);
		return applied;
	}

	public static boolean keep(ChunkPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		return enabled() && minecraft.player != null && minecraft.player.chunkPosition().getChessboardDistance(pos) <= clientRadius();
	}

	public static void onForget(ChunkPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (saving() && minecraft.level != null) {
			save(minecraft, minecraft.level.getChunkSource().getChunk(pos.getMinBlockX() >> 4, pos.getMinBlockZ() >> 4, ChunkStatus.FULL, false));
		}
	}

	public static void saveAround(Minecraft minecraft) {
		if (!saving() || minecraft.level == null || minecraft.player == null || serverRadius < 0) {
			return;
		}
		ChunkPos center = minecraft.player.chunkPosition();
		int cx = center.getMinBlockX() >> 4;
		int cz = center.getMinBlockZ() >> 4;
		for (int x = cx - serverRadius; x <= cx + serverRadius; x++) {
			for (int z = cz - serverRadius; z <= cz + serverRadius; z++) {
				save(minecraft, minecraft.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false));
			}
		}
	}

	private static String world() {
		WorldMaps.View view = WorldMaps.current();
		return view == null ? null : view.local();
	}

	private static long pack(int x, int z) {
		return x & 0xFFFFFFFFL | (z & 0xFFFFFFFFL) << 32;
	}

	private static long pack(ChunkPos pos) {
		return pack(pos.getMinBlockX() >> 4, pos.getMinBlockZ() >> 4);
	}

	private static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve("brilliant-server-mod").resolve("chunk-cache");
	}

	private static Path dir(String world) {
		return root().resolve(String.valueOf(SharedConstants.getProtocolVersion())).resolve(world);
	}

	private static Path file(String world, long pos) {
		return dir(world).resolve(ChunkPos.getX(pos) + "." + ChunkPos.getZ(pos) + ".bin");
	}

	private static void save(Minecraft minecraft, LevelChunk chunk) {
		ClientPacketListener connection = minecraft.getConnection();
		String world = world();
		if (chunk == null || connection == null || world == null || minecraft.level == null) {
			return;
		}
		byte[] raw;
		if (allAir(chunk)) {
			return;
		}
		ByteBuf buffer = Unpooled.buffer();
		try {
			ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, minecraft.level.getLightEngine(), null, null);
			ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(new RegistryFriendlyByteBuf(buffer, connection.registryAccess()), packet);
			raw = new byte[buffer.readableBytes()];
			buffer.readBytes(raw);
		} catch (Exception e) {
			return;
		} finally {
			buffer.release();
		}
		long pos = pack(chunk.getPos());
		index(world).add(pos);
		IO.execute(() -> {
			try {
				Path target = file(world, pos);
				Files.createDirectories(target.getParent());
				Path temp = target.resolveSibling(target.getFileName() + ".tmp");
				Files.write(temp, deflate(raw));
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception ignored) {
			}
		});
	}

	private static Set<Long> index(String world) {
		return INDEX.computeIfAbsent(world, key -> {
			Set<Long> set = ConcurrentHashMap.newKeySet();
			IO.execute(() -> {
				try (var files = Files.list(dir(key))) {
					files.forEach(path -> {
						String[] parts = path.getFileName().toString().split("\\.");
						if (parts.length == 3 && parts[2].equals("bin")) {
							try {
								set.add(pack(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
							} catch (NumberFormatException ignored) {
							}
						}
					});
				} catch (Exception ignored) {
				}
			});
			return set;
		});
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level != null && serverRadius >= 0 && wanted(serverRadius) != applied) {
			minecraft.level.getChunkSource().updateViewRadius(serverRadius);
		}
		apply(minecraft);
		if (!enabled() || minecraft.level == null || minecraft.player == null || serverRadius < 0 || ++ticks % 10 != 0) {
			return;
		}
		if (saving()) {
			if (System.currentTimeMillis() - cleanedAt > CLEANUP_MS) {
				cleanedAt = System.currentTimeMillis();
				IO.execute(FakeChunks::cleanup);
			}
			loadFromDisk(minecraft);
		}
		fillGaps(minecraft);
	}

	static long[] ring(int cx, int cz, int r) {
		long[] out = new long[r * 8];
		int n = 0;
		for (int i = -r; i < r; i++) {
			out[n++] = pack(cx + i, cz - r);
			out[n++] = pack(cx + r, cz + i);
			out[n++] = pack(cx - i, cz + r);
			out[n++] = pack(cx - r, cz - i);
		}
		return out;
	}

	private static boolean allAir(LevelChunk chunk) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (!section.hasOnlyAir()) {
				return false;
			}
		}
		return true;
	}

	private static boolean missing(ClientLevel level, long pos) {
		LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos), ChunkStatus.FULL, false);
		return chunk == null || allAir(chunk);
	}

	private static void loadFromDisk(Minecraft minecraft) {
		String world = world();
		if (world == null) {
			return;
		}
		Set<Long> saved = index(world);
		ChunkPos center = minecraft.player.chunkPosition();
		int budget = Math.max(1, ModSettings.get().keepChunksRate);
		int radius = clientRadius();
		for (int r = serverRadius + 1; r <= radius && budget > 0; r++) {
			for (long pos : ring(center.getMinBlockX() >> 4, center.getMinBlockZ() >> 4, r)) {
				if (budget > 0 && saved.contains(pos) && !PENDING.contains(world + pos) && missing(minecraft.level, pos)) {
					budget--;
					read(world, pos);
				}
			}
		}
	}

	private static void fillGaps(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null) {
			return;
		}
		ChunkPos center = minecraft.player.chunkPosition();
		int cx = center.getMinBlockX() >> 4;
		int cz = center.getMinBlockZ() >> 4;
		int far = 0;
		for (int r = clientRadius(); r > serverRadius + 1 && far == 0; r--) {
			for (long pos : ring(cx, cz, r)) {
				if (!missing(level, pos)) {
					far = r;
					break;
				}
			}
		}
		int budget = PLACEHOLDERS_PER_STEP;
		for (int r = serverRadius + 1; r < far && budget > 0; r++) {
			for (long pos : ring(cx, cz, r)) {
				if (budget > 0 && level.getChunkSource().getChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos), ChunkStatus.FULL, false) == null) {
					budget--;
					placeholder(level, connection, ChunkPos.getX(pos), ChunkPos.getZ(pos));
				}
			}
		}
	}

	private static void placeholder(ClientLevel level, ClientPacketListener connection, int x, int z) {
		LevelChunk empty = new LevelChunk(level, new ChunkPos(x, z));
		connection.handleLevelChunkWithLight(new ClientboundLevelChunkWithLightPacket(empty, level.getLightEngine(), null, null));
		if (level.dimensionType().hasSkyLight()) {
			LevelLightEngine light = level.getLightEngine();
			for (int y = light.getMinLightSection(); y < light.getMinLightSection() + light.getLightSectionCount(); y++) {
				light.queueSectionData(LightLayer.SKY, SectionPos.of(x, y, z), new DataLayer(15));
				level.setSectionDirtyWithNeighbors(x, y, z);
			}
		}
	}

	private static void read(String world, long pos) {
		PENDING.add(world + pos);
		IO.execute(() -> {
			try {
				Path path = file(world, pos);
				byte[] data = inflate(Files.readAllBytes(path));
				Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
				READY.add(new Loaded(world, pos, data));
			} catch (Exception e) {
				forget(world, pos);
			}
		});
	}

	private static void forget(String world, long pos) {
		index(world).remove(pos);
		PENDING.remove(world + pos);
		IO.execute(() -> {
			try {
				Files.deleteIfExists(file(world, pos));
			} catch (Exception ignored) {
			}
		});
	}

	private static void apply(Minecraft minecraft) {
		Loaded loaded;
		while ((loaded = READY.poll()) != null) {
			PENDING.remove(loaded.world() + loaded.pos());
			ClientPacketListener connection = minecraft.getConnection();
			ClientLevel level = minecraft.level;
			if (!saving() || connection == null || level == null || !loaded.world().equals(world()) || minecraft.player == null
					|| !missing(level, loaded.pos())
					|| minecraft.player.chunkPosition().getChessboardDistance(new ChunkPos(ChunkPos.getX(loaded.pos()), ChunkPos.getZ(loaded.pos()))) <= serverRadius) {
				continue;
			}
			try {
				ClientboundLevelChunkWithLightPacket packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(
						new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(loaded.data()), connection.registryAccess()));
				connection.handleLevelChunkWithLight(packet);
			} catch (Exception e) {
				forget(loaded.world(), loaded.pos());
			}
		}
	}

	private static byte[] deflate(byte[] raw) {
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		deflater.setInput(raw);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 64);
		byte[] chunk = new byte[16384];
		while (!deflater.finished()) {
			out.write(chunk, 0, deflater.deflate(chunk));
		}
		deflater.end();
		return out.toByteArray();
	}

	private static byte[] inflate(byte[] packed) throws Exception {
		Inflater inflater = new Inflater();
		inflater.setInput(packed);
		ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
		byte[] chunk = new byte[16384];
		try {
			while (!inflater.finished()) {
				int n = inflater.inflate(chunk);
				if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					throw new IllegalStateException("truncated");
				}
				out.write(chunk, 0, n);
				if (out.size() > 16 * 1024 * 1024) {
					throw new IllegalStateException("too large");
				}
			}
		} finally {
			inflater.end();
		}
		return out.toByteArray();
	}

	private record Entry(Path path, long size, long modified) {}

	private static void cleanup() {
		ModSettings.Data s = ModSettings.get();
		long maxAge = s.keepChunksDays > 0 ? s.keepChunksDays * 86_400_000L : Long.MAX_VALUE;
		long limit = Math.max(64, s.keepChunksDiskMb) * 1024L * 1024L;
		List<Entry> entries = new ArrayList<>();
		try (var files = Files.walk(root())) {
			files.filter(path -> path.getFileName().toString().endsWith(".bin")).forEach(path -> {
				try {
					entries.add(new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
				} catch (Exception ignored) {
				}
			});
		} catch (Exception e) {
			return;
		}
		entries.sort(Comparator.comparingLong(Entry::modified));
		long total = entries.stream().mapToLong(Entry::size).sum();
		long now = System.currentTimeMillis();
		ArrayDeque<Entry> queue = new ArrayDeque<>(entries);
		while (!queue.isEmpty() && (total > limit || now - queue.peek().modified() > maxAge)) {
			Entry entry = queue.poll();
			try {
				Files.deleteIfExists(entry.path());
				total -= entry.size();
			} catch (Exception ignored) {
			}
		}
		if (queue.size() != entries.size()) {
			INDEX.clear();
		}
		diskBytes = total;
	}

	public static String diskLabel() {
		long now = System.currentTimeMillis();
		if (now - diskAt > 5000) {
			diskAt = now;
			IO.execute(() -> {
				try (var files = Files.walk(root())) {
					diskBytes = files.filter(Files::isRegularFile).mapToLong(path -> path.toFile().length()).sum();
				} catch (Exception e) {
					diskBytes = 0;
				}
			});
		}
		long bytes = diskBytes;
		return bytes < 0 ? "計算中⋯" : bytes < 1024 * 1024 ? String.format("%.0f KB", bytes / 1024.0) : String.format("%.1f MB", bytes / 1024.0 / 1024.0);
	}

	public static void clear() {
		INDEX.clear();
		PENDING.clear();
		READY.clear();
		IO.execute(() -> {
			try (var files = Files.walk(root())) {
				files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
			} catch (Exception ignored) {
			}
			diskBytes = 0;
		});
	}

	public static String rangeLabel(int range) {
		return range > 0 ? Math.min(MAX_RANGE, range) + " 區塊" + (range > 32 ? "（較吃效能）" : "") : "跟隨顯示距離";
	}

	public static String diskLimitLabel(int mb) {
		return mb >= 1024 ? String.format("%.1f GB", mb / 1024.0) : mb + " MB";
	}

	public static void main(String[] args) throws Exception {
		byte[] raw = new byte[200_000];
		for (int i = 0; i < raw.length; i++) {
			raw[i] = (byte) (i % 7 == 0 ? i : 0);
		}
		byte[] packed = deflate(raw);
		check(packed.length < raw.length / 2 && java.util.Arrays.equals(inflate(packed), raw), "deflate round trip");
		boolean rejected = false;
		try {
			inflate(java.util.Arrays.copyOf(packed, packed.length / 2));
		} catch (Exception e) {
			rejected = true;
		}
		check(rejected, "truncated file is rejected");
		long[] around = ring(0, 0, 2);
		check(around.length == 16 && java.util.Arrays.stream(around).distinct().count() == 16
				&& java.util.Arrays.stream(around).allMatch(p -> Math.max(Math.abs(ChunkPos.getX(p)), Math.abs(ChunkPos.getZ(p))) == 2), "ring covers each border chunk once");
		check(ChunkPos.getX(pack(-5, 7)) == -5 && ChunkPos.getZ(pack(-5, 7)) == 7 && pack(new ChunkPos(-5, 7)) == pack(-5, 7), "chunk key matches vanilla");
		System.out.println("ok");
	}

	private static void check(boolean ok, String name) {
		if (!ok) {
			throw new AssertionError(name);
		}
	}
}
