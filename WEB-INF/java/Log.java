import java.util.logging.*;

public class Log {
	public static final int FILE = 1;
	public static final int CONSOLE = 0; //defult handler
	private static final String MONITER_FILE = "../../../moniter";
	private static final String IMPORTANT_FLAG = "###! ";
	private Handler handler = null;
	private Logger log = null;
	
	public Log(String className, int handlerFlag) {
        log = Logger.getLogger(className);
        log.setLevel(Level.INFO);
        log.setUseParentHandlers(false);
        log.setLevel(Level.INFO);
        
        try {
        	if(handlerFlag == FILE) handler = new FileHandler(MONITER_FILE);
        	else handler = new ConsoleHandler();
        } catch (Exception e) {
        	e.printStackTrace();
        }

        log.addHandler(handler);
	}
    
    public void info(String... vars) {
    	StringBuilder sb = new StringBuilder();
    	for(String s: vars) sb.append(s);
    	log.info(sb.toString());
    }

    public void info(Object... vars) { //important info contains numbers
    	StringBuilder sb = new StringBuilder();
    	sb.append(IMPORTANT_FLAG);
    	for(Object s: vars) {
    		sb.append(s.toString());
    	}
    	log.info(sb.toString());
    }


}