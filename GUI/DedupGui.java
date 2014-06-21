/* Marc Eder
 * Matthew Kidd
 * Fanghui Zhang
 * Jeff Craley
 * Joe Raser
 * EC504 Project
 * 3/4/14
 * DedupGui.java: GUI interface for DeDuplicator */

package GUI;

import dedup.Locker;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;


 
/* DedupGui.java requires no other files. */
public class DedupGui extends JPanel
                      implements ListSelectionListener {
    
	
	private static final long serialVersionUID = 1L;
	private JList<String> list;
    private DefaultListModel<String> listModel;
 
    private static JFileChooser fileChooser = new JFileChooser();
    private static JFileChooser pathChooser = new JFileChooser();

    private static File[] chosenFiles;
    private static String[] chosenFilePaths;
    private static String outputPath;
    
    private static final String AddFileString = "Add File";
    private static final String retrieveFileString = "Retrieve File";
    private static final String deleteFileString = "Delete File";
    private static final String selectPathString = "Select Output Path";
    private static final String loadLockerString = "Load Locker";
    private static final String createLockerString = "Create Locker";
    
    private JButton retrieveButton;
    private JButton lockerCreate;
    private JButton lockerLoad;
    private JButton addFileButton;
    private JButton deleteFileButton;
    private JButton selectPathButton;
    
    private JLabel lockerLabel;
    private JTextField outputPathLabel;
    
    private JTextField filenameRetrieve;
    private JTextArea lockerName;
    private static JTextArea stats = new JTextArea("DEDUPLICATION STATISTICS");
    private static JTextArea details = new JTextArea("LOCKER CONTENTS");
	private static JTextArea console = new JTextArea();
	private static JScrollPane scroll = new JScrollPane (console, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

	
    private JLabel progressLabel;
    private static Locker storage; 
    private JProgressBar progressBar;
 
    public DedupGui() {
        super(new BorderLayout());
		suppressSystemStreams();
        listModel = new DefaultListModel<String>();
        pathChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        outputPath = System.getProperty("user.dir");
        
        lockerLabel = new JLabel("File Locker");
        outputPathLabel = new JTextField(System.getProperty("user.dir"));
        outputPathLabel.setEditable(false);
        
        //Create the list and put it in a scroll pane.
        list = new JList<String>(listModel);
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndex(0);
        list.addListSelectionListener(this);
        list.setVisibleRowCount(10);
        JScrollPane listScrollPane = new JScrollPane(list);
 
        // Set up functionality buttons
        addFileButton = new JButton(AddFileString);
        addFileListener addFileListener = new addFileListener(addFileButton);
        addFileButton.setActionCommand(AddFileString);
        addFileButton.addActionListener(addFileListener);
        
        selectPathButton = new JButton("Change");
        selectPathButton.setActionCommand(selectPathString);
        selectPathButton.addActionListener(new selectFileListener());
        
        deleteFileButton = new JButton(deleteFileString);
        deleteFileButton.setActionCommand(deleteFileString);
        deleteFileButton.addActionListener(new deleteFileListener());
       
        // For locker creation/loading
        lockerCreate = new JButton("Create New Locker");
        lockerCreate.setActionCommand(createLockerString);
        lockerCreate.addActionListener(new createLockerListener());
        
        lockerLoad = new JButton("Load Locker");
        lockerLoad.setActionCommand(loadLockerString);
        lockerLoad.addActionListener(new createLockerListener());;
        
        lockerName =  new JTextArea(" Enter Locker Name Here");
        lockerName.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e){
            	lockerName.setText("");
            	lockerCreate.setEnabled(true);
            	lockerLoad.setEnabled(true);
            }
        });
        lockerName.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.BLACK));
        
        /* Setting Current Directory */
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        pathChooser.setCurrentDirectory(new File (System.getProperty("user.dir")));

        
        retrieveButton = new JButton(retrieveFileString);
        retrieveButton.setActionCommand(retrieveFileString);
        retrieveButton.addActionListener(new retrieveFileListener());
 
        filenameRetrieve = new JTextField();
        filenameRetrieve.addActionListener(addFileListener);
        filenameRetrieve.getDocument().addDocumentListener(addFileListener);

        // Set up progress bar
        progressBar = new JProgressBar();
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressLabel = new JLabel("Approximate Disk Space Saved Through DeDuplication");
        
        console.setLineWrap(true);
    	console.setEditable(false);
    	stats.setLineWrap(true);
    	stats.setEditable(false);
    	details.setLineWrap(true);
    	details.setEditable(false);
    	
        // Disable buttons until locker name entered
        addFileButton.setEnabled(false);
		retrieveButton.setEnabled(false);
		deleteFileButton.setEnabled(false);
		lockerCreate.setEnabled(false);
		lockerLoad.setEnabled(false);
		selectPathButton.setEnabled(false);
        
        // Add a double bevel border to the center pane
        Border raisedbevel = BorderFactory.createRaisedBevelBorder();
        Border loweredbevel = BorderFactory.createLoweredBevelBorder();
        Border compound = BorderFactory.createCompoundBorder(raisedbevel, loweredbevel);
 
        //Create a panel that uses BoxLayout.
        JPanel buttonPane = new JPanel();
        JPanel outputPathPane = new JPanel();
        JPanel progressPane =  new JPanel();
        JPanel centerPane =  new JPanel();
        JPanel topPane = new JPanel();
        JPanel topPaneLabel = new JPanel();
        JPanel topPaneNaming = new JPanel();
        
        topPane.setLayout(new BoxLayout(topPane,
                BoxLayout.Y_AXIS));
        topPaneNaming.setLayout(new BoxLayout(topPaneNaming,
                BoxLayout.X_AXIS));
        progressPane.setLayout(new GridLayout(2,1));

        outputPathPane.setLayout(new BoxLayout(outputPathPane,
                BoxLayout.LINE_AXIS));
        buttonPane.setLayout(new BoxLayout(buttonPane,
                                           BoxLayout.LINE_AXIS));
        centerPane.setLayout(new BoxLayout(centerPane,
                BoxLayout.Y_AXIS));
        
        JPanel displayPane = new JPanel(new GridLayout(1,2));
        progressPane.add(progressLabel);
        progressPane.add(progressBar);
        JPanel detailsPane = new JPanel(new GridLayout(2,1));
        detailsPane.add(listScrollPane);
        detailsPane.add(stats);
        stats.setBorder(compound);
        details.setBorder(compound);
        console.setBorder(compound);
        listScrollPane.setBorder(compound);
        topPaneLabel.add(lockerLabel);
        topPaneNaming.add(lockerLoad);
        topPaneNaming.add(lockerCreate);
        topPaneNaming.add(lockerName);
        topPaneNaming.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
        topPane.add(topPaneLabel);
        topPane.add(topPaneNaming);
        displayPane.add(detailsPane);
        displayPane.add(details);
        topPane.add(displayPane);
        topPane.add(progressPane);
        topPane.setBorder(compound);

        outputPathPane.add(selectPathButton);
        outputPathPane.add(new JLabel(" Output Directory:  "));
        outputPathPane.add(outputPathLabel);
        
        buttonPane.add(retrieveButton);
        buttonPane.add(deleteFileButton);
        buttonPane.add(new JSeparator(SwingConstants.VERTICAL));
        buttonPane.add(addFileButton);

        centerPane.add(scroll);

        JPanel bottomPane = new JPanel(new GridLayout(2,1));
        bottomPane.add(outputPathPane);
        bottomPane.add(buttonPane);
        bottomPane.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
        bottomPane.setBorder(compound);
        
        add(bottomPane, BorderLayout.SOUTH);
        add(topPane, BorderLayout.NORTH);
        add(centerPane, BorderLayout.CENTER);
    }
 
    class retrieveFileListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
        	// Retrieve the file from the locker
        	if (list.getModel().getSize() > 0){
        		writeToConsole("Retrieving files...");
	        	try {
	        		for (String file : list.getSelectedValuesList()){
	        			boolean success = storage.retrieve(file, outputPath);
	        			if (success){
	        				writeToConsole(file + " retrieved to output directory as Copy" + file);
	        			}
	        		}
				} catch (UnsupportedEncodingException e1) {
					e1.printStackTrace();
				} catch (NoSuchAlgorithmException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					Toolkit.getDefaultToolkit().beep();
					writeToConsole("File could not be retrieved.");
				}
        	} else {
        		Toolkit.getDefaultToolkit().beep();
        		writeToConsole("No files to retrieve.");
        	}
        }
    }
    
    
    class deleteFileListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
        	if (list.getModel().getSize() > 0){
	            // Remove the file from the locker
        		writeToConsole("Deleting files...");
	        	try {
	        		for (String file : list.getSelectedValuesList()){
	        			boolean success = storage.delete(file);
	        			if (success){
	        				writeToConsole(file + " deleted");
	        			}
	        		}
					// Serialize the locker to a file
					storage.serializeToFile();
					// Update display
					stats.setText(storage.printDeDuplicationStatsString());
					details.setText(storage.printLockerContentsString());
				} catch (UnsupportedEncodingException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					writeToConsole("File could not be deleted.");

				}
	        	
	        	/*****************************/
	        	// Update the progress bar
	        	try {
	        		progressBar.setMaximum(storage.numFilesInLocker());
	        		progressBar.setValue((int) storage.getDeDuplicationValue());
				} catch (IOException e1) {
					e1.printStackTrace();
				}
	        	
	        	//This method can be called only if
	            //there's a valid selection
	            //so go ahead and remove whatever's selected.
	        	int[] indicesToDelete = list.getSelectedIndices();
	            for (int index = indicesToDelete.length - 1; index >= 0; index--){
	            	listModel.remove(indicesToDelete[index]);
	            }
	            
				try {
					progressBar.setValue((int) (storage.getDeDuplicationValue()));
		            progressBar.setMaximum((int) (storage.numFilesInLocker()));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
	 
	            int size = listModel.getSize();
	            if (size == 0) { //Nobody's left, disable firing.
	                retrieveButton.setEnabled(false);
	                deleteFileButton.setEnabled(false);
	                writeToConsole("Locker empty");
	        		Toolkit.getDefaultToolkit().beep();
	            } else { //Select an index.
	                list.setSelectedIndex(size - 1);
	                list.ensureIndexIsVisible(size - 1);
	            }
        	} else {
        		Toolkit.getDefaultToolkit().beep();
        		writeToConsole("No files to delete.");
        	}
        }
    }
    
    // Select file from System for insertion //
    /////////////////////////////////////////////////////
    class selectFileListener implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            String command = event.getActionCommand();
            if (command.equals(selectPathString)){
            	pathChooser.showDialog(pathChooser, "Select output path:");
            	outputPath = pathChooser.getSelectedFile().getPath();
            	outputPathLabel.setText(" " + outputPath.toString());
            }
        }
    }
 
    ///////////////////////////////////////////////////////////////////////
    //This listener is for the add file button.
    class addFileListener implements ActionListener, DocumentListener {
        private boolean alreadyEnabled = false;
        private JButton button;
 
        public addFileListener(JButton button) {
            this.button = button;
        }
 
        //Required by ActionListener.
        public void actionPerformed(ActionEvent e) {
        	String command = e.getActionCommand();
            if (command.equals(AddFileString)) {
            	writeToConsole("Adding files...");
				fileChooser.showDialog(fileChooser,
				        "Add File(s)");

				chosenFiles = fileChooser.getSelectedFiles();

				chosenFilePaths = new String[chosenFiles.length];
				for (int ii = 0; ii < chosenFiles.length; ii++){
					chosenFilePaths[ii] = chosenFiles[ii].getPath();
				}

            	try{
            		for (int ii = 0; ii < chosenFilePaths.length; ii++){
            			long start = System.currentTimeMillis();
	                    boolean success = storage.store(chosenFilePaths[ii]);
	                    long end = System.currentTimeMillis();
	                    if (success){
	                    	String filename = new File(chosenFilePaths[ii]).getName();
	                    	listModel.addElement(filename);
	                    	writeToConsole("Added " + filename + " in " + ((double)(end-start)/1000) + "s.");
	                    }
            		}
                    storage.serializeToFile();
					stats.setText(storage.printDeDuplicationStatsString());
					details.setText(storage.printLockerContentsString());
            	} catch (NoSuchAlgorithmException e1) {
					e1.printStackTrace();
            	} catch (IOException e1) {
					e1.printStackTrace();
            	}
            
            	int size = listModel.getSize();
	            list.setSelectedIndex(size-1);
	            list.ensureIndexIsVisible(size-1);
	            
	            //Update progress bar: for now simple 
	            progressBar.setIndeterminate(false);
				try {
	        		progressBar.setMaximum(storage.numFilesInLocker());
	        		progressBar.setValue((int) storage.getDeDuplicationValue());
				} catch (IOException e1) {
					e1.printStackTrace();
				}
            }
        }
 
        
        //This method tests for string equality. You could certainly
        //get more sophisticated about the algorithm.  For example,
        //you might want to ignore white space and capitalization.
        protected boolean alreadyInList(String name) {
            return listModel.contains(name);
        }
 
        //Required by DocumentListener.
        public void insertUpdate(DocumentEvent e) {
            enableButton();
        }
 
        //Required by DocumentListener.
        public void removeUpdate(DocumentEvent e) {
            handleEmptyTextField(e);
        }
 
        //Required by DocumentListener.
        public void changedUpdate(DocumentEvent e) {
            if (!handleEmptyTextField(e)) {
                enableButton();
            }
        }
 
        private void enableButton() {
            if (!alreadyEnabled) {
                button.setEnabled(true);
            }
        }
 
        private boolean handleEmptyTextField(DocumentEvent e) {
            if (e.getDocument().getLength() <= 0) {
                button.setEnabled(false);
                alreadyEnabled = false;
                return true;
            }
            return false;
        }
    }
 
    //This method is required by ListSelectionListener.
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() == false) {
 
            if (list.getSelectedIndex() == -1) {
            //No selection, disable retrieve
                retrieveButton.setEnabled(false);
 
            } else {
            //Selection, enable the retrieve button.
                retrieveButton.setEnabled(true);
            }
        }
    }
 
    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private static void createAndShowGUI() {
        //Create and set up the window.
        JFrame frame = new JFrame("Storage Locker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900,700));
        //Create and set up the content pane.
        JComponent newContentPane = new DedupGui();
        newContentPane.setMinimumSize(new Dimension(900,700));
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);
 
        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }
    
    public class createLockerListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			String command = e.getActionCommand();
			//Create the locker with the name specified the text field
			String filenameLocker = lockerName.getText();
			File f = new File(filenameLocker + ".txt");
			if (command.equals(createLockerString)){
				if (filenameLocker.length() > 0){
					// Create the locker
					storage = new Locker(filenameLocker);
					// Change the locker name and disable the naming field
					lockerLabel.setText("File Locker: " + filenameLocker);
					writeToConsole("Locker \"" + filenameLocker + "\" created");
					// Posts blanks info
					details.setText(storage.printLockerContentsString());
					
					// Enable the file select and retrieve
					deleteFileButton.setEnabled(true);
					retrieveButton.setEnabled(true);
					lockerCreate.setVisible(false);
					lockerLoad.setVisible(false);
					lockerName.setVisible(false);
					selectPathButton.setEnabled(true);
					addFileButton.setEnabled(true);
				} else {
					Toolkit.getDefaultToolkit().beep();
					writeToConsole("Enter a name for your locker.");
					return;
				}
			} else if (command.equals(loadLockerString)){
				if(f.exists()){  
	        		writeToConsole("Loading locker...");
					try {
						// Load the locker
						storage = loadDB(filenameLocker + ".txt");
						// Change the locker name and disable the naming field
						lockerLabel.setText("File Locker: " + filenameLocker);
						writeToConsole("Loaded locker \"" + filenameLocker + "\"");

						// Load locker contents to display
						ArrayList<String> fnames = storage.getFileNames();
						for (int ii = 0; ii < fnames.size(); ii++){
							listModel.insertElementAt(fnames.get(ii), ii);
							list.setSelectedIndex(ii);
							list.ensureIndexIsVisible(ii);
						}
						details.setText(storage.printLockerContentsString());
						stats.setText(storage.printDeDuplicationStatsString());
		        		progressBar.setMaximum(storage.numFilesInLocker());
		        		progressBar.setValue((int) storage.getDeDuplicationValue());

						
						// Enable the file select and retrieve
						deleteFileButton.setEnabled(true);
						retrieveButton.setEnabled(true);
						lockerCreate.setVisible(false);
						lockerLoad.setVisible(false);
						lockerName.setVisible(false);
						selectPathButton.setEnabled(true);
						addFileButton.setEnabled(true);
					} catch (ClassNotFoundException e1) {
						e1.printStackTrace();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				} else {
					Toolkit.getDefaultToolkit().beep();
					writeToConsole("Locker not found.");
					return;
				}
			}
		}

	}
    
	private static void writeToConsole(String s){
		console.append(s + "\n");
		console.setCaretPosition(console.getDocument().getLength());
	}
    
    private void suppressSystemStreams() {
    	OutputStream out = new OutputStream() {
    		@Override
    		public void write(int b) throws IOException {
    			return;
    		}
    		
    		@Override
    		public void write(byte[] b, int off, int len) throws IOException {
    			return;
    		}
    		
    		@Override
    		public void write(byte[] b) throws IOException {
    			return;
    		}
    	};

    	System.setOut(new PrintStream(out, true));
    	System.setErr(new PrintStream(out, true));
    }
    
	public static Locker loadDB (String filename) throws IOException, ClassNotFoundException{
		ObjectInputStream inStream = new ObjectInputStream(new FileInputStream(filename));
		Locker db = (Locker) inStream.readObject();
		inStream.close();
		return db;
	}
 
    public static void main(String[] args) throws UnsupportedEncodingException, NoSuchAlgorithmException, IOException {
        //creating and showing the GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}


