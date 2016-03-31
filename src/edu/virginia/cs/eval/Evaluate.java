package edu.virginia.cs.eval;

import edu.virginia.cs.user.ReferenceModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import edu.virginia.cs.index.ResultDoc;
import edu.virginia.cs.index.Searcher;
import edu.virginia.cs.utility.SpecialAnalyzer;
import edu.virginia.cs.user.UserProfile;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import edu.virginia.cs.model.GenerateQuery;
import edu.virginia.cs.model.QueryTopicInference;
import edu.virginia.cs.utility.FileOperations;
import edu.virginia.cs.utility.StringTokenizer;

public class Evaluate {

    private final String _judgeFile = "./data/second_top_150_user_profiles/";
    private final String _indexPath = "lucene-AOL-index";
    final static String _userProfileDir = "./data/user_profiles/";
    private Searcher _searcher = null;
    private HashMap<String, ArrayList<String>> mappingQueryToURL;
    private ArrayList<String> allQueries;
    private ArrayList<String> allQueryProbab;
    private GenerateQuery gQuery;
    private UserProfile uProfile; // client side user profile
    private ReferenceModel refUserModel;
//    private SpecialAnalyzer analyzer;
    private double totalMAP = 0.0;
    private double totalQueries = 0.0;

    HashMap<String, Float> tempRefModel;
    HashMap<String, Integer> tempRefToken;

    //for semantic evaluation
    private ArrayList<String> origQueryR;
    private ArrayList<String> coverQueryR;
    private SemanticEvaluation semEval;
//    private SemanticCoverQuery semanticQuery;
    private int countUsers = 0;
    private double totalKL = 0;
    private double totalMI = 0;

    private double entropyRange;
    private int numOfCoverQ;

    //writer
    FileWriter rout;

    private void generateJudgement(String userId) {
        mappingQueryToURL = new HashMap<>();
        allQueries = new ArrayList<>();
        String judgeFile = _judgeFile + userId + ".txt";
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(judgeFile));
            String query;
            while ((query = br.readLine()) != null) {
                String relDoc = br.readLine();
                ArrayList<String> tempList;
                if (mappingQueryToURL.containsKey(query)) {
                    tempList = mappingQueryToURL.get(query);
                } else {
                    tempList = new ArrayList<>();
                }
                tempList.add(relDoc);
                mappingQueryToURL.put(query, tempList);
                allQueries.add(query);
            }
        } catch (Exception ex) {
            Logger.getLogger(Evaluate.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void fakeQueryGeneration(String userId) throws IOException {
        uProfile = new UserProfile();
        uProfile.setReferenceModel(tempRefModel);
        uProfile.setReferenceToken(tempRefToken);

//        uProfile.createUserProfile(_userProfileDirTrain, userId); //removing for ignore training data
        QueryTopicInference qti = new QueryTopicInference();
        qti.initializeGeneration(allQueries);

        gQuery = new GenerateQuery();
        gQuery.setEntropy(entropyRange);
        gQuery.loadTopicWords("topic_repo");
        allQueryProbab = new ArrayList<>();
        FileOperations fiop = new FileOperations();
        allQueryProbab = fiop.LoadFile("result-gamma.dat", -1);
    }

    private void createRefModel() throws Throwable {
        refUserModel = new ReferenceModel();
        refUserModel.createReferenceModel(_userProfileDir); //create reference model
        tempRefModel = refUserModel.getReferenceModel();
//        tempRefToken = refUserModel.getReferenceToken();
        FileOperations fiop = new FileOperations();
        fiop.storeHashMapInFile("./data/reference_model.txt", tempRefModel);
    }

    private void loadRefModel() throws Throwable {
        tempRefModel = new HashMap<>();
        tempRefToken = new HashMap<>();
        FileOperations fiop = new FileOperations();
        ArrayList<String> lines = fiop.LoadFile("./data/reference_model.txt", -1);
        for (String line : lines) {
            line = line.trim();
            String[] words = line.split(" ");
            if (words.length == 2) {
                tempRefModel.put(words[0], Float.valueOf(words[1]));
                tempRefToken.put(words[0], 0);
            } else if (words.length == 3) {
                tempRefModel.put(words[0] + " " + words[1], Float.valueOf(words[2]));
                tempRefToken.put(words[0] + " " + words[1], 0);
            }
        }
//        System.out.println("Reference Model Size - " + tempRefModel.size());
    }

    private void startEval() throws Throwable {
        gQuery = new GenerateQuery();
        entropyRange = 0.2;
        numOfCoverQ = 2;

        //write original and cover query
        rout = new FileWriter("queryLogsTopic.txt");

        QueryParser parser = new QueryParser(Version.LUCENE_46, "", new SpecialAnalyzer());
        String method = "--ok";//specify the ranker you want to test
        _searcher = new Searcher(_indexPath);
        _searcher.activatePersonalization(true); // activate personalized search

        semEval = new SemanticEvaluation();
        semEval.initDSMinfo();
        loadRefModel();
        ArrayList<String> allUserId = getAllUserIds(_judgeFile);
        for (String userId : allUserId) {
            countUsers++;
            _searcher.initializeUserProfile();
            _searcher.getUserProfile().setReferenceModel(tempRefModel);
            _searcher.getUserProfile().setReferenceToken(tempRefToken);

            //semantic evaluation
            origQueryR = new ArrayList<>();
            coverQueryR = new ArrayList<>();

            generateJudgement(userId);
            fakeQueryGeneration(userId);
            int k = 10;
            double meanAvgPrec = 0.0, nDCG = 0.0;
            double numQueries = 0.0;
            int qCount = 0;
            int foundQ = 0;
            for (String query : mappingQueryToURL.keySet()) {
                ArrayList<String> relDocs = new ArrayList<>();
                ArrayList<String> tempDocs = mappingQueryToURL.get(query);
                Query textQuery = parser.parse(parser.escape(query));
                String[] qParts = textQuery.toString().split(" ");
                for (String relDoc : tempDocs) {
                    String docContent = _searcher.search(relDoc, "clicked_url");
                    HashSet<String> tokSet = new HashSet<>(StringTokenizer.TokenizeString(docContent));
                    int tokMatched = 0;
                    for (String part : qParts) {
                        if (tokSet.contains(part)) {
                            tokMatched++;
                        }
                    }
                    if (tokMatched > 0) {
                        relDocs.add(relDoc);
                    }
                }
                if (relDocs.size() > 0) {
                    String judgement = "";
                    for (String doc : relDocs) {
                        judgement += doc + " ";
                    }
                    //compute corresponding AP
                    double temp = AvgPrec(query, judgement, (int) qCount);
                    meanAvgPrec += temp;
                    ++numQueries;
                }
                qCount++;
            }
            totalMAP += meanAvgPrec;
            totalQueries += numQueries;
            double divergence3 = (double) _searcher.getUserProfile().calculateKLDivergence(uProfile.getUserProfile(), uProfile.totalTokens);//give total token to calcualte token prob on the fly
            totalKL += divergence3;
            double MAP = meanAvgPrec / numQueries;
            double mutualInfo = semEval.calculatePEL(origQueryR, coverQueryR);
            totalMI += mutualInfo;
            System.out.printf("%-8d\t%-8d\t%-8f\t%.8f\t%.8f\n", countUsers, Integer.parseInt(userId), MAP, divergence3, mutualInfo);
        }

        double avgKL = 0;
        double avgMI = 0;
        if (countUsers > 0) {
            avgKL = totalKL / countUsers;
            avgMI = totalMI / countUsers;
        }
        double finalMAP = totalMAP / totalQueries;
        System.out.println("\n************Final Results**************");
        System.out.println("\nTotal Users : " + countUsers);
        System.out.println("Total Quries : " + totalQueries);
        System.out.printf("\nFinal Map : %-8f\n", finalMAP);
        System.out.println("Average KL : " + avgKL);
        System.out.println("Average MI : " + avgMI);

        rout.close();
    }

    public static void main(String[] args) throws Throwable {
        Evaluate eval = new Evaluate();
        eval.startEval();
    }

    private double AvgPrec(String query, String docString, int index) throws Throwable {
        //add to original query
        //write query to file
        int nCoverQuery = numOfCoverQ;
        origQueryR.add(query);

//        fakeQueries = new ArrayList<>();
        ArrayList<String> fakeQueries = gQuery.generateNQueries(allQueryProbab.get(index), nCoverQuery, uProfile.getAvgQueryLength());
        //change here for cover query
//        ArrayList<String> fakeQueries = semanticQuery.getCoverQueries(query, 1, 10);//semantic query generation
        double avgp = 0.0;
//        fakeQueries.add(canQ);
        int randNum = (int) (Math.random() * fakeQueries.size());
//        rout.write(query + "\n");
        for (int k = 0; k < fakeQueries.size(); k++) {
//            rout.write(fakeQueries.get(k) + "\n");
            coverQueryR.add(fakeQueries.get(k));
            // sending fake queries to search engine
            ArrayList<ResultDoc> searchResults = _searcher.search(fakeQueries.get(k)).getDocs();
            // generating fake clicks for fake queries
            if (!searchResults.isEmpty()) {
                int rand = (int) (Math.random() * searchResults.size());
                ResultDoc rdoc = searchResults.get(rand);
                _searcher.updateUProfileUsingClickedDoc(rdoc.content());
            }
            // sending original user query to search engine
            if (k == randNum) {
//                ArrayList<ResultDoc> results = _searcher.search(query).getDocs();
                ArrayList<ResultDoc> results = _searcher.search(query).getDocs(); // for Plausible Deniable Search
                //add original query to cover query for our approach
//                coverQueryR.add(query); //for Semantic not include original in the cover
                coverQueryR.add(query);
                if (results.isEmpty()) {
                    continue;
                }

                // re-rank the results based on the user profile kept in client side
//                results = reRankResults(results);
                HashSet<String> relDocs = new HashSet<>(Arrays.asList(docString.split(" ")));
                int i = 1;
                double numRel = 0;
                for (ResultDoc rdoc : results) {
                    if (relDocs.contains(rdoc.geturl())) {
                        numRel++;
                        avgp = avgp + (numRel / i);
                        // update user profile of the client side
                        uProfile.updateUserProfileUsingClickedDocument(rdoc.content());
                        // update user profile of the server side
                        _searcher.updateUProfileUsingClickedDoc(rdoc.content());
                    }
                    ++i;
//                    System.out.println("Number of Relevant Docs found = " + numRel);
                }
                avgp = avgp / relDocs.size();
            }
        }
        // update user profile of the client side using submitted query
        uProfile.updateUserProfile(query);
        return avgp;
    }

    private ArrayList<ResultDoc> reRankResults(ArrayList<ResultDoc> relDocs) throws IOException {
        HashMap<String, Float> tempMap = new HashMap<>();
//        HashSet<String> uniqueDocTerms;
        HashMap<String, Integer> uniqueDocTerms;
        HashMap<String, Integer> uProf = uProfile.getUserProfile();
        for (int i = 0; i < relDocs.size(); i++) {
            List<String> tokens = StringTokenizer.TokenizeString(relDocs.get(i).content());
            uniqueDocTerms = new HashMap<>();
//            uniqueDocTerms = new HashSet<>();
            for (String tok : tokens) {
//                uniqueDocTerms.add(tok);
                if (uniqueDocTerms.containsKey(tok)) {
                    uniqueDocTerms.put(tok, uniqueDocTerms.get(tok) + 1);
                } else {
                    uniqueDocTerms.put(tok, 1);
                }
            }
            float tempVal = 0;
            float lambda = 0.1f;
            for (String str : uniqueDocTerms.keySet()) {
                Integer value = uProf.get(str);
                if (value == null) {
                    value = 0;
                }
//                Float tokenProb = (value * 1.0f) / uProfile.totalTokens;//smooting
                Float tokenProb = ((value * 1.0f) / uProfile.totalTokens) * uniqueDocTerms.get(str);//smooting
                Float refProb = uProfile.referenceModel.get(str);
                if (refProb == null) {
                    refProb = 0.0f;
                }
                Float smoothedTokenProb = (1 - lambda) * tokenProb + lambda * refProb;
                Float n = smoothedTokenProb;//probality calculation need here
                //Float n = uProf.get(str);
                if (n != null) {
                    tempVal = tempVal + n;
                }
            }
//            tempVal += 1 / (i + 1); // impact of rank in re-ranking
            tempMap.put(String.valueOf(i), tempVal);
//            System.out.println((i + 1) + " - " + tempVal);
        }
        Map<String, Float> tempResultedMap = sortByComparator(tempMap, false);
        tempMap = new HashMap<>();
        int i = 0;
        for (Map.Entry<String, Float> entry : tempResultedMap.entrySet()) {
            float tempVal = 0;
            tempVal = 0.5f * (1.0f / (i + 1)) + 0.5f * (1.0f / (Integer.parseInt(entry.getKey() + 1)));
            tempMap.put(String.valueOf(Integer.parseInt(entry.getKey())), tempVal);
            i++;
        }
        Map<String, Float> resultedMap = sortByComparator(tempMap, false);
        ArrayList<ResultDoc> retValue = new ArrayList<>();
        for (Map.Entry<String, Float> entry : resultedMap.entrySet()) {
            retValue.add(relDocs.get(Integer.parseInt(entry.getKey())));
        }
        return retValue;
    }

    private Map<String, Float> sortByComparator(Map<String, Float> unsortMap, final boolean order) {
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
        Map<String, Float> sortedMap = new LinkedHashMap<>();
        list.stream().forEach((entry) -> {
            sortedMap.put(entry.getKey(), entry.getValue());
        });
        return sortedMap;
    }

    private ArrayList<String> getAllUserIds(String folder) throws Throwable {
        ArrayList<String> allUserIds = new ArrayList<>();
        File dir = new File(folder);
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                String fileName = f.getName();
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
                allUserIds.add(fileName);
            }
        }
        return allUserIds;
    }

}
