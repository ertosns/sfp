import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;
import java.io.*;
import java.util.*;
import err.Utils;

public class UtilsTC extends TestCase
{
	private static final String message = "the world wonder";
    private String songId;
    private File songFile = null;
    private String title;
	public void setUp() 
	{

	}

    @Test
	public void testEncDec() throws Exception
	{
        String dec = new String(Utils.publicKeyDecryption(Utils.publicKeyEncryption(message.getBytes())));
        Assert.assertTrue(dec.equals(message));
    }

    @Test
    public void testBase64() 
    {
    	Assert.assertTrue(Utils.base64ToString(Utils.toBase64(message)).equals(message));
        Assert.assertTrue(Utils.base64UriToString(Utils.toBase64Uri(message)).equals(message));
        Assert.assertTrue(new String(Utils.base64ToBytes(new String(Utils.toBase64Bytes(message)))).equals(message));
    }

    @Test
    public void testDownloadSong() 
    {
    	songId = "bTTi43A6QYM";
    	Object[] songInfo = Utils.downloadSong(songId);

    	songFile = (File) songInfo[0];
    	Assert.assertTrue(songFile != null);
        
        title = (String) songInfo[1];
        Assert.assertTrue(title != null);
    }
    
    @Test
    public void testNumbers() 
    {
    	String message2 = "conan o'brien is funny";
    	byte[] bytes = Utils.mergeBytes(message.getBytes(), message2.getBytes());
    	Assert.assertTrue(message2.getBytes().length + message.getBytes().length == bytes.length);

    	int num = 88234234;
        Assert.assertTrue(Utils.bytesToInt(Utils.intToBytes(num)) == num);
        
        long lNum = 32453452786780L;
        System.out.printf("lNum in Binary: %80s\n", Long.toBinaryString(lNum));
        
        bytes = Utils.longToBytes(lNum);
        System.out.printf("\nlNum in binary Utils: %80s\n", Utils.toBinary(bytes));
        
        long unitLong = Utils.bytesToLong(bytes);
        System.out.println("lNum: "+lNum);
        System.out.println("lNum utils: "+unitLong);
        Assert.assertTrue(unitLong == lNum);
    }

}