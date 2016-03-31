package edu.virginia.cs.index;

import edu.virginia.cs.utility.SpecialAnalyzer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class Indexer {

    /**
     * Creates the initial index files on disk
     *
     * @param indexPath
     * @return
     * @throws IOException
     */
    private static IndexWriter setupIndex(String indexPath) throws IOException {
        Analyzer analyzer = new SpecialAnalyzer();///special analyzer used here
        IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_46,
                analyzer);
        config.setOpenMode(OpenMode.CREATE);
        config.setRAMBufferSizeMB(2048.0);

        FSDirectory dir;
        IndexWriter writer = null;
        dir = FSDirectory.open(new File(indexPath));
        writer = new IndexWriter(dir, config);

        return writer;
    }

    /**
     * @param indexPath Where to create the index
     * @param prefix The prefix of all the paths in the fileList
     * @param fileList Each line is a path to a document
     * @throws IOException
     */
    public static void index(String indexPath, String prefix, String fileList)
            throws IOException {

        System.out.println("Creating Lucene index...");

        FieldType _contentFieldType = new FieldType();
        _contentFieldType.setIndexed(true);
        _contentFieldType.setStored(true);

        IndexWriter writer = setupIndex(indexPath);
        //indesed whole folder
        File folder = new File(prefix);
        File[] listOfFiles = folder.listFiles();
        System.out.println("File name: " + prefix);
        String line = null;
        String lineUrl = null;
        int indexed = 0;
        for (int i = 0; i < listOfFiles.length; i++) {
            File file = listOfFiles[i];
            if (file.isFile() && file.getName().endsWith(".txt")) {
                System.out.println("File name: " + file.getName());
                /* do somthing with content */
                BufferedReader br = new BufferedReader(new FileReader(prefix + file.getName()));
                while ((lineUrl = br.readLine()) != null) {
                    if ((line = br.readLine()) == null) //skiping url line
                    {
                        break;
                    }
                    Document doc = new Document();
                    doc.add(new Field("content", line, _contentFieldType));
                    doc.add(new Field("clicked_url", lineUrl, _contentFieldType));
                    writer.addDocument(doc);
                    ++indexed;
                    if (indexed % 1000 == 0) {
                        System.out.println(" -> indexed " + indexed + " docs...");
                    }
                }
                br.close();
            }
        }
        writer.close();
    }
}
