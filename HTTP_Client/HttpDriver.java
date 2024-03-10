
// Brett Gattinger
// 30009390

/**
 * A driver class for HttpClient
 * 
 * CPSC 441
 * Assignment 2
 * 
 * 
 * One command line argument is required: 
 * 		- the URL of the object to be downloaded
 * 
 * 
 * @author 	Majid Ghaderi
 * @version 2022
 *
 */


import java.io.*;
import java.util.*;
import java.util.logging.*;


public class HttpDriver {
	private static final Logger logger = Logger.getLogger("HttpClient");
	
	public static void main(String[] args) {
		// example URLs
		// http://ict746x.cs.ucalgary.ca/
		// http://ict746x.cs.ucalgary.ca/files/small.txt
		// http://ict746x.cs.ucalgary.ca/files/files/large.jpg
		// http://ict746x.cs.ucalgary.ca/files/files/mdium.pdf

		// input URL is required
		if (args.length == 0) {
			System.out.println("incorrect usage, input URL is required");
			System.out.println("try again");
			System.exit(0);
		}

		// parse command line args
		HashMap<String, String> params = parseCommandLine(args);
		
		// set the parameters
		String url = params.getOrDefault("-u", args[0]); // object url
		Level logLevel = Level.parse( params.getOrDefault("-v", "all").toUpperCase() ); // log levels: all, info, off

		// set log level
		setLogLevel(logLevel);

		//DEV NOTE HTTP Client object instantiated here ~ apparently there is nothing I need to code for its constructor
		//		|-> I think everythin I code for the assignment is done in the HTTP Client class's get method (check this)
		HttpClient client = new HttpClient();
		System.out.printf("downloading %s...\n", url);
		client.get(url);
		System.out.println("download completed.");

		// get rid of any lingering threads/timers
		System.exit(0);
	}

	
	// parse command line arguments
	private static HashMap<String, String> parseCommandLine(String[] args) {
		HashMap<String, String> params = new HashMap<String, String>();

		int i = 0;
		while ((i + 1) < args.length) {
			params.put(args[i], args[i+1]);
			i += 2;
		}
		
		return params;
	}
	
	
	// set the global log level and format
	private static void setLogLevel(Level level) {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s %n");
		
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(level);
		logger.addHandler(handler);		
		logger.setLevel(level);
		logger.setUseParentHandlers(false);
	}
	
}
