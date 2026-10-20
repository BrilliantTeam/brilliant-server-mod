package net.brilliantw.richpresence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ReauthTest {
	private static String jwt(String payload) {
		Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
		return enc.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8)) + "." + enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".sig";
	}

	@Test
	void readsExpiryFromMinecraftToken() {
		assertEquals(1_800_000_000_000L, Reauth.expiresAt(jwt("{\"exp\":1800000000,\"xuid\":\"1\"}")));
		assertEquals(0, Reauth.expiresAt(jwt("{\"xuid\":\"1\"}")));
		assertEquals(0, Reauth.expiresAt("0"));
		assertEquals(0, Reauth.expiresAt(null));
	}

	@Test
	void upsertsLauncherAccountKeepingOtherFields() {
		com.google.gson.JsonArray accounts = com.google.gson.JsonParser.parseString(
				"[{\"id\":\"off\",\"kind\":\"offline\"},{\"id\":\"0123456789abcdef0123456789abcdef\",\"kind\":\"msa\",\"xuid\":\"9\",\"sessionError\":\"expired\",\"extra\":1}]").getAsJsonArray();
		java.util.UUID uuid = java.util.UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
		Reauth.upsertLauncherAccount(accounts, new Reauth.Session("Rice", uuid, "at", "rt", ""), 42);
		assertEquals(2, accounts.size());
		com.google.gson.JsonObject msa = accounts.get(1).getAsJsonObject();
		assertEquals("rt", msa.get("refreshToken").getAsString());
		assertEquals("9", msa.get("xuid").getAsString());
		assertEquals("", msa.get("sessionError").getAsString());
		assertEquals(1, msa.get("extra").getAsInt());
		assertEquals(42, msa.get("expiresAt").getAsLong());
		Reauth.upsertLauncherAccount(accounts, new Reauth.Session("New", java.util.UUID.randomUUID(), "a", "r", "x"), 1);
		assertEquals(3, accounts.size());
	}

	@Test
	void rejectsWrongPastedUrl() {
		assertEquals("貼上的不是登入完成後的網址", Reauth.completeLogin("https://example.com/?code=x"));
		assertEquals("網址已過期，請重新按一次登入", Reauth.completeLogin(Reauth.REDIRECT_URI + "?code=x&state=nope"));
	}
}
