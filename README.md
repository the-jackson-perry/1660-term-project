#Setup Instructions (OS: windows)

##1) Setting up cloud stuff part 1

###a) Create a project on GCP with billing enabled. Also make sure that cloud storage and dataproc are enabled

###b) Create a new bucket in cloud storage (I created mine in us-east1)

###c) Create a new cluster on dataproc (I created mine in us-east1-b with 1 master node and 2 worker nodes running 2.0.8-debian10)

###d) Create a new service account

####i) Download the keys for the service account. We will need them later in the setup.

###e) Give your service account full permissions to your storage bucket and cluster (if you are having issues with permissions try giving it owner permissions)


##2) Set up local stuff

###a) Make sure you have github and docker installed and working on your computer

###b) Clone my github repository ```git clone https://github.com/the-jackson-perry/1660-term-project.git```

###c) Save the GCP service account key json as 'service-account-key.json' in the '1660-term-project/local/' directory of the repository you just cloned

###d) Open the file 'cloudConfig.txt' located in the '1660-term-project/local/' directory and fill in your details according to your specific cloud settings. Replace my settings that are already in the file. The items you need in order are:
- Google username (my email is 'jacksonperry12345@gmail.com' so I use 'jacksonperry12345')
- storage bucket name
- projectId
- region you created your cluster and bucket in
- clusterName

###e) Navigate to the '1660-term-project/local/' directory and run the following command to build the docker image ```docker build -t front-end-app .```


##3) Setting up cloud stuff part 2

###a) Copy the following files from the '1660-term-project/cloud/' directory to the dataproc cluster master-node (I upload them to a different cloud storage bucket in the project then use gsutil to copy them to the node):
- ConstructInvertedIndices.java
- WordCount.java
- TopN.java
- manifest.mf
- manifest2.mf
- manifest3.mf

###b) Also copy the 'jars' folder from the '1660-term-project/cloud/' directory to the master-node.

###c) Compile the jars on the master-node by using the following commands:
- ```javac -cp ".:/home/jacksonperry12345/jars/*" ConstructInvertedIndices.java```
- ```jar -cvfm ConstructInvertedIndices.jar manifest.mf *.class```
- ```rm *.class```
- ```javac -cp ".:/home/jacksonperry12345/jars/*" WordCount.java```
- ```jar -cvfm WordCount.jar manifest.mf *.class```
- ```rm *.class```
- ```javac -cp ".:/home/jacksonperry12345/jars/*" TopN.java```
- ```jar -cvfm TopN.jar manifest.mf *.class```

###d) Create a folder called 'jars' in the storage bucket from step 1b called 'jars'

###e) Copy the following files from the master-node into the 'jars' folder of the bucket:
- ConstructInvertedIndices.jar
- WordCount.jar
- TopN.jar


##4) Running the application

###a) Make sure that you have VcXsrv installed and configured correctly. Start it up. Instructions  can be found here: https://dev.to/darksmile92/run-gui-app-in-linux-docker-container-on-windows-host-4kde

###b) Run the command ```ipconfig``` to find your ip. ('Ethernet adapter vEthernet (WSL): IPv4 Address:' is the label I found my IP at.)

###c) Run the following command with the IP you found in the previous step ```docker run -it -e DISPLAY=<yourIP>:0.0 front-end-app```

####i) Some of the operations in the program take a while. If you press a button and it does not do something quickly, just give it some time.

#Extra Credit Option

I did the extra credit of using JTable to display the results to both term-search and top-n. If you Ctrl+f and search for 'JTableExtraCredit', you can easily find the place I use the JTables because I left some comments in the code with that term.
