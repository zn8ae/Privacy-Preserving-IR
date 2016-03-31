/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.virginia.cs.model;

import edu.virginia.cs.utility.SpecialAnalyzer;
import edu.virginia.cs.utility.FileOperations;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

/**
 *
 * @author Wasi
 */
public class BuildTopicModel {

    private HashMap<String, Integer> dictionary;
    private HashMap<String, Integer> dictionaryWithTF;
    private int numberOfWordsInDictionary = 0;
    private FileOperations fiop;
    private SpecialAnalyzer analyzer;
    private QueryParser parser;

    public BuildTopicModel() throws IOException {
        fiop = new FileOperations();
        dictionary = new HashMap<>();
        dictionaryWithTF = new HashMap<>();
        analyzer = new SpecialAnalyzer();
        parser = new QueryParser(Version.LUCENE_46, "", analyzer);
        BooleanQuery.setMaxClauseCount(2048);
    }

    private void analyzeDocument(String document) throws IOException {
        HashMap<String, Integer> tempRecord = new HashMap<>();
        Query textQuery;
        String previousToken = "";
        try {
            textQuery = parser.parse(parser.escape(document));
            String[] tokens = textQuery.toString().split(" ");
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    if (!dictionary.containsKey(token)) {
                        dictionary.put(token, numberOfWordsInDictionary);
                        numberOfWordsInDictionary++;
                    }
                    if (tempRecord.containsKey(token)) {
                        tempRecord.put(token, tempRecord.get(token) + 1);
                    } else {
                        tempRecord.put(token, 1);
                    }
                    // generating bigrams
                    if (!previousToken.isEmpty()) {
                        String bigram = previousToken + " " + token;
                        if (!dictionary.containsKey(bigram)) {
                            dictionary.put(bigram, numberOfWordsInDictionary);
                            numberOfWordsInDictionary++;
                        }
                        if (tempRecord.containsKey(bigram)) {
                            tempRecord.put(bigram, tempRecord.get(bigram) + 1);
                        } else {
                            tempRecord.put(bigram, 1);
                        }
                    }

                    previousToken = token;
                }
            }
            String line = String.valueOf(tempRecord.size());
            for (Map.Entry<String, Integer> entry : tempRecord.entrySet()) {
                line = line + " " + dictionary.get(entry.getKey()) + ":" + entry.getValue();
                if (dictionaryWithTF.containsKey(entry.getKey())) {
                    int tempFreq = entry.getValue() + dictionaryWithTF.get(entry.getKey());
                    dictionaryWithTF.put(entry.getKey(), tempFreq);
                } else {
                    dictionaryWithTF.put(entry.getKey(), entry.getValue());
                }
            }
            fiop.appnedToFile("documentRecord.dat", line);
        } catch (ParseException ex) {
            Logger.getLogger(BuildTopicModel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void LoadDirectory(String folder) throws Throwable {
        int numberOfDocumentsLoaded = 0;
        File dir = new File(folder);
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                numberOfDocumentsLoaded++;
                analyzeDocument(fiop.LoadFile(f.getAbsolutePath()));
            } else if (f.isDirectory()) {
                LoadDirectory(f.getAbsolutePath());
            }
        }
        System.out.println("Loading " + numberOfDocumentsLoaded + " documents from " + folder);
        writeDictionaryToFile("dictionary.txt", 1);
        writeDictionaryToFile("dictionaryWithFrequency.txt", 2);
    }

    private void sortByComparator(HashMap<String, Integer> unsortedMap, boolean order, String filename, int choice) throws Throwable {
        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortedMap.entrySet());
        Collections.sort(list, (Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) -> {
            if (order) {
                return o1.getValue().compareTo(o2.getValue());
            } else {
                return o2.getValue().compareTo(o1.getValue());

            }
        });
        fiop.appnedToFile(filename, list, choice);
    }

    private void writeDictionaryToFile(String filename, int choice) throws Throwable {
        if (choice == 1) {
            sortByComparator(dictionary, true, filename, choice);
        } else {
            sortByComparator(dictionaryWithTF, true, filename, choice);
        }
    }

    public static void main(String[] args) throws Throwable {
        // TODO code application logic here
        BuildTopicModel tmodel = new BuildTopicModel();
        tmodel.LoadDirectory("./data/bbc/");
    }
}
