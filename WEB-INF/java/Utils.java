import com.github.axet.vget.VGet;
import java.io.*;
import java.net.*;
import java.util.*;

public final class Utils implements Consts{

    static BigInteger p; 
    static BigInteger q;
    static BigInteger n;
    static BigInteger e;
    static BigInteger d;
    static BigInteger phi;
    static BigInteger minN = new BigInteger("4951760157141521099596496896"); // 2^92 bigger than that is secure if choose of numbers is secure
    static Random r = new Random(); //TODO (res) is that secure?
    String testMessage = "diophantus";
   
        static {
        e = new BigInteger(92, r);
        while(e.compareTo(BigInteger.ZERO)  == 0) e = new BigInteger(92, r);
        p = new BigInteger(92, r).nextProbabilyPrime();
        q = minN.divide(p).nextProbabilyPrime();
        n = p.multiply(q);
        phi = q.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));

        BigInteger k = new BigInteger(1);
        BigDecimal[] divideAndRemainder = new BigDecimal[2];
        while(divideAndRemainder[1] == null || divideAndRemainder[1].compare(BigDecimal.ZERO) != 0) {
            divideAndRemainder = new BigDecimal(phi.multiply(k).add(BigInteger.ONE)).divideAndRemainder(e, new MathContext(20));
            k.add(BigInteger.ONE);
        }
    
        if(!new String(publicKeyDecryption(publicKeyEncryption(testMessage.getBytes()))).equals(testMessage)) {
            // test, and ensure that probabily primes are primes
            throw new Exception("bad Algorithms"); //TODO (try) does that need try, catch (in static init) if yes use Error|RuntimeError
        }
    }

    public static String toBase64(String m) {
        return Base64.getEncoder().encodeToString(m.getBytes());
    }

    public static String toBase64Uri(String m) {
        return Base64.getEncoder().encodeToString(m.getBytes()).replace("+", "-").replace("/", "_").replace("=", "");
    }

    public static byte[] toBase64Bytes(String m) {
        return Base64.getEncoder().encode(m.getBytes());
    }

    public static String base64ToString(String s){
        return new String(Base64.getDecoder().decode(s.getBytes()));
    }

    public static String base64UriToString(String s){
        // Java URL decoder replace (+, -), (/, _) as in client side but uses pad (=), but in client side (=) replaced with ('')
        // jave to pad first with (=) before encoding
        // note ratio of base64 to utf-8 chars = 4/3
        StringBuilder paddedString = new StringBuilder(s);
        int numberOfEqualCharsToAppend = (4 - (s.length()%4));
        for(int i = 0; i < numberOfEqualCharsToAppend; i++)
            paddedString.append("=");

        return new String(Base64.getDecoder().decode(paddedString.toString().getBytes()));
    }

    public static byte[] base64ToBytes(String s) {
        return Base64.getDecoder().decode(s.getBytes());
    }

    public static Object[] downloadSong(String urlString) {
        VGet v = null;
        String name = "";
        Object[] objs = null;
        byte[] bytes;
        URL url = null;
        try{
            url = new URL("https://www.youtube.com/watch?v="+urlString);
            File f = new File(SONGS_PATH);
            if(!f.exists()){
                f.mkdirs();
            }
            v = new VGet(url, f);
            v.download();
            bytes = v.getFileName().getBytes();
            name = new String(bytes);
            System.out.println("song "+name+" downloaded");
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

    public static byte[] changeStringSize(String name, int size){
    	byte[] oldBytes = name.getBytes(); //TODO fix in vget lib
    	byte[] newBytes = new byte[size];
        int oldSize = oldBytes.length;

        if(size <= oldSize)
    	    for(int i = 0 ; i < size; i++) 
                newBytes[i] = oldBytes[i];
    	else {
    		for(int i = 0; i < oldSize; i++)
    			newBytes[i] = oldBytes[i];
            for(int i = oldSize; i < size; i++)
            	newBytes[i] = (byte) 0x20; //ascii space.
    	}
        return newBytes;
    }

    public static byte[] mergeBytes(byte[] byte1, byte[] byte2) {
        int len1 = byte1.length;
        int len2 = byte2.length;
        byte[] newBytes = new byte[len1+len2];
        for(int i = 0; i < len1; i++)
            newBytes[i] = byte1[i];
        for(int i = 0; i < len2; i++)
            newBytes[i+len1] = byte2[i];
        return newBytes;
    }

    public static byte[] intToBytes(int x) {
        byte[] bytes = new byte[4];
        for(int i = 0; i < 4; i++)
            bytes[i] = (byte) ( x >> 8*i);
        return bytes;
    }

    public static byte[] longToBytes(long x) {
        byte[] bytes = new byte[4];
        for(int i = 0; i < 8; i++)
            byte[i] = (byte) (x >>8*i);
        return bytes;
    }

    public static int bytesToInt(byte[] intBytes) {
        int x = 0;
        for(int i = 0; i < 4; i++)
            x |= (intBytes[i] & 0xff) << 8*i;
        return x;
    }
    
    public static int bytesToBigEndianInt(byte[] intBytes) {
        int x = 0;
        for(int i = 3; i >= 0; i--)
            x |= (intBytes[i] & 0xff) << 8*i;
        return x;
    }

    public static String formatBytes(long len) {
        float m = 0;
        return len/1024+" K "+ (( (m = (((float)len)/(1024*1024)) ) > 0.1)?(String.format("%.2f", m)+" M "):"");
    }

    public static byte[] publicKeyEncryption (byte[] bytes) {
        BigInteger m = new BigInteger(new String(padMessage(bytes, FORWARD)));
        return m.modPow(e, n).toByteArray();
    }

    public static byte[] publicKeyEncryption (int id) {
        BigInteger m = new BigInteger(id);
        return m.modPow(e, n).toByteArray();
    }

    public static byte[] publicKeyDecryption (byte[] c) {
        BigInteger c = new BigInteger(c);
        return padMessage(c.modPow(d, n).toByteArray(), BACKWARD);
    }

    public static int publicKeyDecryption (byte[] c) {
        BigInteger c = new BigInteger(c);
        return bytesToBigEndianInt(padMessage(c.modPow(d, n).toByteArray(), BACKWARD));
    }

    private byte[] padMessage (byte[] bytes, boolean operation) {
        StringBuilder sb = new StringBuilder();
        if (operation == FORWARD) {
            for (byte b : bytes) {
                if (b < 100) sb.append("0");
                if (b < 10) sb.append("0");
                sb.append(b);
            }
            return sb.toString().getBytes();
        } else {
            String c = new String(bytes);
            byte[] bytes = new byte[c.length()];
            for(int i = 0; i < c.length(); i++) {
                bytes[i] = (byte) Integer.parseInt(c.substring(i, i+3).replace("0", ""));
                i +=2;
            }
            return bytes; 
        }
    }

}