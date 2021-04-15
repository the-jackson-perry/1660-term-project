
// GUI imports
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.EventQueue;

// cloud storage imports
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageOptions;

// dataproc imports
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.dataproc.v1.HadoopJob;
import com.google.cloud.dataproc.v1.Job;
import com.google.cloud.dataproc.v1.JobControllerClient;
import com.google.cloud.dataproc.v1.JobControllerSettings;
import com.google.cloud.dataproc.v1.JobMetadata;
import com.google.cloud.dataproc.v1.JobPlacement;

// other imports
import java.util.*;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class FrontEndApp {

	private ArrayList<String> filesToUpload;
	private HashMap<String, ArrayList<SearchTermResult>> invertedIndex;

	private JFrame frmSearchEngine;
	private JTextField searchTermEntry;
	private JTextField nValueEntry;

	private static String cloudUser;
	private static String cloudBucket;
	private static String projectId = "term-project-24680";
	private static String region = "us-east1";
	private static String clusterName = "term-project-cluster";


	public static ArrayList<String> stringToList(String s) {
    return new ArrayList<>(Arrays.asList(s.split(" ")));
  }

	public static void main(String[] args) {

		try {
		    Scanner inScanner = new Scanner(new File("cloudConfig.txt"));
		    while (inScanner.hasNextLine()) {
		    	cloudUser = inScanner.next();
		    	cloudBucket = inScanner.next();
		    	projectId = inScanner.next();
		    	region = inScanner.next();
		    	clusterName = inScanner.next();
		    }
		    inScanner.close();
		} catch(FileNotFoundException fnfe){
		    System.out.println("An reading in file.");
		    fnfe.printStackTrace();
		} catch(Exception e){
			e.printStackTrace();
		}

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					FrontEndApp window = new FrontEndApp();
					window.frmSearchEngine.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public FrontEndApp() {
		initialize();
	}

	/*******************************************************************************************
	*	Method to start up the GUI on the file selection page
	********************************************************************************************/
	private void initialize(){
		filesToUpload = new ArrayList();
		frmSearchEngine = new JFrame();
		frmSearchEngine.getContentPane().setBackground(Color.WHITE);
		frmSearchEngine.setTitle("JacksonPerry Search Engine");
		frmSearchEngine.setBounds(100, 100, 750, 550);
		frmSearchEngine.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmSearchEngine.getContentPane().setLayout(null);
		
		JLabel searchedTermLabel = new JLabel("Load My Engine");
		searchedTermLabel.setHorizontalAlignment(SwingConstants.CENTER);
		searchedTermLabel.setFont(new Font("Tahoma", Font.BOLD, 20));
		searchedTermLabel.setBackground(Color.BLACK);
		searchedTermLabel.setBounds(199, 66, 334, 35);
		frmSearchEngine.getContentPane().add(searchedTermLabel);

		JTextArea filesLabels = new JTextArea();
		filesLabels.setBounds(250, 236, 234, 137);
		frmSearchEngine.getContentPane().add(filesLabels);
		
		JButton selectFilesButton = new JButton("Choose Files");
		selectFilesButton.setBackground(new Color(211, 211, 211));
		selectFilesButton.setFont(new Font("Tahoma", Font.PLAIN, 15));
		selectFilesButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser fc = new JFileChooser("/usr/src/app/Data");
				fc.setMultiSelectionEnabled(true);
				int returnVal = fc.showDialog(null, "Select");
				File[] selectedFiles = fc.getSelectedFiles();
				String currPath;
				String[] splitPath;
				String fileName;
				String fileNameStrList = "";
				filesToUpload.clear();
				for(int i=0; i<selectedFiles.length; i++){
					currPath = selectedFiles[i].getAbsolutePath();
					if(!filesToUpload.contains(currPath)){
						//System.out.println(currPath);
						filesToUpload.add(currPath);
						splitPath = currPath.split("/");
						fileName = splitPath[splitPath.length-1];
						fileNameStrList += fileName+"\n";
					}
				}
				filesLabels.setText(fileNameStrList);
			}
		});
		selectFilesButton.setBounds(302, 183, 127, 40);
		frmSearchEngine.getContentPane().add(selectFilesButton);
		
		JButton fileUploadButton = new JButton("Confirm File Selection");
		fileUploadButton.setBackground(new Color(211, 211, 211));
		fileUploadButton.setFont(new Font("Tahoma", Font.PLAIN, 15));
		fileUploadButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				deleteBucketDirectory("input/");
				String currPath;
				String[] splitPath;
				String fileName;
				for(int i=0; i<filesToUpload.size(); i++){
					currPath = filesToUpload.get(i);
					splitPath = currPath.split("/");
					fileName = splitPath[splitPath.length-2]+"-"+splitPath[splitPath.length-1];
					try{ 
						uploadObject(fileName,currPath);
					} catch(IOException ioe){
						System.out.println(ioe.toString());
						System.exit(1);
					}
				}
				loadActionSelectionPage();
			}
		});
		fileUploadButton.setBounds(250, 386, 234, 59);
		frmSearchEngine.getContentPane().add(fileUploadButton);
	}

	/*******************************************************************************************
	*	Method to load the page for selecting Search-term vs Top-N
	********************************************************************************************/
	private void loadActionSelectionPage(){
		frmSearchEngine.getContentPane().removeAll();

		JLabel selectActionLabel = new JLabel("Please Select an Action");
		selectActionLabel.setHorizontalAlignment(SwingConstants.CENTER);
		selectActionLabel.setFont(new Font("Tahoma", Font.BOLD, 20));
		selectActionLabel.setBackground(Color.BLACK);
		selectActionLabel.setBounds(199, 171, 334, 35);
		frmSearchEngine.getContentPane().add(selectActionLabel);

		JButton searchTermButton = new JButton("Construct Inverted Indices for Search");
		searchTermButton.setBackground(new Color(211, 211, 211));
		searchTermButton.setFont(new Font("Tahoma", Font.PLAIN, 15));
		searchTermButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				runConstructIndicesJob();
				loadResultFileToHashmap();
				loadSearchForTermPage();
			}
		});
		searchTermButton.setBounds(211, 272, 309, 59);
		frmSearchEngine.getContentPane().add(searchTermButton);
		
		JButton topNButton = new JButton("Top-N");
		topNButton.setFont(new Font("Tahoma", Font.PLAIN, 15));
		topNButton.setBackground(new Color(211, 211, 211));
		topNButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				runWordCountJob();
				loadTopNPage();
			}
		});
		topNButton.setBounds(211, 357, 309, 59);
		frmSearchEngine.getContentPane().add(topNButton);

		frmSearchEngine.repaint();
	}

	/*******************************************************************************************
	*	Methods to load the search-term/nValue user input pages
	********************************************************************************************/
	private void loadSearchForTermPage(){	
		frmSearchEngine.getContentPane().removeAll();

		JLabel enterSearchTermLabel = new JLabel("Enter Your Search Term");
		enterSearchTermLabel.setHorizontalAlignment(SwingConstants.CENTER);
		enterSearchTermLabel.setFont(new Font("Tahoma", Font.BOLD, 20));
		enterSearchTermLabel.setBackground(Color.BLACK);
		enterSearchTermLabel.setBounds(199, 171, 334, 35);
		frmSearchEngine.getContentPane().add(enterSearchTermLabel);
		
		searchTermEntry = new JTextField();
		searchTermEntry.setToolTipText("");
		searchTermEntry.setBounds(222, 231, 288, 22);
		frmSearchEngine.getContentPane().add(searchTermEntry);
		searchTermEntry.setColumns(10);

		JButton searchButton = new JButton("Search");
		searchButton.setBackground(new Color(211, 211, 211));
		searchButton.setFont(new Font("Tahoma", Font.PLAIN, 15));
		searchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				long searchStartTime = System.currentTimeMillis();
				ArrayList<SearchTermResult> searchResultList = invertedIndex.get((searchTermEntry.getText().toUpperCase()));
				long searchTime = System.currentTimeMillis() - searchStartTime;
				loadSearchResultPage(searchTermEntry.getText().toUpperCase(), searchTime, searchResultList);
			}
		});
		searchButton.setBounds(249, 302, 234, 59);
		frmSearchEngine.getContentPane().add(searchButton);

		frmSearchEngine.repaint();
	}

	private void loadTopNPage(){
		frmSearchEngine.getContentPane().removeAll();

		//add new contents
		JLabel enterNValueLabel = new JLabel("Enter Your N Value");
		enterNValueLabel.setHorizontalAlignment(SwingConstants.CENTER);
		enterNValueLabel.setFont(new Font("Tahoma", Font.BOLD, 20));
		enterNValueLabel.setBackground(Color.BLACK);
		enterNValueLabel.setBounds(199, 171, 334, 35);
		frmSearchEngine.getContentPane().add(enterNValueLabel);
		
		nValueEntry = new JTextField();
		nValueEntry.setToolTipText("");
		nValueEntry.setBounds(222, 231, 288, 22);
		frmSearchEngine.getContentPane().add(nValueEntry);
		nValueEntry.setColumns(3);

		JButton searchButton = new JButton("Search");
		searchButton.setBackground(new Color(211, 211, 211));
		searchButton.setFont(new Font("Tahoma", Font.PLAIN, 15));
		searchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try{
					int nValue = Integer.parseInt(nValueEntry.getText());
					runTopNJob(nValue);
					loadTopNResultPage(nValue);
				} catch(InputMismatchException imme){
					imme.printStackTrace();
				} catch(Exception e){
					e.printStackTrace();
				}

				
			}
		});
		searchButton.setBounds(249, 302, 234, 59);
		frmSearchEngine.getContentPane().add(searchButton);

		frmSearchEngine.repaint();
	}

	/*******************************************************************************************
	*	Methods to load the GUI results pages
	********************************************************************************************/

	private void loadSearchResultPage(String searchTerm, long searchTime, ArrayList<SearchTermResult> resultsList){
		frmSearchEngine.getContentPane().removeAll();
		//TODO: complete this method
		JLabel searchedTermLabel = new JLabel("You searched for the term: "+searchTerm);
		searchedTermLabel.setBounds(12, 13, 381, 16);
		frmSearchEngine.getContentPane().add(searchedTermLabel);
		
		JLabel searchTimeLabel = new JLabel("Your search was executed in "+searchTime+" ms");
		searchTimeLabel.setBounds(12, 42, 419, 16);
		frmSearchEngine.getContentPane().add(searchTimeLabel);
		
		JLabel backToSearchLink = new JLabel("Go Back To Search");
		backToSearchLink.setForeground(Color.BLUE);
		backToSearchLink.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
				loadSearchForTermPage();
			}
			public void mousePressed(MouseEvent e){}
			public void mouseReleased(MouseEvent e){}
			public void mouseEntered(MouseEvent e){}
			public void mouseExited(MouseEvent e){}
		});
		backToSearchLink.setBounds(539, 23, 181, 16);
		frmSearchEngine.getContentPane().add(backToSearchLink);

		

		String[] columnNames = {"Doc Folder", "Doc Name", "Frequencies"};
		Object[][] tableContents;
		if(resultsList!=null){
			tableContents = new Object[resultsList.size()][3];
			SearchTermResult entry;
			for(int i=0; i<resultsList.size(); i++){
				entry = resultsList.get(i);
				tableContents[i][0] = entry.docFolder;
				tableContents[i][1] = entry.docName;
				tableContents[i][2] = new Integer(entry.frequency);
			}
		} else{
			tableContents = new Object[0][3];
		}
		JTable table = new JTable(tableContents, columnNames); // JTableExtraCredit

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(table);

		scrollPane.setBounds(22, 87, 680, 386);
		frmSearchEngine.getContentPane().add(scrollPane);
		
		frmSearchEngine.repaint();
	}

	private void loadTopNResultPage(int nValue){
		frmSearchEngine.getContentPane().removeAll();
		
		JLabel topNResultLabel = new JLabel("Top N Frequent Terms");
		topNResultLabel.setBounds(12, 47, 381, 16);
		frmSearchEngine.getContentPane().add(topNResultLabel);
		
		JLabel backToSearchLink = new JLabel("Go Back To Search");
		backToSearchLink.setForeground(Color.BLUE);
		backToSearchLink.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
				loadTopNPage();
			}
			public void mousePressed(MouseEvent e){}
			public void mouseReleased(MouseEvent e){}
			public void mouseEntered(MouseEvent e){}
			public void mouseExited(MouseEvent e){}
		});
		backToSearchLink.setBounds(539, 23, 181, 16);
		frmSearchEngine.getContentPane().add(backToSearchLink);
		

		String[] columnNames = {"Term", "Total Frequencies"};
		Object[][] tableContents = loadTopNResultFile(nValue);

		JTable table = new JTable(tableContents, columnNames); // JTableExtraCredit

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(table);

		scrollPane.setBounds(22, 87, 680, 386);
		frmSearchEngine.getContentPane().add(scrollPane);

		frmSearchEngine.repaint();
	}

	/*******************************************************************************************
	*	Methods to load the result files supplied by the cloud portion of the project
	********************************************************************************************/

	private void loadResultFileToHashmap(){
		invertedIndex = new HashMap<>();

	    try {
	        Scanner infile = new Scanner(new File("result"));
	        StringTokenizer line;
	        String word;
	        String docEntry;
	        String[] entryComponents;
	        String[] pathSplit;
	        int frequency;
	        SearchTermResult entry;
	        ArrayList<SearchTermResult> entryList;
	        while (infile.hasNext()) {
	        	line = new StringTokenizer(infile.nextLine());

	        	word = line.nextToken();
	        	while(line.hasMoreTokens()){
	        		docEntry = line.nextToken();
	        		entryComponents = docEntry.split(":");
	        		frequency = Integer.parseInt(entryComponents[1]);
	        		pathSplit = entryComponents[0].split("/");

		        	entry = new SearchTermResult(pathSplit[0], pathSplit[1], frequency);
		        	if(!invertedIndex.containsKey(word)){
		        		entryList = new ArrayList<>();
		        		entryList.add(entry);
		        		invertedIndex.put(word,entryList);
		        	} else{
		        		invertedIndex.get(word).add(entry);
		        	}
	        	}
	        }
	        infile.close();
	    } 
	    catch (FileNotFoundException e) {
	        e.printStackTrace();
	    }
	}

	private Object[][] loadTopNResultFile(int nValue){
		Object[][] resultsMatrix = new Object[nValue][2];
		try {
	        Scanner infile = new Scanner(new File("result"));

	        int i=0;
	        while (infile.hasNext() && i<nValue) {
	        	resultsMatrix[i][1] = infile.nextInt();
	        	resultsMatrix[i][0] = infile.next();
	        	i++;
	        }
	        infile.close();

	        if(i<nValue){//if there are less words that nValue, shrink the result matrix
	        	Object[][] shrunkMatrix = new Object[i][2];
	        	for(int j=0; j<i; j++){
	        		shrunkMatrix[j][0] = resultsMatrix[j][0];
	        		shrunkMatrix[j][1] = resultsMatrix[j][1];
	        	}
	        	resultsMatrix = shrunkMatrix;
	        }
	        return  resultsMatrix;
	    } 
	    catch (FileNotFoundException e) {
	    }
	    return new Object[0][2];
	}

	/*******************************************************************************************
	*	Methods that call a sequence of other methods to complete a job and download results
	********************************************************************************************/

	private void runConstructIndicesJob(){
		try{
			deleteBucketDirectory("output/");
			submitHadoopJob("ConstructInvertedIndices",
							"gs://"+cloudBucket+"/jars/ConstructInvertedIndices.jar",
							"gs://"+cloudBucket+"/input gs://"+cloudBucket+"/output"
							);
			submitHadoopFSJob("-getmerge gs://"+cloudBucket+"/output /home/"+cloudUser+"/result");
			submitHadoopFSJob("-put /home/"+cloudUser+"/result gs://"+cloudBucket+"/output/result");
			downloadObject("output/result", "/usr/src/app/result");
		}
		catch(Exception e){
			System.out.println(e.toString());
			System.exit(1);
		}
	}

	private void runWordCountJob(){
		try{
			deleteBucketDirectory("wordcountOutput/");
			submitHadoopJob("WordCount",
							"gs://"+cloudBucket+"/jars/WordCount.jar",
							"gs://"+cloudBucket+"/input gs://"+cloudBucket+"/wordcountOutput"
							);
			//submitHadoopFSJob("-getmerge gs://"+cloudBucket+"/wordcountOutput /home/"+cloudUser+"/result");
			//submitHadoopFSJob("-put /home/"+cloudUser+"/result gs://"+cloudBucket+"/wordcountOutput/resultDir/result");
		}
		catch(Exception e){
			System.out.println(e.toString());
			System.exit(1);
		}
	}

	private void runTopNJob(int nValue){
		try{
			deleteBucketDirectory("output/");
			submitHadoopJob("TopN",
							"gs://"+cloudBucket+"/jars/TopN.jar",
							"gs://"+cloudBucket+"/wordcountOutput gs://"+cloudBucket+"/output "+nValue
							);
			submitHadoopFSJob("-getmerge gs://"+cloudBucket+"/output /home/"+cloudUser+"/result");
			submitHadoopFSJob("-put /home/"+cloudUser+"/result gs://"+cloudBucket+"/output/result");
			downloadObject("output/result", "/usr/src/app/result");
		}
		catch(Exception e){
			System.out.println(e.toString());
			System.exit(1);
		}
	}


	/*******************************************************************************************
	*	Methods for interacting with google cloud storage
	********************************************************************************************/

	private void uploadObject(String objectNameInBucket, String filePath) throws IOException {
		String projectId = "term-project-24680";
		String bucketName = cloudBucket;

		Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
		BlobId blobId = BlobId.of(bucketName, "input/"+objectNameInBucket);
		BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
		storage.create(blobInfo, Files.readAllBytes(Paths.get(filePath)));
	}

	private void downloadObject(String objectNameInBucket, String destinationFilePath) throws IOException {
		String projectId = "term-project-24680";
		String bucketName = cloudBucket;

		Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
		Blob blob = storage.get(BlobId.of(bucketName, objectNameInBucket));
    	blob.downloadTo(Paths.get(destinationFilePath));
	}

	private void deleteBucketDirectory(String directory){
		String projectId = "term-project-24680";
		String bucketName = cloudBucket;
		
		Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();

		Page<Blob> blobs = storage.list(bucketName, BlobListOption.prefix(directory));
		Iterator<Blob> blobIterator = blobs.iterateAll().iterator();
		while (blobIterator.hasNext()) {
			Blob blob = blobIterator.next();
			blob.delete();
		}
	}


	/*******************************************************************************************
	*	Methods to submit jobs on the dataproc cluster
	********************************************************************************************/
	
	public void submitHadoopJob(String mainClass, String pathToJar, String additionalArgs) throws IOException, InterruptedException {

		String myEndpoint = String.format("%s-dataproc.googleapis.com:443", region);

		// Configure the settings for the job controller client.
		JobControllerSettings jobControllerSettings =
		    JobControllerSettings.newBuilder().setEndpoint(myEndpoint).build();

		// Create a job controller client with the configured settings. Using a try-with-resources
		// closes the client,
		// but this can also be done manually with the .close() method.
		try (JobControllerClient jobControllerClient =
		    JobControllerClient.create(jobControllerSettings)) {

			// Configure cluster placement for the job.
			JobPlacement jobPlacement = JobPlacement.newBuilder().setClusterName(clusterName).build();

			// Configure Hadoop job settings.
			HadoopJob hadoopJob =
			    HadoopJob.newBuilder()
			    .setMainJarFileUri(pathToJar)
			    .addAllArgs(stringToList(mainClass+" "+additionalArgs))
			    .build();

			Job job = Job.newBuilder().setPlacement(jobPlacement).setHadoopJob(hadoopJob).build();

			// Submit an asynchronous request to execute the job.
			OperationFuture<Job, JobMetadata> submitJobAsOperationAsyncRequest =
				jobControllerClient.submitJobAsOperationAsync(projectId, region, job);

			Job response = submitJobAsOperationAsyncRequest.get();

			// Print output from Google Cloud Storage.
			Matcher matches =
			    Pattern.compile("gs://(.*?)/(.*)").matcher(response.getDriverOutputResourceUri());
			matches.matches();

			Storage storage = StorageOptions.getDefaultInstance().getService();
			Blob blob = storage.get(matches.group(1), String.format("%s.000000000", matches.group(2)));

			/*System.out.println(
				String.format("Job finished successfully: %s", new String(blob.getContent())));*/

		} catch (ExecutionException e) {
			// If the job does not complete successfully, print the error message.
			System.err.println(String.format("submitHadoopJob: %s ", e.getMessage()));
		}
	}

	private void submitHadoopFSJob(String arguments) throws IOException, InterruptedException {

	    String myEndpoint = String.format("%s-dataproc.googleapis.com:443", region);

	    // Configure the settings for the job controller client.
	    JobControllerSettings jobControllerSettings =
	        JobControllerSettings.newBuilder().setEndpoint(myEndpoint).build();

	    // Create a job controller client with the configured settings. Using a try-with-resources
	    // closes the client,
	    // but this can also be done manually with the .close() method.
	    try (JobControllerClient jobControllerClient =
	        JobControllerClient.create(jobControllerSettings)) {

	      // Configure cluster placement for the job.
	      JobPlacement jobPlacement = JobPlacement.newBuilder().setClusterName(clusterName).build();

	      // Configure Hadoop job settings. The HadoopFS query is set here.
	      HadoopJob hadoopJob =
	          HadoopJob.newBuilder()
	              .setMainClass("org.apache.hadoop.fs.FsShell")
	              .addAllArgs(stringToList(arguments))
	              .build();

	      Job job = Job.newBuilder().setPlacement(jobPlacement).setHadoopJob(hadoopJob).build();

	      // Submit an asynchronous request to execute the job.
	      OperationFuture<Job, JobMetadata> submitJobAsOperationAsyncRequest =
	          jobControllerClient.submitJobAsOperationAsync(projectId, region, job);

	      Job response = submitJobAsOperationAsyncRequest.get();

	      // Print output from Google Cloud Storage.
	      Matcher matches =
	          Pattern.compile("gs://(.*?)/(.*)").matcher(response.getDriverOutputResourceUri());
	      matches.matches();

	      Storage storage = StorageOptions.getDefaultInstance().getService();
	      Blob blob = storage.get(matches.group(1), String.format("%s.000000000", matches.group(2)));

	      /*System.out.println(
	          String.format("Job finished successfully: %s", new String(blob.getContent())));*/

	    } catch (ExecutionException e) {
	      // If the job does not complete successfully, print the error message.
	      System.err.println(String.format("submitHadoopFSJob: %s ", e.getMessage()));
	    }

	}

	/*******************************************************************************************
	*	Inner classes to store elements from the results of the hadoop jobs
	********************************************************************************************/
	private class SearchTermResult{
		private String docFolder;
		private String docName;
		private int frequency;

		private SearchTermResult(String docFolder, String docName, int frequency){
			this.docFolder = docFolder;
			this.docName = docName;
			this.frequency = frequency;
		}
	}

}