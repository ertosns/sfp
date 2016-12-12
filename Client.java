import java.util.*;
import java.io.*;
import java.net.*;

/**
 * Created by Err on 16-12-6.
 */

//TODO prevent killing service|give high priority
//TODO inform server you received successfully which song should be removed from server, (res) how serveral write will work?
//TODO connectivity receiver doesn't seem to be working.

public class Client  {

    String uniqueId = "bW9oYWJAc2ZwLmVycixtb2hhYm1vaCwx";
    String T = "LOG:INFO, ";
    public Client() {
        connect();
    }

    public void connect() {
        while(true) {
                ListenerThread lt = new ListenerThread();
                synchronized (lt) {
                    try {
                        Log.i(T, "listening thread started");
                        lt.start();
                        lt.wait();
                        lt.done();
                        Log.i(T, "listening thread finished");
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.i(T, "reconnect");
        }
    }

    class ListenerThread extends Thread {
        boolean done = false;

        public void run() {
            Socket sock = null;
            try {
                sock = new Socket("192.168.1.22", 65123);
                // \n used to terminate scanner on the other side.
                byte[] uniqueIdBytes =uniqueId.getBytes();
                Log.i(T, "sending id = "+new String(uniqueIdBytes));
                InputStream is = sock.getInputStream();
                OutputStream os = sock.getOutputStream();
                os.write(Utils.mergeBytes(Utils.intToBytes(uniqueIdBytes.length), uniqueIdBytes));
                Log.i(T, "id sent, blocking for inputStream reading interval");

                byte[] intervalBytes = new byte[4];                
                while(true) {
                    Log.i(T, "waiting...");
                    if(is.available() > 0) {
                        is.read(intervalBytes);
                        break;
                    }
                }

                boolean hasSong = (is.read()==1)?true:false;
                Log.i(T, "has song: "+hasSong);
                if(hasSong) {
                    readSong(is);
                }
                os.write("ack".getBytes());
                //app_level pro
            } catch (IOException io) {
                io.printStackTrace();
            } finally {
                done = true;
                try {
                    if(sock != null) sock.close();
                } catch (IOException i) {
                    i.printStackTrace();
                }
                synchronized (this) {
                    this.notify();
                }
            }
        }

        public void readSong(InputStream is) throws IOException {
            byte[] songNameSizeBytes = new byte[4];
            is.read(songNameSizeBytes);
            int songNameSize = Utils.bytesToInt(songNameSizeBytes);
            Log.i(T, "#1# songNameSize read = "+songNameSize+"b");

            byte[] songNameBytes = new byte[songNameSize];
            is.read(songNameBytes);
            String songName = new String(songNameBytes);
            Log.i(T, "#2# songName = "+songName);

            byte[] songSizeBytes = new byte[4];
            is.read(songSizeBytes);
            int songSize = Utils.bytesToInt(songSizeBytes);
            Log.i(T, "#3# songSize = "+songSize+"b");

            byte[] songByte = new byte[songSize];
            is.read(songByte);
            Log.i(T, "#4# song read");

            songByte = new byte[0];
            Log.i(T, "done, song is wrote to file successfully");
        }

        public boolean done () {
            return done;
        }
    }

    static class Log {
        public static void i(String tag, String s) {
            System.out.println(tag+s);
        }
    }

    static class Utils {

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

    }

    public static void main(String[] args) {
        new Client();
    }

}