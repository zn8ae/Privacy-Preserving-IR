package edu.virginia.cs.eval;

import edu.virginia.cs.user.ReferenceModel;
import java.io.IOException;
import java.util.ArrayList;
import edu.virginia.cs.index.Searcher;
import edu.virginia.cs.utility.SpecialAnalyzer;
import edu.virginia.cs.user.UserProfile;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import edu.virginia.cs.model.GenerateQuery;

public class SemanticEvaluation {

    private String _judgeFile = "./data/user_profiles/test/";
    private final String _userProfileDirTrain = "./data/user_profiles/train/";
    private final String _indexPath = "lucene-AOL-index";
    final static String _userProfileDir = "./data/user_profiles/";
    private Searcher _searcher = null;
    private HashMap<String, ArrayList<String>> mappingQueryToURL;
    private ArrayList<String> allQueries;
    private ArrayList<String> allQueryProbab;
    private GenerateQuery gQuery;
    private UserProfile uProfile; // client side user profile
    private ReferenceModel refUserModel;
    private HashMap<String, Float> intialUserProfile;
    private SpecialAnalyzer analyzer;

//    private SemanticCoverQuery semanticQuery;
    //private double totalMAP = 0.0;
    private double totalQueries = 0.0;
    private double allNumQueries = 0.0;
    private double allMeanAvgPrec = 0.0;

    private double alldivergence1 = 0.0;
    private double alldivergence2 = 0.0;
    private double alldivergence3 = 0.0;

    HashMap<String, Float> tempRefModel;
    HashMap<String, Integer> tempRefToken;

    //knowledge
    HashMap<String, Integer> originalQueryList;
    HashMap<String, Integer> coverQueryList;
    HashMap<String, Integer> originalQueryMap;

    HashMap<String, Double> originalQueryProb;
    HashMap<String, Double> coverQueryProb;
    HashMap<String, Double> probOfXGivenY;

    //data structure for mutual information measurement
    public HashMap<String, Integer> dictionaryWords = new HashMap<>();
    int totalDoc = 2225;
    int totalTerm = 23225;
    int[][] termDocMatrix = new int[totalTerm][totalDoc];
    QueryParser parser;

    int totalNotinDic = 0;

    public void initDSMinfo() {
        analyzer = new SpecialAnalyzer();
        parser = new QueryParser(Version.LUCENE_46, "", analyzer);
        BufferedReader br = null;
        try {
            dictionaryWords = new HashMap<>();
            String line = null;
            br = new BufferedReader(new FileReader("data/mutual_info_data/bbcDictionary.txt"));
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\t");
                //System.out.println("token length: "+tokens.length);
                if (tokens.length == 2)//valid line 
                {
                    Integer serial = Integer.parseInt(tokens[0]);
                    String key = tokens[1];
                    dictionaryWords.put(key, serial);
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(SemanticEvaluation.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                Logger.getLogger(SemanticEvaluation.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        /*System.out.println("Printing...");
         for(String str:dictionaryWords.keySet()){
         System.out.println("Entry: "+str+" "+dictionaryWords.get(str));
         }*/
        //System.out.println("Dictionary Size: "+dictionaryWords.size());
        //process doc term matrix
        BufferedReader brDT = null;
        try {
            ////////dictionaryWords = new HashMap<>();
            String line = null;
            brDT = new BufferedReader(new FileReader("data/mutual_info_data/bbcTermDocMatrix.txt"));
            //System.out.println("Added file path: "+filePath);
            int countRow = 0;
            while ((line = brDT.readLine()) != null) {
                String[] tokens = line.split(" ");
                //System.out.println("token length: "+tokens.length);
                for (int k = 0; k < tokens.length; k++) {
                    termDocMatrix[countRow][k] = Integer.parseInt(tokens[k]);
                }

                countRow++;
            }
        } catch (Exception ex) {
            Logger.getLogger(SemanticEvaluation.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                brDT.close();
            } catch (IOException ex) {
                Logger.getLogger(SemanticEvaluation.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        /*for(int i=0;i<termDocMatrix.length;i++){
         for(int j=0;j<termDocMatrix[0].length;j++){
         System.out.print(termDocMatrix[i][j]+" ");
         }
         System.out.println();
         }*/
    }

    private double getProbFromTDMatrix(String query) {
        double probQuery = 0;
        try {
            // System.out.println("Originla one:"+query);
            if (query.isEmpty()) {
                return 0;
            }
            Query textQuery = parser.parse(QueryParser.escape(query));
            // System.out.println("AFter QueryParser:"+textQuery.toString());
            String tokens[] = textQuery.toString().trim().split(" ");
            int docFreqCount = 0;
            for (int docIndex = 0; docIndex < termDocMatrix[0].length; docIndex++) {
                boolean foundFlag = true;
                for (int i = 0; i < tokens.length; i++) {
                    //get number
                    Integer termIndex = dictionaryWords.get(tokens[i]);
                    if (termIndex != null) {

                        //  System.out.println("Luck Found index:"+termIndex);
                        boolean checkbound = true;
                        if (termIndex > termDocMatrix.length && docIndex > termDocMatrix[0].length) {
                            checkbound = false;
                            System.out.println("Index Out !!");
                        }

                        if (checkbound && termDocMatrix[termIndex][docIndex] < 1) {//if =0
                            foundFlag = false;
                            break;
                        }
                        // System.out.println("Luck Found");
                    } else {

                        //System.out.println("Dic Size:="+dictionaryWords.size());
                        foundFlag = false;
                        totalNotinDic++;
                        break;
                    }
                }
                if (foundFlag) {
                    docFreqCount++;
                }
            }
            probQuery = (docFreqCount * 1.0) / totalDoc;
        } catch (ParseException ex) {
            Logger.getLogger(SemanticEvaluation.class.getName()).log(Level.SEVERE, null, ex);
        }
        return probQuery;
    }

    public double calculatePEL(ArrayList<String> origQuery, ArrayList<String> coverQuery) {
        //calculate probability
        // getProbFromTDMatrix
        HashMap<String, Double> Px = new HashMap<>();
        HashMap<String, Double> Py = new HashMap<>();
        HashMap<String, Double> Pxy = new HashMap<>();
        for (int ix = 0; ix < origQuery.size(); ix++) {
            String qr = origQuery.get(ix);
            double prob = getProbFromTDMatrix(qr);
            Px.put(qr, prob);
            //System.out.println("X Prob:"+qr+" "+prob);

        }
        for (int iy = 0; iy < coverQuery.size(); iy++) {
            String qr = coverQuery.get(iy);
            double prob = getProbFromTDMatrix(qr);
            Py.put(qr, prob);
            //System.out.println("Y Prob:"+qr+" "+prob);

        }
        //create P(x,y)
        for (int ix = 0; ix < origQuery.size(); ix++) {
            for (int iy = 0; iy < coverQuery.size(); iy++) {
                String combineQuery = origQuery.get(ix) + " " + coverQuery.get(iy);
                double prob = getProbFromTDMatrix(combineQuery);
                Pxy.put(combineQuery, prob);
                //System.out.println("XY Prob:"+combineQuery+" "+prob);
            }
        }
        //mutual info
        double muInfo = 0;
        int toatlMatch = 0;
        int totalUNMatch = 0;
        for (int ix = 0; ix < origQuery.size(); ix++) {
            for (int iy = 0; iy < coverQuery.size(); iy++) {
                String combineQuery = origQuery.get(ix) + " " + coverQuery.get(iy);
                double pxy = Pxy.get(combineQuery);
                double px = Px.get(origQuery.get(ix));
                double py = Py.get(coverQuery.get(iy));
                if (pxy > 0 && px > 0 && py > 0) {
                    //System.out.println("Got one match");
                    toatlMatch++;
                    double partER = pxy * ((Math.log10(pxy / (px * py))) / Math.log10(2));
                    muInfo += partER;
                    //System.out.println("partER:="+partER);
                    //System.out.println("mutualER:="+muInfo);
                } else {
                    totalUNMatch++;
                    //System.out.println("px,py,pxy all are not non-zero. Not found");
                }
            }
        }

        return muInfo;
    }

    //Please implement P@K, MRR and NDCG accordingly
    public static void main(String[] args) throws Throwable {

    }

}
