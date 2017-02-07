package err;
import com.github.axet.vget.VGet;
import java.io.*;
import java.net.*;
import java.util.*;
import java.math.*;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.security.*;

public final class Utils implements Consts {

    static Log l = new Log("Utils", Log.CONSOLE);
    static PublicKey pk = null;
    static PrivateKey prk = null;
    static Cipher c = null;
    static String KEY_FILES_PARENT = "../../../keys/";
    static String PUBLIC_KEY_FILE = KEY_FILES_PARENT+"pk";
    static String PRIVATE_KEY_FILE = KEY_FILES_PARENT+"prk";

    static {
        final File publicKeyFile = new File(PUBLIC_KEY_FILE);
        final File privateKeyFile = new File(PRIVATE_KEY_FILE);
        try {
            if (!(publicKeyFile.exists() && privateKeyFile.exists())) {

                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(1024);
                KeyPair key = keyGen.generateKeyPair();
                
                if (publicKeyFile.getParentFile() != null) {
                    publicKeyFile.getParentFile().mkdirs();
                    publicKeyFile.createNewFile();
                }
                ObjectOutputStream pkoos = new ObjectOutputStream( new FileOutputStream(publicKeyFile));
                pkoos.writeObject(key.getPublic());
                pkoos.close();

                if (privateKeyFile.getParentFile() != null) {
                    privateKeyFile.getParentFile().mkdirs();
                    privateKeyFile.createNewFile();
                }
                ObjectOutputStream prkoos = new ObjectOutputStream( new FileOutputStream(privateKeyFile));
                prkoos.writeObject(key.getPrivate());
                prkoos.close();
            
            }
            pk = (PublicKey) new ObjectInputStream(new FileInputStream(publicKeyFile)).readObject();
            prk = (PrivateKey) new ObjectInputStream(new FileInputStream(privateKeyFile)).readObject();
            c = Cipher.getInstance("RSA/ECB/PKCS1PADDING");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] publicKeyEncryption (byte[] message) throws Exception {        
        c.init(Cipher.ENCRYPT_MODE, pk);
        return c.doFinal(message);
    }

    public static byte[] publicKeyEncryption (int id) throws Exception {
        return publicKeyEncryption(intToBytes(id));
    }

    public static byte[] publicKeyDecryption (byte[] cipher) throws Exception {
        l.info("cipher ", new String(cipher), "with size ", formatBytes(cipher.length));   
        c.init(Cipher.DECRYPT_MODE, prk);
        return c.doFinal(cipher);
    }

    public static int publicKeyDecryptionToInt (byte[] cipher) throws Exception {
        return bytesToInt(publicKeyDecryption(cipher));
    }

    public static String toBase64(String m) 
    {
        return Base64.getEncoder().encodeToString(m.getBytes());
    }

    public static String toBase64Uri(String m) 
    {
        return Base64.getEncoder().encodeToString(m.getBytes()).replace("+", "-").replace("/", "_").replace("=", "");
    }

    public static byte[] toBase64Bytes(String m) 
    {
        return Base64.getEncoder().encode(m.getBytes());
    }

    public static byte[] base64ToBytes(String s) 
    {
        return Base64.getDecoder().decode(s.getBytes());
    }

    public static String base64ToString(String s) 
    {
        return new String(Base64.getDecoder().decode(s.getBytes()));
    }

    public static String base64UriToString(String s) 
    {
        // Java URL decoder replace (+, -), (/, _) as in client side but uses pad (=), but in client side (=) replaced with ('')
        // jave to pad first with (=) before encoding
        // note ratio of base64 to utf-8 chars = 4/3
        StringBuilder paddedString = new StringBuilder(s);
        int numberOfEqualCharsToAppend = 3 - s.length()%3;

        //for (int i = 0; i < numberOfEqualCharsToAppend; i++)
          //  paddedString.append("=");
        l.info("base64UriToString after padding: ", paddedString.toString());
        l.info("byse64UriToBinary after padding: ", Utils.toBinary(paddedString.toString().getBytes()));
        return new String(Base64.getUrlDecoder().decode(paddedString.toString().getBytes()));
    }

    public static Object[] downloadSong(String urlString) 
    {
        VGet v = null;
        String name = "";
        Object[] objs = null;
        byte[] bytes;
        URL url = null;
        try
        {
            url = new URL("https://www.youtube.com/watch?v="+urlString);
            File f = new File(SONGS_PATH);
            if(!f.exists()){
                f.mkdirs();
            }
            v = new VGet(url, f);
            v.download();
            bytes = v.getFileName().getBytes();
            name = new String(bytes);
            System.out.println("song "+name+" downloaded\n");
            objs = new Object[2];
            String path = SONGS_PATH+new String(bytes);
            byte[] pathBytes = path.getBytes();
            objs[0] = new File(new String(pathBytes));
            objs[1] = name;
        } catch(Exception e) { 
            e.printStackTrace(); 
        }
        return objs; 
    }

    public static byte[] mergeBytes(byte[] byte1, byte[] byte2) 
    {
        int len1 = byte1.length;
        int len2 = byte2.length;
        byte[] newBytes = new byte[len1+len2];
        for(int i = 0; i < len1; i++)
            newBytes[i] = byte1[i];
        for(int i = 0; i < len2; i++)
            newBytes[i+len1] = byte2[i];
        return newBytes;
    }

    public static byte[] intToBytes(int x) 
    {
        byte[] bytes = new byte[4];
        for(int i = 0; i < 4; i++)
            bytes[i] = (byte) ( x >> 8*i);
        return bytes;
    }

    public static byte[] longToBytes(long x) 
    {
        byte[] bytes = new byte[8];
        for(int i = 0; i < 8; i++)
            bytes[i] = (byte) (x >> i*8);
        return bytes;
    }

    public static long bytesToLong(byte[] intBytes) 
    {
        long x = 0;
        for(int i = 0; i < 8; i++)
            x |= ((long) (intBytes[i] & 0xff)) << 8*i;
        return x;
    }

    public static int bytesToInt(byte[] intBytes) 
    {
        int x = 0;
        for(int i = 0; i < 4; i++)
            x |= (intBytes[i] & 0xff) << 8*i;
        return x;
    }

    public static String bytesToHex(byte[] bytes) 
    {
        if(bytes.length == 0) return "";
        return new BigInteger(bytes).toString(16);
    }

    public static String toBinary(byte[] bytes)
    {   //first byte is the most left string byte
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < bytes.length*Byte.SIZE; i++) {
            sb.append((bytes[i/Byte.SIZE] << i % Byte.SIZE  & 0x80) == 0?'0':'1');
            if (i%Byte.SIZE == 0 && i != 0) sb.append(" ");
        }
        return sb.toString();
    }

    public static void writeToStream(byte[] bytes, ByteArrayOutputStream stream) 
    {
        int len = bytes.length;
        stream.write(intToBytes(len), 0, 4);
        stream.write(bytes, 0, len);
    }

    public static String formatBytes(long len) 
    {
        float m = 0;
        if(len < 1024) return len+"b";
        return len/1024+" K "+ (( (m = (((float)len)/(1024*1024)) ) > 0.1)?(String.format("%.2f", m)+" M "):"");
    }
}