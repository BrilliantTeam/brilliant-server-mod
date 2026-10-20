package net.brilliantw.richpresence;

import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class RPConfig {
	private RPConfig() {}

	public static final long CLIENT_ID = 1014145002496065577L;

	public static final String MINECRAFT_VERSION = FabricLoader.getInstance().getModContainer("minecraft")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");

	public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer("brilliant-server-mod")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("0");

	public static final String USER_AGENT = "Brilliant-Server-Mod/" + MOD_VERSION + " (MC " + MINECRAFT_VERSION + ")";

	public static final String BASE_DOMAIN = "brilliantw.net";

	public static final Map<String, String> SERVER_LABELS = Map.ofEntries(
			Map.entry("brilliantw.net", "輝煌伺服器"),
			Map.entry("test.brilliantw.net", "輝煌伺服器"),
			Map.entry("ipv6.brilliantw.net", "輝煌伺服器"),
			Map.entry("s0.brilliantw.net", "輝煌伺服器｜時隙奉廳"),
			Map.entry("s1.brilliantw.net", "輝煌伺服器｜炒飯服"),
			Map.entry("s2.brilliantw.net", "輝煌伺服器｜書碼服"),
			Map.entry("s3.brilliantw.net", "輝煌伺服器｜羽森服"),
			Map.entry("s4.brilliantw.net", "輝煌伺服器｜恐龍服"),
			Map.entry("wt.brilliantw.net", "輝煌伺服器｜重啟等待")
	);

	public static final String LARGE_IMAGE_KEY = "brilliantirichicon";

	public static final String DEFAULT_IMAGE_KEY = "kapippi";

	public static final Map<String, String> SERVER_IMAGE_KEYS = Map.of(
			"s1.brilliantw.net", "komukumu",
			"s2.brilliantw.net", "hanawa",
			"s3.brilliantw.net", "konkon",
			"s4.brilliantw.net", "pikadon"
	);

	public static final String LARGE_IMAGE_TEXT = "brilliantw.net";
	public static final String SMALL_IMAGE_TEXT = "brilliantw.net";

	public static final String WEBSITE_LABEL = "加入伺服器";
	public static final String WEBSITE_URL = "https://www.brilliantw.net";
	public static final String LAUNCHER_URL = WEBSITE_URL + "/launcher/";
	public static final String LAUNCHER_VERSION = launcherVersion(System.getProperty("brilliant.launcher"));

	static String launcherVersion(String value) {
		return value != null && value.matches("[0-9]{1,4}(\\.[0-9]{1,4}){0,3}") ? value : null;
	}
}
