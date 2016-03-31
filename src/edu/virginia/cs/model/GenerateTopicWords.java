/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.virginia.cs.model;

import edu.virginia.cs.utility.FileOperations;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Wasi
 */
public class GenerateTopicWords {

    public GenerateTopicWords() {
    }

    private void storeData(String betaFile, String dictFile) throws Throwable {
        String filepath = "topic_repo/topic ";
        FileOperations fiop = new FileOperations();
        String content = fiop.LoadFile(dictFile);
        String vocab[] = content.split("[\\r\\n]+");
        content = fiop.LoadFile(betaFile);
        String lines[] = content.split("[\\r\\n]+");
        HashMap<String, Double> unsortedMap = new HashMap<>();
        int x = 0;
        for (String line : lines) {
            String values[] = line.split(" ");
            Double total = 0.0;
            for (int i = 0; i < values.length; i++) {
                Double d = Math.pow(Math.E, Double.valueOf(values[i]));
                unsortedMap.put(vocab[i], d);
                total = total + d;
            }
            System.out.println(total);
            sortByComparator(unsortedMap, false, filepath + String.valueOf(x) + ".txt");
            x++;
        }
    }

    private void sortByComparator(HashMap<String, Double> unsortedMap, boolean order, String filename) throws Throwable {
        List<Map.Entry<String, Double>> list = new LinkedList<>(unsortedMap.entrySet());
        Collections.sort(list, (Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) -> {
            if (order) {
                return o1.getValue().compareTo(o2.getValue());
            } else {
                return o2.getValue().compareTo(o1.getValue());

            }
        });
        FileOperations fiop = new FileOperations();
        fiop.storeInFile(filename, list);
    }

    public static void main(String[] args) throws Throwable {
        // TODO code application logic here
        GenerateTopicWords gTopicWords = new GenerateTopicWords();
        gTopicWords.storeData("topic_model/final.beta", "dictionary.txt");
    }

}
