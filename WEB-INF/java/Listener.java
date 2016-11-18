import java.io.*;
import java.util.*;
import java.net.*;

public class Listener implements Consts{

    //TODO register devices to support multiple devices.
    boolean LISTEN = true;
    Database database = null;
    public void listen() {
        database = new Database();
    	while(LISTEN) {
    		try (ServerSocket ss = new ServerSocket()) {
    			ss.setReuseAddress(true);
    			ss.bind(new InetSocketAddress(PORT), MAX_BACK_LOG);
    			Socket so = ss.accept();

    			InputStream is = so.getInputStream();
    			OutputStream os = so.getOutputStream();
                os.write(intToBytes(W8_INTERVAL)); //TODO make interval react to server load.

    			byte[] idBytes = new byte[8];
    			is.read(idBytes);
    			int id = extractId(Utils.base64ToString(new String(idBytes)));
                
                String url;
                if((url = database.getSongUrlId(id)) != null) {
                	//TODO res send bytes while reading vs sending buffer.
                	os.write(HAS_SONG_FLAG);
                	sendSong(url, os);
                }
                else os.write(HAS_NO_SONG_FLAG);

    		} catch(IOException io) {
    			io.printStackTrace();
    		}
    	}
    }

    public void stop() {
        LISTEN = false;
    }

	private void sendSong(String url, OutputStream os) {
		//100b(fileName)|4b(fileSize(Z))|Zb(song)
		Object[] result = Utils.downloadSong(url);
		byte[] fileName = Utils.changeStringSize((String)result[1], 100);
        File f = (File) result[0];
        int size = (int) f.length();
        byte[] song = new byte[size];
		
        try (FileInputStream fis = new FileInputStream(f)) {
            
            fis.read(song);
            os.write(fileName);
            os.write(intToBytes(size));
            os.write(song);

        } catch(Exception e) { 
                e.printStackTrace();  
        }
    }

    private byte[] intToBytes(int size) {
    	byte[] bytes = new byte[4];
    	for(int i = 0; i < 4; i++)
    		bytes[i] = (byte) (size >> 8*i);
    	return bytes;
    }

    private int extractId(String passId) { // [\\w]*10[[^0]|[0-9]+] , not sure about id part
        return Integer.parseInt(passId.substring(10));
    }

}