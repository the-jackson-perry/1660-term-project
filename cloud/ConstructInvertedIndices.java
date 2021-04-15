
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.HashSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

public class ConstructInvertedIndices {


    public static class TokenizerMapper extends Mapper<Object, Text, Text, Text>{

        private boolean caseSensitive;
        private Set<String> patternsToSkip = new HashSet<String>();

        private Configuration conf;
        private BufferedReader fis;

        @Override
        public void setup(Context context) throws IOException,
                InterruptedException {
            conf = context.getConfiguration();
            caseSensitive = conf.getBoolean("wordcount.case.sensitive", true);
            if (conf.getBoolean("wordcount.skip.patterns", false)) {
                URI[] patternsURIs = Job.getInstance(conf).getCacheFiles();
                for (URI patternsURI : patternsURIs) {
                    Path patternsPath = new Path(patternsURI.getPath());
                    String patternsFileName = patternsPath.getName().toString();
                    parseSkipFile(patternsFileName);
                }
            }
        }

        private void parseSkipFile(String fileName) {
            try {
                fis = new BufferedReader(new FileReader(fileName));
                String pattern = null;
                while ((pattern = fis.readLine()) != null) {
                    patternsToSkip.add(pattern);
                }
            } catch (IOException ioe) {
                System.err.println("Caught exception while parsing the cached file '"
                        + StringUtils.stringifyException(ioe));
            }
        }

        public void map(Object key, Text value, Context context
                                        ) throws IOException, InterruptedException {
            String line = value.toString().toUpperCase();
            StringTokenizer itr = new StringTokenizer(line);

            HashMap<String,Integer> h   = new HashMap<>();

            // get the Unique identifier for the document. In our case it is folder/filename
            String[] filePath = ((FileSplit) context.getInputSplit()).getPath().toString().split("/");
            String docIdentifier = filePath[filePath.length-1].replace("-","/");
            String nextTerm;

            // while there are words left, process them and emit the result
            while (itr.hasMoreTokens()) {
                //strip unneccesary punctuation, parentheses, quotes and such
                nextTerm = itr.nextToken();
                if(nextTerm.length()>1 && nextTerm.charAt(0)=='\'' && nextTerm.charAt(nextTerm.length()-1)=='\''){
                    nextTerm = nextTerm.substring(1,nextTerm.length()-1);
                }
                while(nextTerm.length()>0 && (nextTerm.charAt(nextTerm.length()-1) == '-' || nextTerm.charAt(nextTerm.length()-1) == '\'')){
                    nextTerm = nextTerm.substring(0,nextTerm.length()-1);
                }
                nextTerm = nextTerm.replace(",","").replace(".","").replace(";","").replace(":","").replace("!","").replace("?","").replace("[","").replace("]","").replace("(","").replace(")","");
                if(nextTerm.length()!=0){
                    if(!h.containsKey(nextTerm)){
                        h.put(nextTerm, 1);
                    } else{
                        h.put(nextTerm, h.get(nextTerm) + 1);
                    }
                }
            }
                
            for(String term: h.keySet()){
                context.write(new Text(term), new Text(docIdentifier+":"+h.get(term)));
            }
        }
    }

    public static class IntSumReducer extends Reducer<Text,Text,Text,Text> {

        public void reduce(Text term, Iterable<Text> postings,
                                Context context
                                ) throws IOException, InterruptedException {

            HashMap<String,Integer> p = new HashMap<>();
            
            String[] postingElements;
            String[] elementPair;
            String docIdentifier;
            Integer frequency;
            for(Text posting : postings) {
                postingElements = posting.toString().split(" "); //splits a posting into an array of [docIdentifier,frequency] pairs
                for(String postingElement : postingElements){
                    elementPair = posting.toString().split(":"); //splits pair into components
                    docIdentifier = elementPair[0];
                    frequency = Integer.parseInt(elementPair[1].replace(" ",""));

                    if(!p.containsKey(docIdentifier)) {
                        p.put(docIdentifier, frequency);
                    } else {
                        p.put(docIdentifier, p.get(docIdentifier) + frequency); // aggregate frequencies by document
                    }
                }
            }

            // build up the output value
            StringBuilder outputValue = new StringBuilder();
            for(String docID : p.keySet()){
                outputValue.append(docID);
                outputValue.append(":");
                outputValue.append(p.get(docID));
                outputValue.append(" ");
            }
            // emit term,outputValue
            context.write(term, new Text(outputValue.toString()));
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        GenericOptionsParser optionParser = new GenericOptionsParser(conf, args);
        String[] remainingArgs = optionParser.getRemainingArgs();
        if ((remainingArgs.length != 2) && (remainingArgs.length != 4)) {
            System.err.println("Usage: ConstructInvertedIndices <in> <out> [-skip skipPatternFile]");
            System.exit(2);
        }
        Job job = Job.getInstance(conf, "construct inverted indices");
        job.setJarByClass(ConstructInvertedIndices.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setCombinerClass(IntSumReducer.class);
        job.setReducerClass(IntSumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        List<String> otherArgs = new ArrayList<String>();
        for (int i=0; i < remainingArgs.length; ++i) {
            if ("-skip".equals(remainingArgs[i])) {
                job.addCacheFile(new Path(remainingArgs[++i]).toUri());
                job.getConfiguration().setBoolean("wordcount.skip.patterns", true);
            } else {
                otherArgs.add(remainingArgs[i]);
            }
        }
        FileInputFormat.addInputPath(job, new Path(otherArgs.get(0)));
        FileOutputFormat.setOutputPath(job, new Path(otherArgs.get(1)));

        int exitCode = job.waitForCompletion(true) ? 0 : 1;
        System.exit(exitCode);
    }
}