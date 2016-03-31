/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.virginia.cs.utility;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

/**
 *
 * @author Wasi
 */
public class DocumentAnalyzer {

    private HashMap<String, Integer> dictionary;
    private final SpecialAnalyzer analyzer;
    private final QueryParser parser;

    public DocumentAnalyzer() throws IOException {
        analyzer = new SpecialAnalyzer();
        parser = new QueryParser(Version.LUCENE_46, "", analyzer);
    }

    public void SetDictionary(HashMap<String, Integer> param) {
        dictionary = param;
    }

    /**
     * The main method that creates and starts threads.
     *
     * @param document
     * @return
     */
    public String ProcessDocument(String document) throws IOException {
        HashMap<String, Integer> tempRecord = new HashMap<>();
        Query textQuery;
        String line = null;
        try {
            textQuery = parser.parse(QueryParser.escape(document));
            String[] tokens = textQuery.toString().split(" ");
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    if (dictionary.containsKey(token)) {
                        if (tempRecord.containsKey(token)) {
                            tempRecord.put(token, tempRecord.get(token) + 1);
                        } else {
                            tempRecord.put(token, 1);
                        }
                    }
                }
            }
            line = String.valueOf(tempRecord.size());
            for (Map.Entry<String, Integer> entry : tempRecord.entrySet()) {
                line = line + " " + dictionary.get(entry.getKey()) + ":" + entry.getValue();
            }
        } catch (ParseException ex) {
            Logger.getLogger(DocumentAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return line;
    }
}
