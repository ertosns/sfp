import org.junit.runner.*;
import org.junit.runner.notification.Failure;

public class TRunner 
{
	public static void main(String[] args) 
	{
		System.out.println("testing...");
		Result res = JUnitCore.runClasses(new Class[] {UtilsTC.class, DatabaseTC.class});
		for (Failure fail : res.getFailures()) 
		{
			System.out.println("fail: "+fail.toString()+"\nmes: "+fail.getMessage()+"\nDes: "+fail.getDescription()+"\nhead: "+fail.getTestHeader()+"+\nExc: "+fail.getTrace()+"\ntoString: "+fail.toString());
		}
		System.out.println("Done");
	}
}