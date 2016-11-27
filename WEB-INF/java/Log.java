public class Log {
	
	String className;
	boolean print = true;

    public Log(String className) {
    	this.className = className;
    }

	public Log(String className, boolean print) {
		this.className = className;
		this.print = print;
	}

	public void info(String m) {
		if(print) print(m);
		else printToFile(m);
	}

	private void print(String m) {
        System.out.println("### "+className+" ### "+m);    
	}

	private void printToFile(String m){
		//TODO
	}

}