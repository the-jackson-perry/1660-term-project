import java.io.*;
import java.util.*;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class TopN{
    public static class TopN_Mapper extends Mapper<Object,
                            Text, LongWritable, Text> {
  
        private TreeMap<Long, String> tmap;
      
        @Override
        public void setup(Context context) throws IOException,
                                         InterruptedException
        {
            tmap = new TreeMap<Long, String>();
        }
      
        @Override
        public void map(Object key, Text value,
           Context context) throws IOException, 
                          InterruptedException
        {
      
            // input data format => movie_name    
            // no_of_views  (tab seperated)
            // we split the input data
            String[] tokens = value.toString().split("\t");
      
            String term = tokens[0];
            long frequency = Long.parseLong(tokens[1]);
      
            // insert data into treeMap,
            // we want top 10  viewed movies
            // so we pass no_of_views as key
            tmap.put(frequency, term);
      
            // we remove the first key-value
            Configuration conf = context.getConfiguration();
            int nVal = Integer.parseInt(conf.get("nValue", ""+3)); //default nValue = 3
            if (tmap.size() > nVal)
            {
                tmap.remove(tmap.firstKey());
            }



        }
      
        @Override
        public void cleanup(Context context) throws IOException,
                                           InterruptedException
        {
            for (Map.Entry<Long, String> entry : tmap.entrySet()) 
            {
      
                long frequency = entry.getKey();
                String term = entry.getValue();
      
                context.write(new LongWritable(1), new Text(frequency+":"+term)); //frequency is output negative so that sort is made descending instead of ascending
            }
        }
    }

    public static class TopN_Reducer extends Reducer<LongWritable, Text, LongWritable, Text> {
  
        private TreeMap<Long,String> tmap2;
      
        @Override
        public void setup(Context context) throws IOException,
                                         InterruptedException
        {
            tmap2 = new TreeMap<Long,String>();
        }
      
        @Override
        public void reduce(LongWritable key, Iterable<Text> values, 
          Context context) throws IOException, InterruptedException
        {
      
            String[] valComponents;
            String term;
            long frequency;
            
            
            for (Text val : values)
            {
                valComponents = val.toString().split(":");
                frequency = Long.parseLong(valComponents[0]);
                term = valComponents[1];
                tmap2.put(frequency,term);

                // we remove the first entry if the treeset size surpasses N
                Configuration conf = context.getConfiguration();
                int nVal = Integer.parseInt(conf.get("nValue", ""+3)); //default nValue = 3
                if (tmap2.size() > nVal)
                {
                    tmap2.remove(tmap2.firstKey());
                }
            }
      

        }
      
        @Override
        public void cleanup(Context context) throws IOException,
                                           InterruptedException
        {
            String [] entryComponents;
            long frequency;
            String term;
            for (Map.Entry<Long, String> entry : tmap2.descendingMap().entrySet())
            {
                frequency = entry.getKey();
                term = entry.getValue();
                context.write(new LongWritable(frequency), new Text(term));
            }
        }
    }



    public static void main(String[] args) throws Exception
    {
        Configuration conf = new Configuration();
        String[] otherArgs = new GenericOptionsParser(conf,
                                  args).getRemainingArgs();
  
        if (otherArgs.length < 3) 
        {
            System.err.println("Error: please provide two paths and an N-value");
            System.exit(2);
        }

        conf.set("nValue", otherArgs[2]);
  
        Job job = Job.getInstance(conf, "top N");
        job.setJarByClass(TopN.class);
  
        job.setMapperClass(TopN_Mapper.class);
        job.setReducerClass(TopN_Reducer.class);
  
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(Text.class);
  
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
  
        FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
        FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
  
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }


}
  
