package err;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.logging.*;

//TODO in tcp|http connection does ack message sent at the end of application protocol is important? it isn't tcp ensure that every packet is sent, and received. optimize to work without that extra|redundant (ACK) way.
//TODO two kinds of songs should be sent {shareSongs(those are deleted if downloaed}, shownload song)},
// if user accepted song {either add new column of named accepted and only fetch accepted songs, or only search songs with sender_id = 0 and if user 
// accpeted song remove old item with it's row id, and create new row with sender_id = 0} balanced in time and memory first one is more intuitive.
// limit ip #requests per minute

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
                l.info("encodedId in form  EMAIL/PASS/NONCE ", encodedId);                
                String[] idSegments = Utils.base64ToString(encodedId).split(ID_SPLITER); // {email, pass, nonce}
                   
                l.info("writing interval ", W8_INTERVAL);
                write(intToBytes(W8_INTERVAL)); 
                String[] songsInfo; // even number of pair {songId, title}
                int userId = 0;

                if ((userId = database.getAuthID(idSegments[1], idSegments[0], Integer.parseInt(idSegments[2]))) > 0) {

                    boolean has = database.hasUnackRequests(userId);
                    write(Utils.intToBytes(has?TRUE:FALSE));
                    l.info("hasUnackRequests ", has);

                    has = database.hasNewSongs(userId);
                    write(Utils.intToBytes(has?TRUE:FALSE));
                    l.info("hasNewSongs ", has);
                    
                    has = database.hasNewPeerRequests(userId);
                    write(Utils.intToBytes(has?TRUE:FALSE));
                    l.info("hasPeersRequests ", has);
                                       
                    if ((songsInfo = database.getLocalSongsInfo(userId)).length > 0) {
                        int totalDownloads = database.getTotalDownloads(userId); //no need to update browserDownload every few mins|secs
                        l.info("write totalDownloads ", totalDownloads, " listOfDownloadsSize ", songsInfo.length);
                        write(Utils.intToBytes(songsInfo.length));
                        write(Utils.intToBytes(totalDownloads)); 
                        

                    
                        StringBuilder sb = new StringBuilder();
                        for(int i = 0; i < songsInfo.length; i++)
                            sb.append(songsInfo[i]);
                        byte[] songsInfoBytes = sb.toString().getBytes();
                        int songBytesLen = songsInfoBytes.length;
                        String[] firstSongInfo = songsInfo[0].split("/"); // {url, title, id}
                        String firstSongUrl = firstSongInfo[0];
                        int firstSongDatabaseId = Integer.parseInt(firstSongInfo[2].replace("\n", ""));
                        l.info("write songInfoBytes");
                        write(Utils.intToBytes(songBytesLen));
                        write(songsInfoBytes);

                        l.info("client found, downloading song of id: ", firstSongUrl);
                	    sendSong(firstSongUrl);
                        os.write(bytes);
                        l.info("song is sent");

                        boolean success = (is.read() == 1)? true:false;
                        if (success) {
                            l.info("song received successfully, cleaning up, downloads incremented");
                            clean(userId, firstSongDatabaseId); //TODO or add timestame to song for cashe.
                        }

                    } else {
                        write(Utils.intToBytes(0));
                        os.write(bytes);
                    }
                } else {
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

    public void clean (int id, int databaseID) {
        // clean song in songs file |TODO stick timestamp for cashe
        l.info("remove songs for file, database id "+id+", databaseId "+databaseID);
        try {
            if(songFile != null) {
                boolean success =songFile.delete();
                l.info("song file deteted with success ", success);
            }
            database.removeSong(databaseID); // by datbase id
            database.incrementUserDownloadsNum(id);
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