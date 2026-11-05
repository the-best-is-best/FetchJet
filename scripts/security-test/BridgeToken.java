// Prints the desktop bridge token from ~/.fetch_jet/bridge.json (macOS test helper, TESTING.md).
//   java scripts/security-test/BridgeToken.java "$(security find-generic-password -s FetchJet -a secrets-key -w)"
// The key comes from the login Keychain; only works for the user who runs FetchJet.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BridgeToken {
    public static void main(String[] args) throws Exception {
        byte[] key = HexFormat.of().parseHex(args[0].trim());
        String json = Files.readString(Path.of(System.getProperty("user.home"), ".fetch_jet/bridge.json"));
        String sealed = json.replaceAll("(?s).*\"token\": *\"fjenc1:([^\"]+)\".*", "$1");
        byte[] b = Base64.getDecoder().decode(sealed);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, b, 0, 12));
        System.out.print(new String(c.doFinal(b, 12, b.length - 12)));
    }
}
