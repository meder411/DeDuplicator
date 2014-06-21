/* Marc Eder
 * Matthew Kidd
 * Fanghui Zhang
 * Jeff Craley
 * Joe Raser
 * EC504 Project
 * 3/4/14
 * Locker.java: Storage locker functionality, including insert, retrieve, delete, accessor and mutator methods
 */

package dedup;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Locker class that contains all the necessary details of its contents. Capable of adding, removing, and deleting files. Does not require live state.
 */
public class Locker implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final int DEFAULT_CHUNK_SIZE = 2048;
	private final int prime = 69031;
	private final String lockerName;
	private ConcurrentHashMap <String, byte[]> dictionary;
	private ArrayList<FileInfo> files;
	private long lockerSize;

	/**
	 * A BloomFilter class for handling deletion
	 */
	private class BloomFilter implements Serializable {
		private static final long serialVersionUID = 1L;
		private final int numHashes = 12;
		private int hashHalfLength = 100000;
		private BitSet bf;

		/**
		 * Default constructor for BloomFilter class.
		 * Instantiates a BitSet object of length len.
		 * @param len = Desired integer number of indices in the filter
		 */
		private BloomFilter (int len){
			this.setHashHalfLength(len);
			bf = new BitSet(2*hashHalfLength);
		}

		/**
		 * Sets the length of the BloomFilter object being instantiated.
		 * @param len = Desired integer number of indices in the filter
		 * @return void
		 */
		private void setHashHalfLength(int len){
			this.hashHalfLength = len;
		}

		/**
		 * Adds a String to the bloom filter.
		 * Hashes it for each hash, setting each corresponding bit in the BitSet.
		 * @param s = String to add
		 * @return void
		 */
		private void add(String s){
			byte[] b = s.getBytes();
			for (int ii = 0; ii < numHashes; ii++){
				int hashIndex = bloomHash(b,ii);
				bf.set(hashIndex);
			}
		}

		/**
		 * Checks to see if an element is in the BloomFilter.
		 * @param s = The string to check
		 * @return True if the element is found in the BloomFilter. False if the element is not found in the BloomFilter.
		 */
		private boolean inBloomFilter(String s) {
			boolean inFilter = true;
			byte[] b = s.getBytes();

			for(int ii = 0; ii < numHashes; ii++){
				int hashIndex = bloomHash(b,ii);
				if (!bf.get(hashIndex)){
					inFilter = false;
				}
			}
			return inFilter;
		}

		/**
		 * Computes the specified hash of the the input byte array.
		 * @param input = Byte array of input bytes
		 * @param hashNum = Integer number of hash to use
		 * @return Specified hash of the input byte array.
		 */
		// http://courses.csail.mit.edu/6.854/current/Handouts/Tim%20Kaler.pdf
		private int bloomHash (byte[] input, int hashNum){
			return (murmurHash(input, 32, 67) + hashNum * murmurHash(input, 32, 93)) % hashHalfLength + hashHalfLength;
		}

		/**
		 * Computes the specified hash of the the input byte array. Adapted from Austin Appleby's C++ code provided at https://sites.google.com/site/murmurhash/.
		 * @param input = Byte array of input bytes
		 * @param len = Desired integer number of indices in the filter
		 * @param seed = An integer to seed the hash
		 * @return The calculated hash. 
		 */
		private int murmurHash (byte[] input, int len, int seed){
			int m = 0xc6a4a793;
			int r = 16;
			int h = seed ^ (len * m);

			int start = 0;
			for (int i = 0; i < len / 4; i++){
				h += input[start] + input[start+1] + input[start+2] + input[start+3];
				h += m;
				h ^= h >> r;
				start += 4;
			}

			int d = 16;
			for (int j = len % 4; j > 0; j++){
				h += input[start + j] << d;
				d -= 8;
			}
			h *= m;
			h ^= h >> 10;
			h *= m;
			h ^= h >> 17;

			return h;
		}
	}	
	///////////////////////////////////////////////////
	///////////    ***FileInfo Class***    ////////////
	//// Contains all information for each file ///////
	///////////////////////////////////////////////////

	/**
	 * A class that contains all the details about a file in the storage locker
	 */
	private class FileInfo implements Serializable {
		private static final long serialVersionUID = 2L;
		private String fileName;
		private int storageIndex;
		private String hash;
		private ArrayList<String> fileReconstructor;
		private long size;
		private String timeAdded;
		private BloomFilter bf;
		private int fileChunkSize;

		/**
		 * Constructor for FileInfo class.  
		 * @param fileName = Name of the file
		 * @param hash = Hash of the entire file
		 * @param fileReconstructor = Ordered ArrayList containing references to indices in the dictionary that contain the file information
		 * @param bf = BloomFilter containing details about which dictionary references are used in the file
		 * @param fileSize = Size of the file
		 * @param fileChunkSize = Chunk size used when processing this file
		 */
		private FileInfo (String fileName, String hash, ArrayList<String> fileReconstructor, BloomFilter bf, long fileSize, int fileChunkSize){
			this.fileName = fileName;
			this.fileReconstructor = fileReconstructor;
			this.hash = hash;
			this.size = fileSize;
			this.timeAdded = DateFormat.getDateTimeInstance().format(System.currentTimeMillis());
			this.bf = bf;
			this.fileChunkSize = fileChunkSize;
		}

		/**
		 * Renames the file  
		 * @param name = New name of the file
		 * @return void
		 */
		private void rename (String name){
			this.fileName = name;
		}

		/**
		 * Sets the index of the file in the storage locker  
		 * @param index = Storage locker index of the file
		 * @return void
		 */
		private void setStorageIndex(int index){
			this.storageIndex = index;
		}

		/**
		 * Returns the file's index in storage locker  
		 * @param None
		 * @return Index of the file in the storage locker 
		 */
		private int getStorageIndex(){
			return this.storageIndex;
		}

		/**
		 * Returns the file's size
		 * @param None
		 * @return Size of the file
		 */
		private long getSize(){
			return this.size;
		}

		/**
		 * Returns the chunk size used to process the file  
		 * @param None
		 * @return Chunk size used to process the file 
		 */
		private int getFileChunkSize(){
			return this.fileChunkSize;
		}

		/**
		 * Returns the file's name
		 * @param None
		 * @return File's name
		 */
		private String getFileName (){
			return this.fileName;
		}

		/**
		 * Returns the time that the file was added to the storage locker  
		 * @param None
		 * @return Time when file added to the storage locker
		 */
		private String getTimeAdded(){
			return this.timeAdded;
		}

		/**
		 * Returns the ArrayList storing all of the dictionary references used to recreate the file
		 * @param None
		 * @return ArrayList of dictionary references
		 */
		private ArrayList<String> getFileReconstructor (){
			return this.fileReconstructor;
		}

		/**
		 * Returns the hash of the entire file
		 * @param None
		 * @return Hash of entire file
		 */
		private String getHash(){
			return this.hash;
		}

		/**
		 * Checks if a String is in the file's BloomFilter
		 * @param s = String to check
		 * @return True if the String is in the BloomFilter. False otherwise.
		 */
		private boolean inBloomFilter(String s){
			return this.bf.inBloomFilter(s);
		}

		/**
		 * Returns a human-readable String describing the contents of a FileInfo object.
		 * @param None
		 * @return String describing contents of object
		 */
		@Override
		public String toString(){
			return "File Name: " + this.fileName + ", Storage Index: " + this.storageIndex + ", Size: " + this.size/1000 + "kB";
		}
	}


	/**
	 * Constructor for Locker class. Creates a storage locker referenced by the parameter name. 
	 * @param name = Name of the storage locker
	 */
	public Locker(String name) {
		this.files = new ArrayList<FileInfo>();
		this.dictionary = new ConcurrentHashMap <String, byte[]>();
		this.lockerSize = 0L;
		this.lockerName = name;
	}


	//////////////////////////////////////////////////////////
	///////////// ***Locker Retrieval Methods*** /////////////
	//// - public void retrieve (String filename)           //
	//// - public boolean fileExists (String filename)     ///
	//// - public FileInfo fileInLocker (String filename) ////
	//////////////////////////////////////////////////////////

	/**
	 * Retrieves a file from the storage locker. Runs through the file's ArrayList of dictionary references to reconstruct the file from the dictionary's contents.
	 * Returns an error if the file is not found
	 * @param fileName = Name of file to retrieve
	 * @return void
	 */
	public boolean retrieve (String fileName, String path) throws UnsupportedEncodingException, NoSuchAlgorithmException, IOException{

		// Grabs correct fileReconstructor for this file
		FileInfo currentFile = fileInLocker(fileName);
		if (currentFile == null){
			System.err.println("File not found.");
			return false;
		}
		ArrayList<String> fr = currentFile.getFileReconstructor();

		// Serializes contents of file to a copy of the original file
		String fileNameOut = "Copy" + fileName;
		if (path != null){
			fileNameOut = path + "\\" + fileNameOut;
		}
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(fileNameOut), false));
		for (int j = 0; j < fr.size(); j++){
			bos.write(this.dictionary.get(fr.get(j)));
		}
		bos.close();
		System.out.println("Retrieved File Information:\n" + currentFile.toString());
		return true;
	}

	/**
	 * Checks if a file exists in the storage locker
	 * @param filename = Name of file whose existence is being questioned
	 * @return True if the file is in the storage locker. False otherwise.
	 */
	public boolean fileExists (String filename){
		for (FileInfo file : this.files){
			if (filename.equalsIgnoreCase(file.getFileName())){
				return true;
			}
		}
		return false;
	}

	/**
	 * If a file exists in the storage locker, returns a reference to it. If it doesn't exist, returns null.
	 * @param filename = Name of desired file
	 * @return Reference to the file if it's in the storage locker. Returns null otherwise.
	 */
	private FileInfo fileInLocker(String filename){
		// Identify if filename already exists
		for (FileInfo file : this.files){
			if (filename.equalsIgnoreCase(file.getFileName())){
				return file;	
			}
		}
		return null;
	}

	/**
	 * Checks if a file being stored in the locker is the same version of one already in the locker. 
	 * @param lockerVersion = FileInfo object of the locker's version of the file
	 * @param filename = Name of file being inserted
	 * @return True if the versions are the same. False otherwise.
	 */
	private boolean fileUnchanged (FileInfo lockerVersion, String filename) throws IOException, NoSuchAlgorithmException {
		return lockerVersion.getHash().equals(hashFile(filename));
	}


	////////////////////////////////////////////////////////////////////////
	////////////////////// ***Locker Storage Methods*** ////////////////////
	////// - public void store (String filename)                      //////
	////// - private void dynamicBlockInsert (String filename)        //////
	////// - private void fixedBlockInsert (String filename)          //////
	////// - private void addToDictionary (String hash, byte[] chunk) //////
	////// - private boolean hashExists (String hash)                 //////
	////// - private boolean contentsMatch (String hash, byte[]chunk) //////
	////////////////////////////////////////////////////////////////////////

	/**
	 * Stores a file specified by it's path. Decides whether to use dynamic or fixed chunk sizes depending on the type and size of the file.
	 * @param filePath = The path to the file on the local machine
	 * @return true on success, false on fail
	 */
	public boolean store (String filePath) throws IOException, NoSuchAlgorithmException{
		File f = new File(filePath);
		Path p = FileSystems.getDefault().getPath(filePath);

		// Filename needs to be the entire path name, jrr 4/22/14
		// Why? MCE 4/24/14
		// The FileInputStream needs the complete filepath, 
		// I just changed the FIS constructor argument 
		String filename = f.getName();
		String mimeType = Files.probeContentType(p);
		FileInfo file = fileInLocker(filename);

		// Loads file to a MappedByteBuffer to read contents
		FileInputStream fis = new FileInputStream (f.getPath());
		FileChannel fc = fis.getChannel();
		MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		bb.load();

		// Checks to see if the file being stored is the same version of an existing file
		// Enters the if-statement if the filename matches an existing file AND the sizes of the two files are the same 
		if (file != null && fc.size() == file.getSize()){
			if (fileUnchanged(file, filename)){
				System.out.println("This version of " + filename + " is already in the locker");
				fc.close();
				fis.close();
				return false;
			}
		}

		long start = System.currentTimeMillis(); // Start timer

		if (mimeType!= null && mimeType.equals("image/bmp")){
			// If a version already exists...
			// Add file with temporary filename, remove existing version of file (gets rid of any chunks that have disappeared in the update)
			// Rename added file to correct filename
			if (file != null){
				System.out.println(filename + " already in locker. Updating version using a fixed block size...");
				FileInfo newFile = fixedBlockInsert(bb, filename, file.getFileChunkSize(), true);
				delete(filename);
				newFile.rename(filename);
				System.out.println(filename + " updated successfully.");
			} else {
				System.out.println("Inserting " + filename + " using a fixed block size...");
				fixedBlockInsert(bb, filename, chooseChunkSize(mimeType, fc.size()), false);
				System.out.println(filename + " added successfully.");
			}
		// When no mimeType, JPEG, or TXT, use dynamic block
		// Dynamic blocks for TXT and JPEG files
		} else {
			// If a version already exists...
			// Add file with temporary filename, remove existing version of file (gets rid of any chunks that have disappeared in the update)
			// Rename added file to correct filename
			if (file != null){
				System.out.println(filename + " already in locker. Updating version using a dynamic block size...");
				FileInfo newFile = dynamicBlockInsert(bb, filename, file.getFileChunkSize(), true);
				delete(filename);
				newFile.rename(filename);
				System.out.println(filename + " updated successfully.");
			} else {
				System.out.println("Inserting " + filename + " using a dynamic block size...");
				dynamicBlockInsert(bb, filename, chooseChunkSize(mimeType, fc.size()), false);
				System.out.println(filename + " added successfully.");
			}
		}

		// Prints feedback
		long end = System.currentTimeMillis(); // End timer
		System.out.println(("Adding time: " + (double)(end-start)/1000) + "s\n");
		fc.close();
		fis.close();
		return true;
	}
	
	/**
	 * Chooses the average chunk size to use when processing a file. Based on empirical tests.
	 * @param None
	 * @return Average chunk size to use.
	 */
	private int chooseChunkSize (String mimeType, long size){
		int chunkSize = DEFAULT_CHUNK_SIZE;
		if (mimeType == null){
			return chunkSize;
		}
		if (mimeType.equalsIgnoreCase("text/plain")){
			if (size < 500000L){
				chunkSize = 512;
			} else {
				chunkSize = 1024;
			}
		} else if (mimeType.equalsIgnoreCase("image/jpeg")){
			if (size < 100000L){
				chunkSize = 512;
			} else {
				chunkSize = 2048;
			}
		} else if (mimeType.equalsIgnoreCase("image/bmp")){
			if (size < 300000L){
				chunkSize = 1024;
			} else {
				chunkSize = 2048;
			}
		}
		return chunkSize;
	}

	/**
	 * Inserts a file using dynamic block DeDuplication
	 * @param bb = The reference to the MappedByteBuffer streaming the file from the local machine
	 * @param fileName = Name of file to be inserted
	 * @param avgChunkSize = Average chunk size uses to process the file
	 * @param update = True if updating a the version of an existing file
	 * @return FileInfo object containing the file just added.
	 */
	private FileInfo dynamicBlockInsert(MappedByteBuffer bb, String fileName, int avgChunkSize, boolean update) {
		FileInfo insertedFile = null;
		try {
			String fileHash = hashFile(fileName); // Hash of whole file

			// If this is an update, add a temporary suffix to filename
			// Gets removed after deleting old version
			if (update){
				fileName += "TEMP";
			}

			// Variable and data structure initialization
			ArrayList<String> fr = new ArrayList<String>(); // To hold references for file reconstruction
			BloomFilter bf = new BloomFilter(3000); // For fast look up (particularly for deletion)
			ByteArrayOutputStream baos = new ByteArrayOutputStream(); // To store chunk info as bytes are read
			long numBytesExamined = 0L; // Tracks # bytes read
			long limit = bb.limit(); // # bytes in file
			int currWindowLen = avgChunkSize; // Hash window length 
			int currWindowStart = 0; // Index of first byte in window
			int bitCheck = avgChunkSize - 1; // For checking text fingerprint (8 trailing 0's)
			int multiplier = 1; // Will store largest polynomial multiplier in polynomial hash
			int hash = 0; // Will hold polynomial hash
			byte[] circBuf = new byte[currWindowLen]; // Stores recently read bits for hashing purposes
			int bufIndex = 0; // Tracks where most recent bit has been added in circBuf

			// 1. Performs initial hash on first chunk of size currWindowLen
			// Creates a polynomial hash
			for (int i = 0; i < currWindowLen; i++){
				byte b = bb.get();
				baos.write(b);
				circBuf[bufIndex] = b;
				bufIndex++;
				bufIndex %= circBuf.length; 
				if (i == 0){
					hash += b;
				} else {
					hash *= prime;
					hash += b;
					multiplier *= prime; // Updates value of largest polynomial multiplier
				}
				numBytesExamined++;
			}

			// 2. Rolls hash through the file looking for textual fingerprint
			while (currWindowStart + currWindowLen < limit){

				// Shifts polynomial hash right by one byte
				if (numBytesExamined <= bb.limit()){
					byte b = bb.get();
					baos.write(b);
					hash = hash - (multiplier * circBuf[bufIndex]);
					hash *= prime;
					hash += b;
					circBuf[bufIndex] = b;
					bufIndex++;
					bufIndex %= circBuf.length;
					numBytesExamined++;
				}

				currWindowLen++; // Tracks current chunk size after each new byte added

				// If the right characteristic is found or it's the last chunk
				if ((hash & bitCheck) == 0 || numBytesExamined == limit){

					// Find md5 of chunk for verification
					byte[] c = baos.toByteArray();
					baos.reset(); // clears ByteStream
					String hs = md5ToString(md5(c));

					// Reset the window position counters
					currWindowStart += currWindowLen;
					currWindowLen = 0;

					// If the hash is not in the dictionary...add it
					if (!this.hashExists(hs)){
						addToDictionary(hs, c);
						fr.add(hs);
						bf.add(hs);

					// If hash is in dictionary...one of two things could be happening
					// 1. Actual repetition of information (This is good)
					// 2. Polynomial hash collision (This is bad and needs to be handled)
					} else {
						// Case 1: Actual repetition
						// If repetition in the file...just reference hash in file reconstructor
						if (contentsMatch(hs, c)){
							fr.add(hs);
							bf.add(hs);

						// Case 2: Polynomial hash collision 
						// If polynomial hash collision...handle it
						// Add new md5 of the chunk to dictionary and reference in the file reconstructor
						} else {
							addToDictionary(hs, c);
							fr.add(hs);
							bf.add(hs);
						}
					}
				}
			}

			// Adds file to our locker and updates total lockerSize
			insertedFile = addFileToLocker(fileName, fileHash, fr, bf, limit, avgChunkSize);
			this.lockerSize += limit;
			System.out.println("Used " + fr.size() + " chunks to store file information");

		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return insertedFile;
	}

	/**
	 * Inserts a file using fixed block DeDuplication
	 * @param bb = The reference to the MappedByteBuffer streaming the file from the local machine
	 * @param fileName = Name of file to be inserted
	 * @param avgChunkSize = Average chunk size uses to process the file
	 * @param update = True if updating a the version of an existing file
	 * @return FileInfo object containing the file just added.
	 */
	private FileInfo fixedBlockInsert(MappedByteBuffer bb, String fileName, int chunkSize, boolean update) {
		FileInfo insertedFile = null;
		try {
			String fileHash = hashFile(fileName); // Hash of whole file

			if (update){
				fileName += "TEMP";
			}

			// Variable and data structure initialization
			int arrLen = chunkSize; // Stores chunk size, used so that last chunk size adjustable (doesn't surpass file length)
			byte[] chunk = new byte[arrLen]; // Will store chunk data
			int limit = bb.limit(); // # bytes in file
			ArrayList<String> fr = new ArrayList<String>(); // To hold references for file reconstruction
			BloomFilter bf = new BloomFilter(3000); // For fast look up (particularly for deletion)
			int currWindowStart = 0; // Index of first byte in current chunk

			// Reads chunks of data from file
			while (currWindowStart < limit){

				// Grabs next chunk
				bb.get(chunk, 0, arrLen);

				// Hashes the chunk
				byte[] h = md5(chunk);
				String hs = md5ToString(h); 

				// If the hash is not in the dictionary...add it and reference it
				if (!hashExists(hs)){
					addToDictionary(hs, chunk);
					fr.add(hs);
					bf.add(hs);

				// If the hash is already in the dictionary...just reference it
				} else {
					fr.add(hs);
					bf.add(hs);
				}
				currWindowStart += arrLen; // Updates

				// If there is enough unprocessed data left in file to fill a full chunk... 
				if (currWindowStart < limit - arrLen){
					chunk = new byte[arrLen]; // Clears chunk

				// If the size of the remaining data is less than chunkSize
				} else {
					arrLen = limit - currWindowStart; // Sets chunk size to be remainder of file
					chunk = new byte[arrLen]; // Clears chunk
				}
			}

			// Adds file to our locker and updates total lockerSize
			insertedFile = addFileToLocker(fileName, fileHash, fr, bf, limit, chunkSize);
			this.lockerSize += limit;
			System.out.println("Used " + fr.size() + " chunks to store file information");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return insertedFile;
	}

	/**
	 * Performs the final steps of building the FileInfo object and storing in in the locker
	 * @param filename = Name of file being added
	 * @param fileHash = Hash of entire file
	 * @param fr = ArrayList containing all the dictionary references for the file
	 * @param bf = Set BloomFilter for deletion purposed
	 * @param size = Size of file being added
	 * @param avgChunkSize = Average chunk size used to process the file
	 * @return FileInfo object containing the file just added.
	 */
	private FileInfo addFileToLocker (String filename, String fileHash, ArrayList<String> fr, BloomFilter bf, long size, int avgChunkSize){
		this.files.add(new FileInfo(filename, fileHash, fr, bf, size, avgChunkSize));
		int newIndex = this.files.size() - 1;
		this.files.get(newIndex).setStorageIndex(newIndex);
		return this.files.get(newIndex);

	}

	/**
	 * Adds a <hash, chunk> pair to the dictionary
	 * @param hash = Hash of a chunk of the file
	 * @param chunk = Byte array containing a chunk of the file
	 * @return void
	 */
	private void addToDictionary(String hash, byte[] chunk) {
		this.dictionary.put(hash, chunk);
	}

	/**
	 * Checks if the dictionary already contains a specified hash
	 * @param hash = Hash of a chunk of the file
	 * @return True if the hash is already in the dictionary. False otherwise.
	 */
	private boolean hashExists (String hash) {
		return this.dictionary.containsKey(hash);
	}

	/**
	 * Checks if the file chunk associated with a given hash String in the dictionary matches a file chunk that has hashed to the same hash String.
	 * Used for double-checking hash collisions.
	 * @param hash = Hash of a chunk of file
	 * @param chunk = Byte array of the chunk
	 * @return True if the hash collision is due to identical chunk content. False if collision due to other circumstances (bad...).
	 */
	private boolean contentsMatch (String hash, byte[] chunk) throws UnsupportedEncodingException, NoSuchAlgorithmException{
		return Arrays.equals(md5(this.dictionary.get(hash)), md5(chunk));
	}

	///////////////////////////////////////////////////////
	/////////// ***Locker Deletion Methods*** ////////////
	//// - public void delete (String fileName)     /////
	////////////////////////////////////////////////////
	/**
	 * Deletes a specified file from the storage locker. Uses bloom filter look ups to delete dictionary
	 * contents without affecting other files. 
	 * @param fileName = Name of file to delete
	 * @return void
	 */
	public boolean delete (String fileName){

		FileInfo file = fileInLocker(fileName); // Retrieves FileInfo for given fileName

		// Error if file not found
		if (file == null){
			System.err.println("File not found.");
			return false;
		}

		// For each reference in the fileReconstruct, check bloomFilters for all other files
		// If it's not in any, we are safe to remove it from the dictionary
		boolean clearedForDelete = true;
		for (String ref : file.getFileReconstructor()){
			for (FileInfo f : this.files){
				if (f != file){
					clearedForDelete = !f.inBloomFilter(ref);
				}
			}
			if (clearedForDelete){
				this.dictionary.remove(ref);
			}
		}

		this.files.remove(file); // Removes file from list
		this.lockerSize -= file.getSize();
		
		// Re-number file indexes of remaining files
		for (int ii = 0; ii < this.files.size(); ii++){
			this.files.get(ii).storageIndex = ii;
		}
		return true;
	}	

	////////////////////////////////////////////////////
	////////////// ***Hashing Methods*** ///////////////
	///// - public byte[] md5(byte[] chunk)        /////
	///// - public String md5ToString (byte[] md5) /////
	////////////////////////////////////////////////////

	/**
	 * Hashes the chunks of file
	 * @param chunk = Byte array containing contents of chunk of the file
	 * @return Byte array containing the md5 hash of the chunk
	 */
	public byte[] md5 (byte[] chunk) throws UnsupportedEncodingException, NoSuchAlgorithmException{
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(chunk);
		byte[] h = md.digest(chunk);
		return h;
	}

	/**
	 * Converts the md5 hash to an ASCII string
	 * @param md5 = Byte array containing the md5 hash of a chunk
	 * @return String of md5 hash of the chunk
	 */
	public String md5ToString (byte[] md5){
		// Makes it ASCII string
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < md5.length; i++) {
        	sb.append(Integer.toString((md5[i] & 0xff) + 0x100, 16).substring(1));
        }
        String hashOutput = sb.toString();
		return hashOutput;
	}

	/**
	 * Hashes the entire contents of a file. Used to check that a file has been reconstructed properly or
	 * if it's already in the locker.
	 * @param filename = File to examine
	 * @return String of md5 hash of entire contents of a file
	 */
	public String hashFile (String filename) throws IOException, NoSuchAlgorithmException{
		RandomAccessFile f = new RandomAccessFile (filename, "r");
		FileChannel fc = f.getChannel();
		MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		byte[] newVersion = new byte[(int) fc.size()];
		bb.get(newVersion);
		fc.close();
		f.close();
		return md5ToString(md5(newVersion));
	}


	//////////////////////////////////////////////////////
	//////////////// ***DB Backup Methods*** /////////////
	////// - public void serializeToFile()          //////
	//////////////////////////////////////////////////////

	/**
	 * Writes storage locker to a file. Used to remove need for live state.
	 * @param None
	 * @return void
	 */
	public void serializeToFile() throws IOException{
		String fileNameOut = this.lockerName + ".txt";
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(fileNameOut), false));
		oos.writeObject(this);
		oos.close();
	}


	//////////////////////////////////////////////////////////////
	//////////////// ***Locker Statistics Methods*** /////////////
	/// - public long getSizeOfLocker ()                       ///
	/// - public long getSizeOfLockerFile ()                   ///
	/// - public String getDeDuplicationRatio (boolean approx) ///
	/// - public long printLockerContents ()                   ///
	/// - public long printDeDuplicationStats                  ///
	/// - public boolean isEmpty()                             ///
	//////////////////////////////////////////////////////////////

	/**
	 * Returns the name identifying the locker
	 * @return Name of locker
	 */
	public String getLockerName(){
		return this.lockerName;
	}

	/**
	 * Returns cumulative size of all files in locker
	 * @param None
	 * @return Cumulative size of all files in locker
	 */
	public long getSizeOfLocker(){
		return this.lockerSize;
	}
	
	/**
	 * Returns number of files in locker
	 * @param None
	 * @return Number of files in locker
	 */
	public int numFilesInLocker(){
		return this.files.size();
	}

	/**
	 * Returns names of files in locker
	 * @param None
	 * @return Names of files in locker
	 */
	public ArrayList<String> getFileNames(){
		ArrayList<String> fnames = new ArrayList<String>();
		for (FileInfo f : this.files){
			fnames.add(f.getFileName());
		}
		return fnames;
	}
	/**
	 * Checks if locker is empty
	 * @param None
	 * @return True if the locker contains no files. False if there is at minimum one file in the locker.
	 */
	public boolean isEmpty(){
		return this.files.isEmpty();			
	}

	/**
	 * Returns size of the file backing the locker
	 * @param None
	 * @return Size of file backing the locker
	 */
	public long getSizeOfLockerFile() throws IOException{
		String name = this.lockerName + ".txt";
		RandomAccessFile locker = new RandomAccessFile (name, "r");
		FileChannel l = locker.getChannel();
		long dbSize = l.size();
		l.close();
		locker.close();
		return dbSize;
	}

	/**
	 * Returns the x term of the deduplication ratio.
	 * @param None
	 * @return Deduplication value
	 */
	public float getDeDuplicationValue() throws IOException{
		float size = (float) getSizeOfLocker() / getSizeOfLockerFile();
		size *= 10;
		size = (int) size / (float) 10;
		return size;
	}
	
	/**
	 * Returns the deduplication ratio of all the files in the locker. Reduces it to an "x : 1" format.
	 * @param None
	 * @return Deduplication ratio
	 */
	public String getDeDuplicationRatio() throws IOException{
		String approxReduced = this.getDeDuplicationValue() + " : 1";
		return approxReduced;
	}

	/**
	 * Prints the contents of the locker.
	 * @param None
	 * @return void
	 */
	public void printLockerContents(){
		System.out.println("-------------------\n| LOCKER CONTENTS |\n-------------------");
		System.out.printf("%2s  %-40s %-15s %-30s%n", "", "File Name", "Size", "Added On");
		System.out.printf("%2s  %-40s %-15s %-30s%n", "", "---------", "----", "--------");
		for (FileInfo f : this.files){
			System.out.printf("%2d. %-40s %-15s %-30s%n", f.getStorageIndex(), f.getFileName(), (f.getSize() / 1000) + "kB", f.getTimeAdded());			
		}
		System.out.println();
	}
	
		/**
	 * Prints the contents of the locker to a string.
	 * @param None
	 * @return void
	 */
	public String printLockerContentsString(){
		
	    // Create a stream to hold the output
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    PrintStream ps = new PrintStream(baos);
	    // IMPORTANT: Save the old System.out!
	    PrintStream old = System.out;
	    // Tell Java to use your special stream
	    System.setOut(ps);
	    System.out.println("LOCKER CONTENTS\n");
	    System.out.printf("%2s  %-40s %-15s %-30s%n", "", "File Name", "Size", "Added On");
		for (FileInfo f : this.files){
			System.out.printf("%2d. %-40s %-15s %-30s%n", f.getStorageIndex(), f.getFileName(), (f.getSize() / 1000) + "kB", f.getTimeAdded());			
		}
		System.out.println();
		// Put things back
	    System.out.flush();
	    System.setOut(old);
	    // Show what happened
	    return baos.toString();
	}
	/**
	 * Prints statistics about the storage locker and deduplication process.
	 * @param None
	 * @return void
	 */
	public void printDeDuplicationStats() throws IOException{
		System.out.println("----------------------------\n| DEDUPLICATION STATISTICS |\n----------------------------");
		System.out.println("Number of Files in Storage: " + this.files.size());
		System.out.println("Total Size of Stored Files: " + this.getSizeOfLocker()/1000 + "kB");
		System.out.println("Size of Storage Database: " + this.getSizeOfLockerFile()/1000 + "kB");
		System.out.println("Approximate DeDuplication Ratio (rounded): " + this.getDeDuplicationRatio());
		System.out.println();
	}
	
	/**
	 * Prints statistics about the storage locker and deduplication process to a string
	 * @param None
	 * @return void
	 */
	public String printDeDuplicationStatsString() throws IOException{
		// Create a stream to hold the output
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    PrintStream ps = new PrintStream(baos);
	    // IMPORTANT: Save the old System.out!
	    PrintStream old = System.out;
	    // Tell Java to use your special stream
	    System.setOut(ps);
		System.out.println("DEDUPLICATION STATISTICS\n");
		System.out.println("Number of Files in Storage: " + this.files.size());
		System.out.println("Total Size of Stored Files: " + this.getSizeOfLocker()/1000 + "kB");
		System.out.println("Size of Storage Database: " + this.getSizeOfLockerFile()/1000 + "kB");
		System.out.println("Approximate DeDuplication Ratio (rounded): " + this.getDeDuplicationRatio());
		System.out.println();
		
		// Put things back
	    System.out.flush();
	    System.setOut(old);
	    // Show what happened
	    return baos.toString();
	}

}
