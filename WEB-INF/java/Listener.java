import java.io.*;
import java.util.*;
import java.net.*;
import java.util.logging.*;


public class Listener implements Runnable, Consts{

    
    Database database;
    byte[] bytes = new byte[0];
    File songFile = null;
    Socket sock;
    Log l;

    public Listener(Socket socket, Database db) {
        sock = socket;
        database = db;

        l = new Log(this.toString(), Log.CONSOLE);
        l.info("sock connected to "+sock.getInetAddress());
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
                l.info("encodedId in form of email/pass ", encodedId);                
                String[] idSegments = Utils.base64ToString(encodedId).split(ID_SPLITER); // {email, pass}

                l.info("writing interval ", W8_INTERVAL);
                write(intToBytes(W8_INTERVAL)); 
                String[] songsInfo; // even number of pair {songId, title}
                int userId = 0;

                if( (userId = database.getAuthID(idSegments[1], idSegments[0]), idSegments[2]) > 0 &&
                    (songsInfo = database.getSongsInfo(userId)).length > 0) {

                    int totalDownloads = database.getTotalDownloads(userId); //no need to update browserDownload every few mins|secs
                    l.info("write totalDownloads ", totalDownloads, " listOfDownloadsSize ", songsInfo.length);
                    write(Utils.intToBytes(totalDownloads)); 
                    write(Utils.intToBytes(songsInfo.length));

                    StringBuilder sb = new StringBuilder();
                    for(int i = 0; i < songsInfo.length; i++)
                        sb.append(songsInfo[i]);
                    byte[] songsInfoBytes = sb.toString().getBytes();
                    int songBytesLen = songsInfoBytes.length;
                    String firstSongId = songsInfo[0].split("/")[0];
                    l.info("write songInfoBytes");
                    write(Utils.intToBytes(songBytesLen));
                    write(songsInfoBytes);

                    l.info("client found, downloading song of id: ", firstSongId);
                	sendSong(firstSongId);
                    os.write(bytes);
                    l.info("song is sent");

                    boolean success = (is.read() == 1)? true:false;
                    if(success) {
                        l.info("song received successfully, cleaning up, downloads incremented");
                        clean(userId, firstSongId); //TODO or add timestame to song for cashe.
                        database.incrementUserDownloadsNum(userId);
                    }
                }
                else {
                    l.info("nothing is found, writing -1(unkonwn total downloads) 0(num of songs to download)");
                    write(Utils.intToBytes(-1)); //TOTAL DOWNLOADS UNKNOWN
                    write(Utils.intToBytes(0));  //NUM OF SONGS TO DOWNLOAD
                    os.write(bytes);
                }



    		} catch(Exception e) {
    			e.printStackTrace();
    		} finally {
                bytes = new byte[0];
            }
    }

    public void clean (int id, String url) {
        // clean song in songs file |TODO stick timestamp for cashe
        // remove song from database.
        l.info("clean user id "+id+", songId "+url);
        try {
            if(songFile != null) {
                boolean success =songFile.delete();
                l.info("song file deteted with success ", success);
            }
            database.removeSong(id, url);
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
            
            write(intToBytes(size)); 
            l.info("#1# songSize wrote: ", Utils.formatBytes(size));
            write(song); 
            l.info("#2# song wrote, total sent bytes len ", Utils.formatBytes(bytes.length));
            
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
        Database database = new Database();
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
                new Thread(new Listener(so, database)).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}