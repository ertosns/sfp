import java.io.*;
import java.util.*;
import java.net.*;
import java.util.logging.*;

//TODO after song received at Client send, client should send acq flag, and clean up in server side|attach timestap with seach file for cashe
public class Listener implements Runnable, Consts{

    //TODO register devices to support multiple devices.    
    Database database = null;
    Logger l =  Logger.getLogger(this.toString());

    Handler handler = new ConsoleHandler();
    byte[] bytes = new byte[0];
    File songFile = null;
    Socket sock;

    public Listener(Socket socket) {
        sock = socket;

        //TODO init one object of the following, (res) doens't database methodes need to be synchronized?
        l.setUseParentHandlers(false);
        l.setLevel(Level.INFO);
        handler.setLevel(Level.INFO);
        l.addHandler(handler);
        
        database = new Database();
    }

    @Override 
    public void run() {
        listen(sock);
    }

    public void listen(Socket so) {
        	
    		try {
    			InputStream is = so.getInputStream();
    			OutputStream os = so.getOutputStream();
                byte[] uniqueIdSizeBytes = new byte[4];
                is.read(uniqueIdSizeBytes);
                int uniqueIdBytesSize = Utils.bytesToInt(uniqueIdSizeBytes);
                byte[] uniqueIdBytes = new byte[uniqueIdBytesSize];
                is.read(uniqueIdBytes);
                String encodedId = new String(uniqueIdBytes);
                l.info("encodedId = "+encodedId);
     			int id = extractId(Utils.base64ToString(encodedId));
                //TODO authenticat user
                write(intToBytes(W8_INTERVAL)); //TODO make interval react to server load.
                String url;
                if((url = database.getSongUrlId(id)) != null) {
                	//TODO res send bytes while reading vs sending buffer.
                    l.info("client found");
                	write(HAS_SONG_FLAG);
                	sendSong(url);
                    os.write(bytes);
                    l.info("song is sent");

                    boolean success = (is.read() == 1)? true:false;
                    if(success) {
                        l.info("song received successfully, cleaning up");
                        clean(id); //TODO or add timestame to song for cashe.
                    }
                }
                else {
                    write(HAS_NO_SONG_FLAG);
                    os.write(bytes);
                }

    		} catch(Exception e) {
    			e.printStackTrace();
    		} finally {
                bytes = new byte[0];
            }
    }

    public void clean (int id) {
        // clean song in songs file |TODO stick timestamp for cashe
        // remove song from database.
        try {
            if(songFile != null) {
                songFile.delete();
                l.info("song file deteted");
            }
            database.removeSong(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	private void sendSong(String url) {
		//100b(fileName)|4b(fileSize(Z))|Zb(song)
		Object[] result = Utils.downloadSong(url);
        String songNameStr = (String)result[1];
		byte[] fileName = songNameStr.getBytes();
        songFile = (File) result[0];
        int size = (int) songFile.length();
        byte[] song = new byte[size];
		
        try (FileInputStream fis = new FileInputStream(songFile)) {
            
            fis.read(song);
            int songNameBytesLen = fileName.length;
            write(intToBytes(songNameBytesLen)); 
            l.info("#1# songNameSize wrote: "+songNameBytesLen);
            write(fileName); 
            l.info("#2# fileName wrote: "+songNameStr);
            write(intToBytes(size)); 
            l.info("#3# songSize wrote: "+size+"b");
            write(song); 
            l.info("#4# song wrote, bytes len = "+bytes.length);
            
        } catch(Exception e) { 
                e.printStackTrace();
                return;  
        }
    }

    private void write(byte[] extraBytes) {
        int oldLen = bytes.length;
        int extraLen = extraBytes.length;
        int len = oldLen+extraLen;
        byte[] newBytes = new byte[len];
        for(int i = 0; i < oldLen; i++)
            newBytes[i] = bytes[i];
        for(int i = 0; i < extraLen; i++)
            newBytes[i+oldLen] = extraBytes[i];
        bytes = newBytes;
    }

    private byte[] intToBytes(int size) {
    	byte[] bytes = new byte[4];
    	for(int i = 0; i < 4; i++)
    		bytes[i] = (byte) (size >> 8*i);
    	return bytes;
    }

    private int extractId(String passId) { 
        int id = Integer.parseInt(passId.split(ID_SPLITER)[2]);
        l.info("extractedId = "+id);
        return id;
    }

    public static void main(String[] args) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(PORT), MAX_BACK_LOG);
        } catch (IOException io) {
            io.printStackTrace();
            return;
        }

        while(true) {
            try {
                //TODO prevent security hole (DOS) if thread handling specific song for specific id, don't accept similar request.(mark song in db, which require database methods syncronized)        
                Socket so = ss.accept();
                System.out.println("sock accepted");
                new Thread(new Listener(so)).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}