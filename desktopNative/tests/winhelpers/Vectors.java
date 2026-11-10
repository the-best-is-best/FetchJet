import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Vectors {
    static final HexFormat HEX = HexFormat.of();
    static final SecureRandom RANDOM = new SecureRandom();

    static byte[] seal(byte[] key, byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] body = cipher.doFinal(plain);
        byte[] out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return out;
    }

    static byte[] open(byte[] key, byte[] sealed) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, sealed, 0, 12));
        return cipher.doFinal(sealed, 12, sealed.length - 12);
    }

    static byte[] sign(PrivateKey key, byte[] data) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key);
        signer.update(data);
        return signer.sign();
    }

    static void gen(File dir) throws Exception {
        dir.mkdirs();
        Map<String, String> out = new LinkedHashMap<>();
        byte[] small = "FetchJet تجربة 🎵 vectors".getBytes(StandardCharsets.UTF_8);
        out.put("sha.input", HEX.formatHex(small));
        out.put("sha.expected", HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(small)));

        byte[] big = new byte[20 * 1024 * 1024 + 123];
        new java.util.Random(42).nextBytes(big);
        Files.write(new File(dir, "big.bin").toPath(), big);
        out.put("big.sha256", HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(big)));

        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        byte[] plain = "كلمة السر proxy-pässword 🎵 0123456789".getBytes(StandardCharsets.UTF_8);
        out.put("aes.key", HEX.formatHex(key));
        out.put("aes.plain", HEX.formatHex(plain));
        out.put("aes.sealed", HEX.formatHex(seal(key, plain)));
        out.put("aes.sealed.empty", HEX.formatHex(seal(key, new byte[0])));
        byte[] large = new byte[300_000];
        new java.util.Random(7).nextBytes(large);
        out.put("aes.large.sha256", HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(large)));
        Files.write(new File(dir, "aes-large.sealed").toPath(), seal(key, large));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair signing = generator.generateKeyPair();
        KeyPair other = generator.generateKeyPair();
        out.put("ecdsa.pub", Base64.getEncoder().encodeToString(signing.getPublic().getEncoded()));
        out.put("ecdsa.other.pub", Base64.getEncoder().encodeToString(other.getPublic().getEncoded()));
        out.put("ecdsa.sig.big", Base64.getEncoder().encodeToString(sign(signing.getPrivate(), big)));
        for (int i = 0; i < 40; i++) {
            byte[] message = ("message " + i).getBytes(StandardCharsets.UTF_8);
            out.put("ecdsa.many." + i, HEX.formatHex(message) + ":" + HEX.formatHex(sign(signing.getPrivate(), message)));
        }

        KeyPair agreement = generator.generateKeyPair();
        out.put("ecdh.java.pub", HEX.formatHex(agreement.getPublic().getEncoded()));
        Files.write(new File(dir, "ecdh-private.pk8").toPath(), agreement.getPrivate().getEncoded());

        byte[] secretKey = new byte[32];
        RANDOM.nextBytes(secretKey);
        String secretPlain = "site-pässword كلمة السر 🎵";
        out.put("secret.key", HEX.formatHex(secretKey));
        out.put("secret.key.b64", Base64.getEncoder().encodeToString(secretKey));
        out.put("secret.plain", HEX.formatHex(secretPlain.getBytes(StandardCharsets.UTF_8)));
        out.put("secret.stored", "fjenc1:" + Base64.getEncoder().encodeToString(seal(secretKey, secretPlain.getBytes(StandardCharsets.UTF_8))));

        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> e : out.entrySet()) text.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        Files.writeString(new File(dir, "vectors.txt").toPath(), text.toString());
        System.out.println("vectors written to " + dir);
    }

    static Map<String, String> load(File file) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file.toPath())) {
            int cut = line.indexOf('=');
            if (cut > 0) map.put(line.substring(0, cut), line.substring(cut + 1).trim());
        }
        return map;
    }

    static int fails = 0;

    static void check(String name, boolean ok) {
        if (!ok) fails++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
    }

    static void verify(File dir) throws Exception {
        Map<String, String> vectors = load(new File(dir, "vectors.txt"));
        Map<String, String> made = load(new File(dir, "native.txt"));
        byte[] key = HEX.parseHex(vectors.get("aes.key"));
        byte[] opened = open(key, HEX.parseHex(made.get("aes.sealed")));
        check("java opens what the native code sealed", java.util.Arrays.equals(opened, HEX.parseHex(vectors.get("aes.plain"))));
        byte[] openedEmpty = open(key, HEX.parseHex(made.get("aes.sealed.empty")));
        check("java opens a native sealed empty message", openedEmpty.length == 0);

        PrivateKey mine = KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(Files.readAllBytes(new File(dir, "ecdh-private.pk8").toPath())));
        PublicKey peer = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(HEX.parseHex(made.get("ecdh.native.pub"))));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(mine);
        agreement.doPhase(peer, true);
        byte[] derived = MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret());
        check("java derives the same ECDH key as the native code", HEX.formatHex(derived).equals(made.get("ecdh.native.derived")));

        byte[] secretKey = HEX.parseHex(vectors.get("secret.key"));
        String stored = made.get("secret.stored");
        byte[] revealed = open(secretKey, Base64.getDecoder().decode(stored.substring("fjenc1:".length())));
        check("java reveals a secret the native code protected", new String(revealed, StandardCharsets.UTF_8).equals("native secret سر 🎵"));
        System.out.println("java check: " + fails + " failed");
        if (fails > 0) System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("gen")) gen(new File(args[1]));
        else if (args.length == 2 && args[0].equals("check")) verify(new File(args[1]));
        else System.out.println("Vectors gen <dir> | check <dir>");
    }
}
