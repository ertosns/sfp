import java.util.*;
import java.io.*;
import err.Utils;
import org.junit.Assert;
public class TU 
{

	public static boolean readCount(InputStream is, byte[] bytes, int expected) throws Exception
	{
    	bytes = new byte[4];
        is.read(bytes);
        int readCount = Utils.bytesToInt(bytes);
        System.out.println("read count "+readCount);
        return  readCount >= expected;
    }

    //TODO (FIX) it don't loop rows
    public static boolean readInfo(InputStream is, byte[] bytes, Object... items) throws Exception
    {
    	String str = null;
    	int num = 0;
        int size = 0;
    	for (int i = 0; i < items.length; i +=2) 
    	{
    		try 
    		{
                bytes = new byte[4];
    			is.read(bytes);
        		size = Utils.bytesToInt(bytes);
                System.out.println("bytes size "+size);

                switch (((Integer)items[i+1]).intValue()) 
                {
                
            	    case DatabaseTC.STR: 
            	    {
                        bytes = new byte[size];
                        is.read(bytes);
                		str = new String(bytes);
                        System.out.println("size "+bytes.length+", str raed "+str);
                	    Assert.assertTrue(str.equals((String)items[i]));	
            	        break;
            	    }
                	case DatabaseTC.ENC_INT: 
                	{
                        bytes = new byte[size];
                        is.read(bytes);
            	   	    num = Utils.bytesToInt(Utils.publicKeyDecryption(bytes));
                        System.out.println("ENC_INT raed "+num);
                		Assert.assertTrue(num >= ((Integer)items[i]).intValue());
                		break;
                	}
                	case DatabaseTC.INT: 
                	{ 
                        if (i==0)
                            break;
                        bytes = new byte[size];
                        is.read(bytes);
                		num = Utils.bytesToInt(bytes);
                        System.out.println("INT raed "+num);
                		Assert.assertTrue(num >= ((Integer)items[i]).intValue());
            	   	    break;
            	    } 
                	case DatabaseTC.DATE: 
                	{
                        bytes = new byte[size];
                        is.read(bytes);
                		long date = Utils.bytesToLong(bytes);
                        long time = ((Long)items[i]).longValue();
                        System.out.println("time read "+time);
            		    Assert.assertTrue(date >= time);	
            		    break;
            	    }
                }

            } 
            catch (IOException io) 
            {
                io.printStackTrace();
                return false;
            }
        	
    	}
    	return true;
    }

}