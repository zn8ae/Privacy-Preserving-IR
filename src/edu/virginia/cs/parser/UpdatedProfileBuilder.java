/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.virginia.cs.parser;

import edu.virginia.cs.index.Searcher;
import edu.virginia.cs.utility.SpecialAnalyzer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import edu.virginia.cs.utility.FileOperations;

/**
 *
 * @author Wasi
 */
public class UpdatedProfileBuilder {

    private final String _indexPath = "lucene-AOL-index";
    private FileOperations fiop;
    private HashMap<String, HashSet<String>> queryToJudgement;
    private Searcher _searcher = null;
    private SpecialAnalyzer analyzer;
    private QueryParser parser;

    public UpdatedProfileBuilder() {
        fiop = new FileOperations();
        _searcher = new Searcher(_indexPath);
        analyzer = new SpecialAnalyzer();
        parser = new QueryParser(Version.LUCENE_46, "", analyzer);
    }

    private void LoadFile(String filename, String id) {
        queryToJudgement = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                Query textQuery = parser.parse(parser.escape(line));
                String[] qParts = textQuery.toString().split(" ");

                String url = reader.readLine();
                String docContent = _searcher.search(url, "clicked_url");
                HashSet<String> tokSet = new HashSet<>(tokenizeString(docContent));

                int tokMatched = 0;
                for (String part : qParts) {
                    if (tokSet.contains(part)) {
                        tokMatched++;
                    }
                }
                if (tokMatched == 0) {
                    continue;
                }

                HashSet<String> temp;
                if (queryToJudgement.containsKey(line)) {
                    temp = queryToJudgement.get(line);
                } else {
                    temp = new HashSet<>();
                }
                temp.add(url);
                queryToJudgement.put(line, temp);
            }
            reader.close();
        } catch (Exception e) {
            System.err.format("[Error]Failed to open file %s!", filename);
        }
        WriteToFile("./data/updated_top_1000_user_profiles/" + id + ".txt");
    }

    private List<String> tokenizeString(String string) {
        List<String> result = new ArrayList<>();
        try {
            TokenStream stream = analyzer.tokenStream(null, new StringReader(string));
            stream.reset();
            while (stream.incrementToken()) {
                result.add(stream.getAttribute(CharTermAttribute.class
                ).toString());
            }
            stream.end();
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private void WriteToFile(String filename) {
        try {
            FileWriter fw = new FileWriter(filename);
            for (Map.Entry<String, HashSet<String>> entry : queryToJudgement.entrySet()) {
                fw.write(entry.getKey() + "\n");
                for (String str : entry.getValue()) {
                    fw.write(str + "\n");
                }
                fw.write("\n");
            }
            fw.close();
        } catch (IOException ex) {
            Logger.getLogger(UpdatedProfileBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void LoadDirectory(String folder) throws Throwable {
        int numberOfDocumentsLoaded = 0;
        File dir = new File(folder);
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                numberOfDocumentsLoaded++;
                String filename = f.getName();
                LoadFile(f.getAbsolutePath(), filename.substring(0, filename.lastIndexOf(".")));
            } else if (f.isDirectory()) {
                LoadDirectory(f.getAbsolutePath());
            }
        }
        System.out.println("Loading " + numberOfDocumentsLoaded + " documents from " + folder);
    }

    public static void main(String[] args) throws Throwable {
        UpdatedProfileBuilder upBuilder = new UpdatedProfileBuilder();
        upBuilder.LoadDirectory("./data/top_1000_user_profiles/");
    }

}
