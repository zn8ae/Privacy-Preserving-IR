/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.virginia.cs.user;

import edu.virginia.cs.index.Searcher;
import edu.virginia.cs.utility.SpecialAnalyzer;
import edu.virginia.cs.similarities.OkapiBM25;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

/**
 *
 * @author wasi
 */
public class ReferenceModel {

    private final static String _indexPath = "lucene-AOL-index";
    private final String _topicTokenFilePath = "dictionaryWithFrequency.txt";
    private final HashMap<String, Float> refModel;
    private final HashMap<String, Integer> queryTokens;
    private final HashMap<String, Integer> IDFRecord;
    private final SpecialAnalyzer analyzer;
    private int totalTokensCorpus;
    private final QueryParser parser;
    private int totalDocument;
    private final Searcher searcher;

    public ReferenceModel() {
        analyzer = new SpecialAnalyzer();
        refModel = new HashMap<>();
        queryTokens = new HashMap<>();
        IDFRecord = new HashMap<>();
        totalDocument = 0;
        totalTokensCorpus = 0;
        parser = new QueryParser(Version.LUCENE_46, "", analyzer);
        BooleanQuery.setMaxClauseCount(2048);

        searcher = new Searcher(_indexPath);
        searcher.setSimilarity(new OkapiBM25());
    }

    //create reference model for smoothing and user profile skeleton
    //consider only query building user profile
    public void addInitUserProfile(String filePath) throws IOException {
        if (filePath != null) {
            String line = null;
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            while ((line = br.readLine()) != null) {
                try {
                    Query textQuery = parser.parse(parser.escape(line));
                    String[] qParts = textQuery.toString().split(" ");
                    for (String qPart : qParts) {
                        if (qPart.isEmpty()) {
                            continue;
                        }
                        totalTokensCorpus++;//for every token
                        Integer n = queryTokens.get(qPart);
                        n = (n == null) ? 1 : ++n;
                        queryTokens.put(qPart, n);
                    }
                } catch (ParseException exception) {
                    exception.printStackTrace();
                }

                if ((line = br.readLine()) != null) {
                    line = searcher.search(line, "clicked_url");
                    List<String> tokens = tokenizeString(line);
                    // getting top 10 tokens -1 for all documents summary 
                    HashMap<String, Integer> retVal = selectTopKtokens(tokens, 10);
                    for (Map.Entry<String, Integer> entry : retVal.entrySet()) {
                        totalTokensCorpus += entry.getValue();
                        try {
                            Query textQuery = parser.parse(parser.escape(entry.getKey()));
                            String smoothedKey = textQuery.toString();
                            Integer n = queryTokens.get(smoothedKey);
                            n = (n == null) ? entry.getValue() : (n + entry.getValue());
                            queryTokens.put(smoothedKey, n);
                        } catch (ParseException ex) {
                            Logger.getLogger(UserProfile.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
            br.close();
        } else {
            System.out.println("No user found with the file name: " + filePath);
        }
    }

    private HashMap<String, Integer> selectTopKtokens(List<String> tokenList, int k) {
        HashMap<String, Integer> retValue = new HashMap<>(); // for returning top k tokens with term frequency
        HashMap<String, Integer> tempMap = new HashMap<>(); // stores term frequency of the tokens
        HashMap<String, Float> unsortedMap = new HashMap<>(); // stores tf-idf weight of the tokens
        for (String token : tokenList) {
            Integer n = tempMap.get(token);
            n = (n == null) ? 1 : ++n;
            tempMap.put(token, n);
        }
        Set<String> tokenSet = new HashSet<>(tokenList);
        for (String token : tokenSet) {
            Integer n = IDFRecord.get(token);
            n = (n == null) ? 1 : ++n;
            IDFRecord.put(token, n);
        }
        totalDocument++; // total number of click documents analyzed
        for (Map.Entry<String, Integer> entry : tempMap.entrySet()) {
            double tfIdfWeight = entry.getValue() * Math.log((totalDocument / IDFRecord.get(entry.getKey())));
            unsortedMap.put(entry.getKey(), (float) tfIdfWeight);
        }
        HashMap<String, Float> temp = sortByComparator(unsortedMap, false, 10);
        for (Map.Entry<String, Float> entry : temp.entrySet()) {
            retValue.put(entry.getKey(), tempMap.get(entry.getKey()));
        }
        return retValue;
    }

    private List<String> tokenizeString(String string) {
        List<String> result = new ArrayList<>();
        try {
            TokenStream stream = analyzer.tokenStream(null, new StringReader(string));
            stream.reset();
            while (stream.incrementToken()) {
                result.add(stream.getAttribute(CharTermAttribute.class).toString());
            }
            stream.end();
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public void initUserProfile(String userProfilePath)
            throws IOException {
        File folder_test = new File(userProfilePath + "test/");
        File[] listOfFiles_test = folder_test.listFiles();
        int count = 1;
        for (File file_test : listOfFiles_test) {
            if (file_test.isFile() && file_test.getName().endsWith(".txt")) {
                addInitUserProfile(userProfilePath + "train/" + file_test.getName());//remove training data
                addInitUserProfile(userProfilePath + "test/" + file_test.getName());
                System.out.println(count);
                count++;
            }
        }
    }

    /**
     *
     * @param filePath
     * @throws IOException
     */
    public void addTopicWords(String filePath) throws IOException {
        if (filePath != null) {
            String line = null;
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            while ((line = br.readLine()) != null) {
                try {
                    totalTokensCorpus++; //for every token
                    String[] qParts = line.split(" ");
                    if (qParts.length == 2) {
                        Query textQuery = parser.parse(parser.escape(qParts[0]));
                        qParts[0] = textQuery.toString();
                        if (qParts[0].isEmpty()) {
                            continue;
                        }
                        Integer n = queryTokens.get(qParts[0]);
                        if (n == null) {
                            n = Integer.parseInt(qParts[1]); //frequency
                        } else {
                            n = n + Integer.parseInt(qParts[1]);
                        }
                        queryTokens.put(qParts[0], n);
                    } else if (qParts.length == 3) {//bigram
                        Query textWOrd = parser.parse(parser.escape(qParts[0]));
                        qParts[0] = textWOrd.toString();
                        Query textWOrd2 = parser.parse(parser.escape(qParts[1]));
                        qParts[1] = textWOrd2.toString();

                        String biToken = qParts[0] + " " + qParts[1];
                        Integer n = queryTokens.get(biToken);
                        if (n == null) {
                            n = Integer.parseInt(qParts[2]);//frequency
                        } else {
                            n = n + Integer.parseInt(qParts[2]);
                        }
                        queryTokens.put(biToken, n);
                    } else {
                        System.out.println("Dictionary entry is not in right format in: " + line);
                    }
                } catch (ParseException exception) {
                    exception.printStackTrace();
                }
            }
            br.close();
        } else {
            System.out.println("No user found with the file name: " + filePath);
        }
    }

    public void createReferenceModel(String userProfilePath)
            throws IOException {
        //add topic model data to handle fake queries
        System.out.println("Adding topic words to reference model");
        addTopicWords(_topicTokenFilePath);
        System.out.println(queryTokens.size());
        System.out.println("Considering user profile");
        initUserProfile(userProfilePath); //setup user profile
        //build reference model
        for (String name : queryTokens.keySet()) {
            Integer value = queryTokens.get(name);
            Float tokenProb = (value * 1.0f) / totalTokensCorpus;
            refModel.put(name, tokenProb);
        }
    }

    public HashMap<String, Float> getReferenceModel() {
        HashMap<String, Float> retVal = new HashMap<>();
        for (String str : refModel.keySet()) {
            retVal.put(str, refModel.get(str));
        }
        return retVal;
    }

    public HashMap<String, Integer> getReferenceToken() {
        HashMap<String, Integer> retToken = new HashMap<>();
        for (String str : queryTokens.keySet()) {
            retToken.put(str, 0);
        }
        return retToken;
    }

    private HashMap<String, Float> sortByComparator(Map<String, Float> unsortMap, final boolean order, int k) {
        List<Map.Entry<String, Float>> list = new LinkedList<>(unsortMap.entrySet());
        // Sorting the list based on values
        Collections.sort(list, (Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) -> {
            if (order) {
                return o1.getValue().compareTo(o2.getValue());
            } else {
                return o2.getValue().compareTo(o1.getValue());

            }
        });
        // Maintaining insertion order with the help of LinkedList
        HashMap<String, Float> sortedMap = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<String, Float> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
            i++;
            if (i == k) {
                break;
            }
        }
        return sortedMap;
    }
}
