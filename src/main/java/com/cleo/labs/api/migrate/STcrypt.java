package com.cleo.labs.api.migrate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public class STcrypt {

    private SecretKey            secret   = null;
    private IvParameterSpec      iv       = null;

    private static final Charset UTF_8    = Charset.forName("UTF-8"); // StandardCharsets.UTF_8 in Java 7
    private static final String  SHA256   = "SHA-256";
    private static final int     KEYBYTES = 16;  // AES 128
    private static final String  AES128   = "AES/CBC/PKCS5Padding";
    private static final String  HASHTAG  = "{SHA-256}";
    private static final String  KEYLABEL = "AES128";
    private static final String  KEYTAG   = "{"+KEYLABEL+"}";

    /**
     * Computes a SHA-256 password hash using the ST algorithm, which
     * includes some null padding on the end of the UTF-8 encoded string.
     * @param password the password to hash
     * @return the password hash byte array
     */
    public static byte[] hash(String password) {
        try {
            // .array() drags along an extra 10% of null padding in the buffer
            return MessageDigest.getInstance(SHA256).digest(UTF_8.encode(password).array());
        } catch (NoSuchAlgorithmException canthappen) {
            return new byte[0];
        }
    }

    /**
     * Matches a password against the encoded hash string.  The encoding should
     * be the base64 encoding of the {SHA-256} tag prefixed to the raw byte
     * hash of the password.
     * @param password the password to match
     * @param encoded the encoded string
     * @return true if the password matches the encoding
     */
    public static boolean match(String password, String encoded) {
        // base64 decode the encoded phrase
        byte[] decoded = DatatypeConverter.parseBase64Binary(encoded);
        String verify = new String(decoded, 0, HASHTAG.length());

        // make sure it starts with {SHA-256}
        if (!verify.equals(HASHTAG)) {
            return false;
        }

        // the remaining bytes should match the hash of the password
        return match(password, Arrays.copyOfRange(decoded, HASHTAG.length(), decoded.length));
    }

    /**
     * Matches a password against a raw byte hash by hashing the password
     * and checking for a match.
     * @param password the password to match
     * @param hash the raw byte hash
     * @return true if the password hashes to the same hash
     */
    public static boolean match(String password, byte[] hash) {
        return Arrays.equals(hash(password), hash);
    }

    
    /**
     * Initializes the ST encrypt/decrypt engine with an AES128 key and
     * Initialization Vector derived from the password.
     * @param password the password to seed the key and IV
     */
    public STcrypt(String password) {
        // replicate bytes to get 2*KEYBYTES bytes worth:
        // the first chunk is the secret key, the second chunk is the IV
        byte[] bytes = password.getBytes(UTF_8);
        byte[] key;
        if (bytes.length < 2*KEYBYTES) {
            key = new byte[2*KEYBYTES];
            for (int i=0; i<2*KEYBYTES; i+=bytes.length) {
                System.arraycopy(bytes, 0, key, i, Math.min(bytes.length, 2*KEYBYTES-i));
            }
        } else {
            key = bytes;
        }
        this.secret   = new SecretKeySpec(key, 0, KEYBYTES, "AES");
        this.iv       = new IvParameterSpec(key, KEYBYTES, KEYBYTES);
    }

    /**
     * Decrypts a tagged base64 encoded String, which is expected to start with
     * the {AES128} tag.
     * @param base64 the base64 encoded String
     * @return the decrypted String, or the original String if not tagged
     */
    public String decrypt(String base64) {
        if (base64.startsWith(KEYTAG)) {
            byte[] ciphertext = DatatypeConverter.parseBase64Binary(base64.substring(KEYTAG.length()));
            return decrypt(ciphertext);
        } else {
            return base64;
        }
    }

    /**
     * Decrypts a raw byte array.
     * @param ciphertext the raw encrypted binary data
     * @return the decrypted String
     */
    public String decrypt(byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(AES128);
            cipher.init(Cipher.DECRYPT_MODE, this.secret, this.iv);
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Encrypts a String into unencoded binary data.
     * @param plaintext the String to encrypt
     * @return the encrypted binary data
     */
    public byte[] encrypt(String plaintext) {
        try {
            byte[] plainbytes = plaintext.getBytes();
            Cipher cipher = Cipher.getInstance(AES128);
            cipher.init(Cipher.ENCRYPT_MODE, this.secret, this.iv);
            byte[] ciphertext = cipher.doFinal(plainbytes);
            return ciphertext;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A Pattern to isolate the base64 encoded encrypted data from XML tags:<br/>
     * &nbsp;&nbsp;&lt;tag&gt;{AES128}data&lt;/tag&gt;<br/>
     * using the &gt;&lt; as lookbehind/ahead context and capturing only the data.
     */
    private static final Pattern ENCRYPTED = Pattern.compile("(?<=>)\\{"+KEYLABEL+"\\}([a-zA-Z0-9+/]+=*)(?=<)");

    /**
     * When used as a main program, the {@code STcrypt} class acts as a
     * decrypting filter.  The single argument is the password used to protect
     * the ST export file.  Use as follows:<br/>
     * {@code java -cp cc-shell.jar com.cleo.labs.api.migrate.STcrypt password < in > out}
     * @param argv the password (exactly one argument required)
     */
    public static void main(String[] argv) {
        if (argv.length != 1) {
            System.err.println("usage: STcrypt passwd < input > output");
            System.exit(1);
        }
        try {
            STcrypt crypt = new STcrypt(argv[0]);
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter    out = new PrintWriter(System.out);
            String line;
            while ((line = in.readLine()) != null) {
                Matcher m = ENCRYPTED.matcher(line);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    m.appendReplacement(sb, crypt.decrypt(m.group(1)));
                }
                m.appendTail(sb);
                out.println(sb.toString());
            }
            out.close();
            in.close();
        } catch (IOException io) {
            System.err.println(io);
        }
    }
}