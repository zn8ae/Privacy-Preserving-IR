/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.virginia.cs.user;

import edu.virginia.cs.index.Searcher;
import edu.virginia.cs.utility.SpecialAnalyzer;
import edu.virginia.cs.similarities.OkapiBM25;
import edu.virginia.cs.utility.StringTokenizer;
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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

/**
 *
 * @author Masud
 */
public class UserProfile {

    private final static String _indexPath = "lucene-AOL-index";
    private HashMap<String, Float> uProfileProb;
    private HashMap<String, Integer> uProfile;
    private HashMap<String, Integer> IDFRecord;
    private SpecialAnalyzer analyzer;
    private String userID;
    public int totalTokens;
    public int initTotalTokensCoount;
    private int totalQueryCorpus;
    private int totalTokensCorpus;

    private QueryParser parser;
    private double avgQueryLength;
    private double totalQueryLength;
    private double totalQuery;
    private int totalDocument;
    private Searcher searcher;

    public HashMap<String, Float> referenceModel;

    public UserProfile() {
        searcher = new Searcher(_indexPath);
        searcher.setSimilarity(new OkapiBM25());
        analyzer = new SpecialAnalyzer();
        uProfile = new HashMap<>();
        uProfileProb = new HashMap<>();
        IDFRecord = new HashMap<>();

        totalTokens = 0;
        initTotalTokensCoount = 0;

        avgQueryLength = 0;
        totalQuery = 0;
        totalQueryLength = 0;
        totalDocument = 0;
        totalQueryCorpus = 0;
        totalTokensCorpus = 0;

        parser = new QueryParser(Version.LUCENE_46, "", analyzer);
        BooleanQuery.setMaxClauseCount(2048);
    }

    public void setReferenceModel(HashMap<String, Float> rModel) {
        referenceModel = new HashMap<>();
        for (String str : rModel.keySet()) {
            referenceModel.put(str, rModel.get(str));
            uProfile.put(str, 0);
        }
    }

    public HashMap<String, Integer> getUserProfile() {
        HashMap<String, Integer> retVal = new HashMap<>();
        for (String str : uProfile.keySet()) {
            retVal.put(str, uProfile.get(str));
        }
        return retVal;
    }

    public void createUserProfile(String userProfilePath, String param)
            throws IOException {
        userID = param;
        File file = new File(userProfilePath + userID + ".txt");
        String line;
        BufferedReader br = new BufferedReader(new FileReader(file));
        int i = 0;
        while ((line = br.readLine()) != null) {
            i++;
            if (i % 2 == 0) {
                // for clicked document content
                line = searcher.search(line, "clicked_url");
                List<String> tokens = StringTokenizer.TokenizeString(line);
                // getting top 10 tokens -1 for all documents summary
                HashMap<String, Integer> retVal = selectTopKtokens(tokens, 10);
                for (Map.Entry<String, Integer> entry : retVal.entrySet()) {
                    totalTokens += entry.getValue();
                    Integer n = uProfile.get(entry.getKey());
                    n = (n == null) ? entry.getValue() : (n + entry.getValue());
                    uProfile.put(entry.getKey(), n);
                    System.out.println("Testing train");
                }
                continue;
            }
            try {
                Query textQuery = parser.parse(parser.escape(line));
                //System.out.println("Text Q: "+textQuery.toString());
                String[] qParts = textQuery.toString().split(" ");
                totalQuery++;
                totalQueryLength = totalQueryLength + qParts.length;
                for (int ind = 0; ind < qParts.length; ind++) {
                    if (qParts[ind].isEmpty()) {
                        continue;
                    }
                    totalTokens++;//for every token
                    Integer n = uProfile.get(qParts[ind]);
                    n = (n == null || n == 0) ? 1 : ++n;

                    uProfile.put(qParts[ind], n);
                    System.out.println(" Try Testing train");
                }
            } catch (ParseException exception) {
                exception.printStackTrace();
            }
        }
        br.close();
        initTotalTokensCoount = totalTokens;
    }

    public void updateUserProfile(String newQuery)
            throws IOException {
        try {
            Query textQuery = parser.parse(parser.escape(newQuery));
            String[] qParts = textQuery.toString().split(" ");
            totalQuery++;
            totalQueryLength = totalQueryLength + qParts.length;
            for (int ind = 0; ind < qParts.length; ind++) {
                if (qParts[ind].isEmpty()) {
                    continue;
                }
                totalTokens++;//for every token
                Integer n = uProfile.get(qParts[ind]);
                n = (n == null) ? 1 : ++n;
                uProfile.put(qParts[ind], n);
            }
        } catch (ParseException exception) {
            exception.printStackTrace();
        }
    }

    public void updateUserProfileUsingClickedDocument(String content)
            throws IOException {
        // updating user profile with the clicked document content
        //updating with document only linear interpolation with lamda =0.1
        List<String> tokens = StringTokenizer.TokenizeString(content);
        HashMap<String, Integer> retVal = selectTopKtokens(tokens, 10); // getting top 10 tokens
        for (Map.Entry<String, Integer> entry : retVal.entrySet()) {
            totalTokens += entry.getValue();
            Integer n = uProfile.get(entry.getKey());
            n = (n == null) ? entry.getValue() : (n + entry.getValue());
            uProfile.put(entry.getKey(), n);
        }
    }

    public double getAvgQueryLength() {
        if (totalQuery < 1) {
            //return 0;//zero create problem
            return 3;
        }
        return totalQueryLength / totalQuery;
    }

    /*
     @return Returns the KL divergence, K(old || newProfile)
     */
    public float calculateKLDivergence(HashMap<String, Integer> old_uProfile, int oldProfileTokenCount) {

        float klDiv = 0;
        float lambda = 0.1f;
        for (String name : old_uProfile.keySet()) {

            Integer value = old_uProfile.get(name);
            if (value == null) {
                value = 0;
            }
            //Float tokenProb = (value * 1.0f) / oldProfileTokenCount;//smooting//use old profile token count
            Float tokenProb = (value * 1.0f) / oldProfileTokenCount;
            Float refProb = referenceModel.get(name);
            if (refProb == null) {
                refProb = 0.0f;
            }
            Float smoothedTokenProb = (1 - lambda) * tokenProb + lambda * refProb;
            Float p1 = smoothedTokenProb;

            Integer value2 = uProfile.get(name);
            if (value2 == null) {
                value2 = 0;
            }
            Float tokenProb2 = (value2 * 1.0f) / totalTokens;//smooting
            Float refProb2 = refProb;//remain same for a single key name
            if (refProb2 == null) {
                refProb2 = 0.0f;
            }
            Float smoothedTokenProb2 = (1 - lambda) * tokenProb2 + lambda * refProb2;
            Float p2 = smoothedTokenProb2; //updated user profile
            if (p1 == null || p2 == null) {
                break;
            }
            if (p1 == 0) {
                continue;
            }
            if (p2 == 0) {
                continue;
            }
            klDiv = (float) (klDiv + p1 * Math.log(p1 / p2));
        }
        return klDiv;
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
