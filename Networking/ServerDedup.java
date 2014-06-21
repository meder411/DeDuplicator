package Networking;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

import dedup.Locker;

/**
 * A server program which accepts requests from clients to
 * capitalize strings.  When clients connect, a new thread is
 * started to handle an interactive dialog in which the client
 * sends in a string and the server thread sends back the
 * capitalized version of the string.
 *
 * The program is runs in an infinite loop, so shutdown in platform
 * dependent.  If you ran it from a console window with the "java"
 * interpreter, Ctrl+C generally will shut it down.
 */
public class ServerDedup {

    /**
     * Application method to run the server runs in an infinite loop
     * listening on port 9898.  When a connection is requested, it
     * spawns a new thread to do the servicing and immediately returns
     * to listening.  The server keeps a unique client number for each
     * client that connects just to show interesting logging
     * messages.  It is certainly not necessary to do this.
     */
	
	private static ServerSocket listener;
	
    public static void main(String[] args) throws Exception {
    	// Report the IP address of server in order to run the client 
    	// at this IP
    	Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
    	while (interfaces.hasMoreElements()){
    	    NetworkInterface current = interfaces.nextElement();
    	    System.out.println(current);
    	    if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
    	    Enumeration<InetAddress> addresses = current.getInetAddresses();
    	    while (addresses.hasMoreElements()){
    	        InetAddress current_addr = addresses.nextElement();
    	        if (current_addr.isLoopbackAddress()) continue;
    	        if (current_addr instanceof Inet4Address)
    	        	  System.out.println(current_addr.getHostAddress());
    	        	else if (current_addr instanceof Inet6Address){}
    	        	  //System.out.println(current_addr.getHostAddress());
    	    }
    	}
    	System.out.println("The dedup server is running.");
        int clientNumber = 0;
        listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(17774));
        
        listener.setReuseAddress(true);
        try {
            while (true) {
                new Dedupper(listener.accept(), clientNumber++).start();
                listener.setReuseAddress(true);
            }
        } catch (BindException bindEx){
        	System.out.println("Try to close socket, port bind issue");
        	listener.close();
        }finally {
        	//System.out.println("Try to close socket");
            listener.close();
        }
        
       
    }

    /**
     * A private thread to handle capitalization requests on a particular
     * socket.  The client terminates the dialogue by sending a single line
     * containing only a period.
     */
    private static class Dedupper extends Thread {
        private Socket socket;
        private int clientNumber;

        public Dedupper(Socket socket, int clientNumber) {
            this.socket = socket;
            this.clientNumber = clientNumber;
            log("New connection with client# " + clientNumber + " at " + socket);
        }

        /**
         * Services this thread's client by first sending the
         * client a welcome message then repeatedly reading strings
         * and sending back the capitalized version of the string.
         */
        public void run() {
            try {

                // Decorate the streams so we can send characters
                // and not just bytes.  Ensure output is flushed
                // after every newline.
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                // Send a welcome message to the client.
                out.println("Hello, you are client #" + clientNumber + ".");
                out.println("Enter a line with only a period to quit\n");
                
                /*****************************************************/
                // Here begin the parsing 
                
                
                // Get messages from the client, line by line; return them
                // capitalized
                String input = "";
        		do {
        			out.println("Are you creating a new storage locker or using an existing one (type new or existing)?");
        			input = in.readLine();
        		} while (input.isEmpty() && !input.equalsIgnoreCase("new") && !input.equalsIgnoreCase("existing"));

        		Locker storage;
        		if (input.equalsIgnoreCase("new")){
        			// For now only one locker available
        			storage = new Locker("locker.txt");
        			//storage.setChunkSize(8192);
        			out.println("New locker created...");
        			out.println("Chunk size set to 8kb...");
        		} else if (input.equalsIgnoreCase("existing")){
        			File f = new File("locker.txt");
        			if (f.exists()){
        				storage = loadDB("locker.txt");
        				out.println("Existing locker loaded...");
        				out.println(storage.printLockerContentsString());
        			} else {
        				out.println("No existing locker. Make a new locker to store files. Restart Connection now.");
        				in.close();
        				listener.close();
        				return;
        			}
        		} else {
        			out.println("Problem loading locker");
        			in.close();
        			return;
        		}

        		do {
        			out.println("Would you like to insert, retrieve, or delete a file?");
        			input = in.readLine();
        			if (storage.isEmpty()){
        				if (input.equalsIgnoreCase("retrieve") || input.equalsIgnoreCase("delete")){
        					out.println("No files to " + input + ". Try inserting a file first.");
        					input = "";
        				}
        			}
        		} while (input.isEmpty() && !input.equalsIgnoreCase("insert") && !input.equalsIgnoreCase("retrieve") && !input.equalsIgnoreCase("delete"));

        		if (input.equalsIgnoreCase("insert")){
        			out.println("Which file(s) would you like to insert? Enter \"done\" when finished:\n1. sample1.txt\n2. sample2.txt\n3. sample3.txt\n4. sample4.txt\n5. sample5.txt\n6. sample6.txt");
        			String filename = in.readLine();
        			while (!filename.equalsIgnoreCase("done")){
        				storage.store(filename);
        				out.println("Enter \"done\" if finished.");
        				filename = in.readLine();

        			}
        			storage.serializeToFile();
        			out.println(storage.printDeDuplicationStatsString());
        			out.println(storage.printLockerContentsString());
        		} else if (input.equalsIgnoreCase("retrieve")){
        			String filename = "";
        			do {
        				out.println("Which file would you like to retrieve (enter filename with suffix):");
        				filename = in.readLine();
        				if (storage.fileExists(filename)){
        					storage.retrieve(filename, null);
        					out.println(filename + " retrieved as \"Copy" + filename + "\"");
        					//System.out.println("The output file matches the input file: " + checkDeduplicationAccuracy (storage, filename));
        				} else {
        					out.println("The file you reqested is not in the locker");
        				}
        			} while (!storage.fileExists(filename));
        		} else if (input.equalsIgnoreCase("delete")){
        			String filename = "";
        			do {
        				out.println("Which file would you like to delete (enter filename with suffix):");
        				out.println("Enter \"done\" if finished.");
        				filename = in.readLine();
        				if (storage.fileExists(filename)){
        					storage.delete(filename);
        					out.println(filename + " deleted from locker");
        					storage.serializeToFile();
        					out.println(storage.printLockerContentsString());
        				} else if(!filename.equalsIgnoreCase("done")) {
        					out.println("The file you reqested is not in the locker");
        				}
        			} while (!filename.equalsIgnoreCase("done"));
        		} else {
        			out.println("Problem processing your request");
        			in.close();
        			listener.close();
        			return;
        		}
        		in.close();
            
           
            } catch (IOException e) {
                log("Error handling client# " + clientNumber + ": " + e);
            } catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
                try {
                    listener.close();
                } catch (IOException e) {
                    log("Couldn't close a socket, what's going on?");
                }
                log("Connection with client# " + clientNumber + " closed");
            }
        }

        /**
         * Logs a simple message.  In this case we just write the
         * message to the server applications standard output.
         */
        private void log(String message) {
            System.out.println(message);
        }
        
    	public static Locker loadDB (String filename) throws IOException, ClassNotFoundException{
    		ObjectInputStream inStream = new ObjectInputStream(new FileInputStream(filename));
    		Locker db = (Locker) inStream.readObject();
    		inStream.close();
    		return db;
    	}
    }
}
