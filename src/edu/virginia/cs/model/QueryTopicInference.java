/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.virginia.cs.model;

import edu.virginia.cs.utility.DocumentAnalyzer;
import edu.virginia.cs.utility.FileOperations;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Wasi
 */
public class QueryTopicInference {

    private HashMap<String, Integer> dictionary;
    private FileOperations fiop;
    private DocumentAnalyzer analyzer;

    public QueryTopicInference() throws IOException {
        fiop = new FileOperations();
        analyzer = new DocumentAnalyzer();
        dictionary = new HashMap<>();
    }

    private void LoadDictionary(String filename) {
        String lines[] = fiop.LoadFile(filename).split("[\\r\\n]+");
        int wordCount = 0;
        for (String line : lines) {
            dictionary.put(line, wordCount);
            wordCount++;
        }
    }

    private void runTopicInference() throws IOException {
        String command = "lda-win64 inf settings.txt topic_model/final query.dat result";
 //       String command = "lda-linux64 inf settings.txt topic_model/final query.dat result"; // for linux
        Process proc = Runtime.getRuntime().exec(command);
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

        // read the output from the command
//        System.out.println("\nHere is the standard output of the command:\n");
        String s = null;
        while ((s = stdInput.readLine()) != null) {
//            System.out.println(s);
        }

        // read any errors from the attempted command
        s = stdError.readLine();
        if (s != null) {
            System.out.println("\nHere is the standard error of the command (if any):\n");
        }
        while (s != null) {
            System.out.println(s);
            s = stdError.readLine();
        }
    }

    private void ProcessQueryLog(ArrayList<String> allQueries) throws IOException {
        File file = new File("query.dat");
        BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsolutePath()));
        for (String query : allQueries) {
            String processedResult = analyzer.ProcessDocument(query);
            bw.write(processedResult + "\n");
        }
        bw.close();
    }

    public void ProcessQuery(String query) throws IOException {
        File file = new File("query.dat");
        BufferedWriter bw = new BufferedWriter(new FileWriter(file.getAbsolutePath()));
        String processedResult = analyzer.ProcessDocument(query);
        bw.write(processedResult + "\n");
        bw.close();
        runTopicInference();
    }

    public void initializeGeneration(ArrayList<String> allQueries) throws IOException {
        LoadDictionary("dictionary.txt");
        analyzer.SetDictionary(dictionary);
        ProcessQueryLog(allQueries);
        runTopicInference();
    }

    public void initializeGenerationForRunner() throws IOException {
        LoadDictionary("dictionary.txt");
        analyzer.SetDictionary(dictionary);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
    }
}
