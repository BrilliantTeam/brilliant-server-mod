package net.brilliantw.richpresence;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

public final class UpdateTrust {
	private static final String PUBLIC_KEY = "lunfr7RSo4DILBeY6dlwBW8KpSFkbSMweYK0DLB/uMU=";
	private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

	private UpdateTrust() {}

	public static boolean verify(String file, String sha256Hex, String signature) {
		if (signature == null || signature.isBlank() || sha256Hex == null) {
			return false;
		}
		try {
			byte[] raw = Base64.getDecoder().decode(PUBLIC_KEY);
			byte[] spki = new byte[SPKI_PREFIX.length + raw.length];
			System.arraycopy(SPKI_PREFIX, 0, spki, 0, SPKI_PREFIX.length);
			System.arraycopy(raw, 0, spki, SPKI_PREFIX.length, raw.length);
			PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
			Signature verifier = Signature.getInstance("Ed25519");
			verifier.initVerify(key);
			verifier.update(("brilliant-update-v1\n" + file + "\n" + sha256Hex.toLowerCase()).getBytes(StandardCharsets.UTF_8));
			return verifier.verify(Base64.getDecoder().decode(signature.trim()));
		} catch (Exception e) {
			return false;
		}
	}
}
