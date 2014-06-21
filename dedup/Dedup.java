/* Marc Eder
 * Matthew Kidd
 * Fanghui Zhang
 * Jeff Craley
 * Joe Raser
 * EC504 Project
 * 3/4/14
 * Dedup.java: Command line and textual interface
 */

package dedup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class Dedup {

	public static void main(String[] args) throws NoSuchAlgorithmException, IOException, ClassNotFoundException{
		
		if (args.length != 0)
		{
			parseArgs(args);
		}
				
		else
		{
		Scanner scan = new Scanner(System.in);
		String input = "";
		do {
			System.out.println("Are you creating a new storage locker or using an existing one (type new or existing)?");
			input = scan.next();
		} while (input.isEmpty() && !input.equalsIgnoreCase("new") && !input.equalsIgnoreCase("existing"));
		
		Locker storage;
		if (input.equalsIgnoreCase("new")){
			System.out.println("Name your new locker: ");
			String name = scan.next();
			storage = new Locker(name);
			System.out.println("Locker " + name + " created...");
		} else if (input.equalsIgnoreCase("existing")){
			System.out.println("What is the name of the locker to load?: ");
			String name = scan.next();
			File f = new File(name + ".txt");
			if (f.exists()){
				storage = loadDB(name);
				System.out.println("Locker " + name + " loaded...");
				storage.printLockerContents();
			} else {
				System.err.println("Locker " + name + " not found. Make a new locker to store files.");
				scan.close();
				return;
			}
		} else {
			System.err.println("Problem loading locker");
			scan.close();
			return;
		}

		do {
			System.out.println("Would you like to insert, retrieve, or delete a file?");
			input = scan.next();
			if (storage.isEmpty()){
				if (input.equalsIgnoreCase("retrieve") || input.equalsIgnoreCase("delete")){
					System.err.println("No files to " + input + ". Try inserting a file first.");
					input = "";
				}
			}
		} while (input.isEmpty() && !input.equalsIgnoreCase("insert") && !input.equalsIgnoreCase("retrieve") && !input.equalsIgnoreCase("delete"));
		
		if (input.equalsIgnoreCase("insert")){
			System.out.println("What file would you like to insert? Enter \"done\" when finished");
			String filename = scan.next();
			while (!filename.equalsIgnoreCase("done")){
				storage.store(filename);
				System.out.println("Enter \"done\" if finished.");
				filename = scan.next();
			}
			storage.serializeToFile();
			storage.printDeDuplicationStats();
			storage.printLockerContents();
		} else if (input.equalsIgnoreCase("retrieve")){
			String filename = "";
			do {
				System.out.println("Which file would you like to retrieve (enter filename with suffix):");
				filename = scan.next();
				if (storage.fileExists(filename)){
					storage.retrieve(filename, null);
					System.out.println(filename + " retrieved as \"Copy" + filename + "\"");
					System.out.println("The output file matches the input file: " + checkDeduplicationAccuracy (storage, filename));
				} else {
					System.err.println("The file you reqested is not in the locker");
				}
			} while (!storage.fileExists(filename));
		} else if (input.equalsIgnoreCase("delete")){
			String filename = "";
			do {
				System.out.println("Which file would you like to delete (enter filename with suffix):");
				System.out.println("Enter \"done\" if finished.");
				filename = scan.next();
				if (storage.fileExists(filename)){
					storage.delete(filename);
					System.out.println(filename + " deleted from locker");
					storage.serializeToFile();
					storage.printLockerContents();
				} else if(!filename.equalsIgnoreCase("done")) {
					System.err.println("The file you reqested is not in the locker");
				}
			} while (!filename.equalsIgnoreCase("done"));
		} else {
			System.err.println("Problem processing your request");
			scan.close();
			return;
		}
		scan.close();
		}
	}
	
	public static Locker loadDB (String name) throws IOException, ClassNotFoundException{
		ObjectInputStream inStream = new ObjectInputStream(new FileInputStream(name + ".txt"));
		Locker db = (Locker) inStream.readObject();
		inStream.close();
		return db;
	}
	
	public static boolean checkDeduplicationAccuracy (Locker storage, String filename) throws IOException, NoSuchAlgorithmException {
		//Compares input and output files
		RandomAccessFile f = new RandomAccessFile (filename, "r");
		FileChannel fc = f.getChannel();
		MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		bb.load();
		byte[] originalFile = new byte[(int) fc.size()];
		bb.get(originalFile);
		f.close();
		fc.close();
		String hashOriginal = storage.md5ToString(storage.md5(originalFile));
		System.out.println("Input file Hash: " + hashOriginal);
		
		f = new RandomAccessFile ("Copy" + filename, "r");
		fc = f.getChannel();
		bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		bb.load();
		byte[] copyFile = new byte[(int) fc.size()];
		bb.get(copyFile);
		f.close();
		fc.close();
		String hashOutput = storage.md5ToString(storage.md5(copyFile));
		System.out.println("Output file Hash: " + storage.md5ToString(storage.md5(copyFile)));
		return hashOriginal.equals(hashOutput);
	}
	
	public static void parseArgs(String[] args) throws ClassNotFoundException, IOException, NoSuchAlgorithmException
	{
		String filename = "";
		String lockerLocation = "";
		String command = "";
		
		
		// store the command
		command = args[0];
			
		// look for flags and appropriate parameters
		for(int ii = 1; ii < args.length; ii++)
		{
			switch (args[ii]) {
				case "-file":
					if (ii++ <= args.length)
						filename = args[ii];
					break;
				case "-locker":
					if (ii++ <= args.length)
						lockerLocation = args[ii];
					if (lockerLocation.substring(lockerLocation.length() - 1) != "/")
						lockerLocation += "/";
					break;
					
			}
		}
		
		Locker storage;
		File f = new File(lockerLocation + "locker.txt");
		if (f.exists()){
			storage = loadDB(lockerLocation + "locker.txt");
		} else {
			storage = new Locker("locker");
		}
		
		// This is where the actions are performed
		switch(command) {
			case "store":
				storage.store(filename);
				storage.serializeToFile();
				break;
			case "retrieve":
				if (storage.fileExists(filename))
					storage.retrieve(filename, null);
				break;
			case "delete":
				if (storage.fileExists(filename))
					storage.delete(filename);
				storage.serializeToFile();
				break;
		}
	}
}