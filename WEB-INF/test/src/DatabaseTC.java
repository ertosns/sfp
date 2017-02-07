import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;
import java.io.IOException;
import java.io.*;
import java.util.*;
import err.Database;
import err.Utils;
import err.Consts;

public class DatabaseTC extends TestCase {
	final static int STR = 1;
    final static int INT = 2;
    final static int ENC_INT = 4;
    final static int LONG = 3;
    final static int DATE = 5;
    
    Database db = null;
    boolean succeed;
    ByteArrayInputStream is = null;
    byte[] bytes = new byte[0];
    String name = "ertosnsBot";
    String peerName = "peerName";
    String pass = "ScryptoPhantus5195PH";
    String peerPass = "peerPass";
    String email = "ertosnsbot@sfp.err";
    String peerEmail = "peer@sfp.err";
    String image = "my smiley face";
    String peerImage = "";
    int nonce = 0;
    int id = 1;
    int peerId = 2;
    int downloads = 0;
    long songDate;
    String songUrl = "bTTi43A6QYM";
    String songTitle = "eslamHos";
    int songId = 1;
    String songMessage = "check this out!";
    
	public void setUp() {
        db = new Database();
        songDate = new Date().getTime();
	}
    
    //TODO should unit testing be tested each method seperately?    
    @Test
    public void testSignUp() {
        Assert.assertTrue(db.signUp(name, pass, email) != Utils.SERVER_ERROR);
    }


    @Test 
    public void testAuth() {
	    auth();
    }

    public void auth() {
        id = db.getAuthID(pass, email);
        Assert.assertTrue(id != Utils.SERVER_ERROR);
        nonce = db.incrementNonce(id);
        id = db.getAuthID(pass, email, nonce);
        Assert.assertTrue(id > 0);
        System.out.println("id = "+id);
        Assert.assertTrue(id != Utils.SERVER_ERROR);
        Assert.assertTrue(db.getTotalDownloads(id) >= 0);
        //PeerId
        Assert.assertTrue( (peerId = db.signUp(peerName, peerPass, peerEmail)) > 0);
        System.out.println("peerId = "+peerId);
        int peerId = db.getAuthID(peerPass, peerEmail);
        System.out.println("peerId = "+peerId);
        Assert.assertTrue(peerId > 0);
        succeed = db.addRelation(peerId, id); //peerId is sender
        Assert.assertTrue(succeed);
    }

    @Test
    public void testPictureApi() {
        auth();
    	byte[] imageBytes = image.getBytes();
    	db.addImage(id, imageBytes);
        String cImage= new String(db.getImage(id));
        Assert.assertTrue(cImage.equals(image));
    }

    @Test
    public void testDownloads() {
        auth();
    	downloads = db.incrementUserDownloadsNum(id);
    	Assert.assertTrue(downloads == db.getTotalDownloads(id));
    }
    
    //TODO does refelction invoke throwable methods?
    @Test
    public void testRelations() throws Throwable{
        auth();

        boolean succeed = db.hasNewPeerRequests(id);
        Assert.assertTrue(succeed);

        bytes = db.getListOfRequests(id, true); //true for new requests vs sent
        is = new ByteArrayInputStream(bytes);
        succeed = TU.readCount(is, bytes, 1);
        Assert.assertTrue(succeed);
        succeed = TU.readInfo(is, bytes, peerName, STR, peerEmail, STR, peerImage, STR, peerId, ENC_INT);
        Assert.assertTrue(succeed);
        
        //TODO (res) will reusing is below garbage collect bytes?
        try { 
        	is.close();
    	} catch (IOException io) {
    		io.printStackTrace();
    	}
        
        succeed = db.markRequestSent(peerId, id);
        Assert.assertTrue(succeed);
        succeed = db.setRequestState(peerId, id, Utils.RESPONDED);
        Assert.assertTrue(succeed);
        succeed = db.checkRelation(peerId, id);
        Assert.assertTrue(succeed);
     
        bytes = db.getPeersInfoById(id);
        is = new ByteArrayInputStream(bytes);
        System.out.println("read peersInfo count");
        succeed = TU.readCount(is, bytes, 1);
        Assert.assertTrue(succeed);
        System.out.println("read req Info");
        succeed = TU.readInfo(is, bytes, peerName, STR, peerEmail, STR, peerImage, STR, peerId, ENC_INT);
        Assert.assertTrue(succeed);
        try { 
        	is.close();
    	} catch (IOException io) {
    		io.printStackTrace();
    	}

        succeed = db.hasUnackRequests(peerId);
        Assert.assertTrue(succeed);

        bytes = db.getListOfUnAckRequests(peerId);
        System.out.println("read unAckRequests count");
        is = new ByteArrayInputStream(bytes);
        succeed = TU.readCount(is, bytes, 1);
        Assert.assertTrue(succeed);
        System.out.println("read req Info");
        succeed = TU.readInfo(is, bytes, name, STR, email, STR, image, STR, id, ENC_INT);
        Assert.assertTrue(succeed);
        try { 
        	is.close();
    	} catch (IOException io) {
    		io.printStackTrace();
    	}
        succeed = db.setRequestAck(peerId);
        Assert.assertTrue(succeed);
         
    	succeed = db.endRelation(peerId, id);
    	Assert.assertTrue(succeed);
    }

    @Test
    public void testSearch() throws Throwable
    {
        auth();
    	bytes = db.searchByEmail(id, peerEmail);
    	is = new ByteArrayInputStream(bytes);
        succeed = TU.readInfo(is, bytes, peerName, STR, peerImage, STR, peerId, ENC_INT);
        Assert.assertTrue(succeed);
        try 
        { 
        	is.close();
    	} 
    	catch (IOException io) 
    	{
    		io.printStackTrace();
    	}

    	bytes = db.searchByName(id, peerName, 0, 1);
    	is = new ByteArrayInputStream(bytes);
        succeed = TU.readCount(is, bytes, 1);
        Assert.assertTrue(succeed);
        succeed = TU.readInfo(is, bytes, peerName, STR, peerImage, STR, peerId, ENC_INT);
        Assert.assertTrue(succeed);
        try 
        { 
        	is.close();
    	} 
    	catch (IOException io) 
    	{
    		io.printStackTrace();
    	}
    }

    @Test
    public void testSongsAPI() throws Throwable
    {
        auth();
    	succeed = db.insertSong(id, peerId, songUrl, songTitle, songMessage);
    	Assert.assertTrue(succeed);


        succeed = db.hasNewSongs(peerId);
        Assert.assertTrue(succeed);

    	bytes = db.getSharedSongsInfo(peerId, true);
    	is = new ByteArrayInputStream(bytes);
        System.out.println("read songs count");
        succeed = TU.readCount(is, bytes, 1);
        Assert.assertTrue(succeed);
        byte[] databaseIdBytes = new byte[4];
        is.read(databaseIdBytes);
        songId = Utils.bytesToInt(databaseIdBytes);
        succeed = TU.readInfo(is, bytes, songUrl, STR, songTitle, STR, 0L, DATE, songMessage, STR, name, STR, id, ENC_INT);
        Assert.assertTrue(succeed);
        try 
        {
        	is.close();
    	}
    	catch (IOException io) 
    	{
    		io.printStackTrace();
    	}
        
    	succeed = db.markSongSent(id, peerId, songUrl);
    	Assert.assertTrue(succeed);

    	succeed = db.markSongDownloadable(songId);
    	Assert.assertTrue(succeed);

        String[] localSongsInfo = db.getLocalSongsInfo(peerId);
        Assert.assertTrue(localSongsInfo.length > 0);
        for(int i = 0; i < localSongsInfo.length; i++)
        {
        	String[] songInfo = localSongsInfo[i].replace("\n", "").split(Consts.FORWARDSLASH_SPLITER);
            for (int j = 0; j < songInfo.length; j++) 
                System.out.println("songInfo with of index "+j+" = "+songInfo[j]);
                System.out.println("song Url: "+songUrl+", songTitle: "+songTitle+", songId: "+songId);
            Assert.assertTrue(songInfo[0].equals(songUrl) && songInfo[1].equals(songTitle) && songId <= Integer.parseInt(songInfo[2]));
        }

        Date songDate = db.getSongDate(peerId);
        Assert.assertTrue(songDate.getTime() >= songDate.getTime());

        succeed = db.removeSong(songId); 
        Assert.assertTrue(succeed);

        succeed = db.addAnonymousDownload();
        Assert.assertTrue(succeed);
    }
}