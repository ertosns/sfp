import com.github.axet.vget.VGet;
import java.io.*;
import java.net.*;
import java.util.*;

public final class Utils implements Consts{

    public static String toBase64(String m) {
        return Base64.getEncoder().encodeToString(m.getBytes());
    }

    public static byte[] toBase64Bytes(String m) {
        return Base64.getEncoder().encode(m.getBytes());
    }

    public static String toBase64Url(String m) {
        return Base64.getUrlEncoder().encodeToString(m.getBytes());
    }

    public static byte[] toBase64UrlBytes(String m) {
        return Base64.getUrlEncoder().encode(m.getBytes());
    }

    public static String base64ToString(String s){
        return new String(Base64.getDecoder().decode(s.getBytes()));
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
            url = new URL(urlString);
            File f = new File(SONGS_PATH);
            if(!f.exists()){
                f.mkdirs();
            }
            v = new VGet(url, f);
            v.download();
            bytes = v.getFileName().getBytes();
            name = new String(bytes, "ISO-8859-1");
            objs = new Object[2];
            String path = SONGS_PATH+new String(bytes);
            byte[] pathBytes = path.getBytes();
            objs[0] = new File(new String(pathBytes));
            objs[1] = name;
        } catch(Exception e) { 
            e.printStackTrace(); 
        }

        // clean
        String[] files = new File(SONGS_PATH).list();
        for (int i = 0; i < files.length; i++)
            new File(files[i]).delete();
        
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

}