import com.github.axet.vget.VGet;
import java.io.*;
import java.net.*;
import java.util.*;

public final class Utils implements Consts{

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
            name = new String(bytes, "ISO-8859-1");
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

        if(newBytes.length <= oldSize)
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

    public static byte[] intToBytes(int x) {
        byte[] bytes = new byte[4];
        for(int i = 0; i < 4; i++)
            bytes[i] = (byte) ( x >> 8*i);
        return bytes;
    }

    public static int bytesToInt(byte[] intBytes) {
        int x = 0;
        for(int i = 0; i < 4; i++)
            x |= (intBytes[i] & 0xff) << 8*i;
        return x;
    }

}