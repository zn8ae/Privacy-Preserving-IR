package edu.virginia.cs.index;

import edu.virginia.cs.utility.SpecialAnalyzer;
import edu.virginia.cs.user.UserProfile;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class Searcher {

    private IndexSearcher indexSearcher;
    private SpecialAnalyzer analyzer;
    private UserProfile userProfile;
    private static SimpleHTMLFormatter formatter;

    private static final int numFragments = 4;
    private static final String defaultField = "content";
    private boolean activatePersonalization = false;

    /**
     * Sets up the Lucene index Searcher with the specified index.
     *
     * @param indexPath The path to the desired Lucene index.
     */
    public Searcher(String indexPath) {
        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexPath)));
            indexSearcher = new IndexSearcher(reader);
            analyzer = new SpecialAnalyzer();
            formatter = new SimpleHTMLFormatter("****", "****");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void activatePersonalization(boolean flag) {
        activatePersonalization = flag;
    }

    public void initializeUserProfile() {
        userProfile = new UserProfile();
    }

    public void buildUserProfile(String userProfilePath, String userID) {
        try {
            userProfile.createUserProfile(userProfilePath, userID);
        } catch (IOException ex) {
            Logger.getLogger(Searcher.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void updateUProfileUsingClickedDoc(String content) throws IOException {
        userProfile.updateUserProfileUsingClickedDocument(content);
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public double getAvgQueryLength() {
        return userProfile.getAvgQueryLength();
    }

    public void setSimilarity(Similarity sim) {
        indexSearcher.setSimilarity(sim);
    }

    /**
     * The main search function.
     *
     * @param searchQuery Set this object's attributes as needed.
     * @return
     */
    public SearchResult search(SearchQuery searchQuery) {
        BooleanQuery combinedQuery = new BooleanQuery();
        for (String field : searchQuery.fields()) {
            QueryParser parser = new QueryParser(Version.LUCENE_46, field, analyzer);
            try {
                Query textQuery = parser.parse(QueryParser.escape(searchQuery.queryText()));
                combinedQuery.add(textQuery, BooleanClause.Occur.MUST);
            } catch (ParseException exception) {
                exception.printStackTrace();
            }
        }
        return runSearch(combinedQuery, searchQuery);
    }

    /**
     * The simplest search function. Searches the abstract field and returns a
     * the default number of results.
     *
     * @param queryText The text to search
     * @return the SearchResult
     *
     */
    public SearchResult search(String queryText) {
        return search(new SearchQuery(queryText, defaultField));
    }

    public String search(String queryText, String field) {
        return runSearch(new SearchQuery(queryText, field), "content");
    }

    private static Map<String, Float> sortByComparator(Map<String, Float> unsortMap, final boolean order) {
        List<Entry<String, Float>> list = new LinkedList<>(unsortMap.entrySet());
        // Sorting the list based on values
        Collections.sort(list, (Entry<String, Float> o1, Entry<String, Float> o2) -> {
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

    private void printMap(Map<String, Float> map) {
        map.entrySet().stream().forEach((entry) -> {
            System.out.println("Key : " + entry.getKey() + " Value : " + entry.getValue());
        });
    }

    /**
     * Performs the actual Lucene search.
     *
     * @param luceneQuery
     * @param numResults
     * @return the SearchResult
     */
    private SearchResult runSearch(Query luceneQuery, SearchQuery searchQuery) {
        try {
            TopDocs docs = indexSearcher.search(luceneQuery, searchQuery.fromDoc() + searchQuery.numResults());
            ScoreDoc[] hits;
            String field = searchQuery.fields().get(0);
            if (activatePersonalization) {
                ScoreDoc[] tempHits = docs.scoreDocs;
                HashMap<String, Float> tempMap = new HashMap<String, Float>();
                HashSet<String> uniqueDocTerms = new HashSet<>();
//                    HashMap<String, Integer> uTerms = new HashMap<>();
                HashMap<String, Integer> uProf = userProfile.getUserProfile();
                //System.out.println("user profile token number: "+userProfile.totalTokens);
                //use prob from token
                for (int i = 0; i < tempHits.length; i++) {
                    uniqueDocTerms = new HashSet<>();
//                        uTerms = new HashMap<>();
                    ScoreDoc hit = tempHits[i];
                    Document doc = indexSearcher.doc(hit.doc);
                    TokenStream ts = doc.getField(field).tokenStream(analyzer);
                    CharTermAttribute terms = ts.addAttribute(CharTermAttribute.class);
                    ts.reset();
                    while (ts.incrementToken()) {
//                            String str = terms.toString();
//                            if (uTerms.containsKey(str)) {
//                                uTerms.put(str, uTerms.get(str) + 1);
//                            } else {
//                                uTerms.put(str, 1);
//                            }
                        uniqueDocTerms.add(terms.toString());
                    }
                    ts.end();
                    ts.close();

                    float tempVal = 0;
                    float lambda = 0.1f;
                    for (String str : uniqueDocTerms) {
//                        for (Entry<String, Integer> entry : uTerms.entrySet()) {
                        //calcualte prob on the fly
                        //user profile used only here for personalized search
//                            String str = entry.getKey();
                        Integer value = uProf.get(str);
                        if (value == null) {
                            value = 0;
                        }
                        //System.out.println("user token number: "+userProfile.totalTokens);
                        int tokenCount = userProfile.totalTokens;
                        if (tokenCount < 1) {
                            tokenCount = 1;
                        }
                        Float tokenProb = (value * 1.0f) / userProfile.totalTokens;//smooting
//                            tokenProb = tokenProb * entry.getValue();
                        Float refProb = userProfile.referenceModel.get(str);
                        if (refProb == null) {
                            refProb = 0.0f;
                        }
                        Float smoothedTokenProb = (1 - lambda) * tokenProb + lambda * refProb;
                        Float n = smoothedTokenProb;//probality calculation need here
                        if (n != null) {
                            tempVal = tempVal + n;
                        }
                    }
                    tempMap.put(String.valueOf(i), hit.score + tempVal);
                    tempHits[i].score = hit.score + tempVal;
                }
                Map<String, Float> resultedMap = sortByComparator(tempMap, false);
                //printMap(tempMap);
                int i = 0;
                hits = new ScoreDoc[tempHits.length];
                for (Map.Entry<String, Float> entry : resultedMap.entrySet()) {
                    hits[i] = tempHits[Integer.parseInt(entry.getKey())];
                    i++;
                }
                //Update user profile
                userProfile.updateUserProfile(searchQuery.queryText());
            } else {
                hits = docs.scoreDocs;
            }

            SearchResult searchResult = new SearchResult(searchQuery, docs.totalHits);
            for (ScoreDoc hit : hits) {
                Document doc = indexSearcher.doc(hit.doc);
                ResultDoc rdoc = new ResultDoc(hit.doc);
                String highlighted;
                try {
                    Highlighter highlighter = new Highlighter(formatter, new QueryScorer(luceneQuery));
                    rdoc.title("" + (hit.doc + 1));
                    String contents = doc.getField(field).stringValue();
                    String contentsJudge = doc.getField("clicked_url").stringValue();
                    rdoc.content(contents);
                    rdoc.url(contentsJudge);
                    String[] snippets = highlighter.getBestFragments(analyzer, field, contents, numFragments);
                    highlighted = createOneSnippet(snippets);
                } catch (InvalidTokenOffsetsException exception) {
                    exception.printStackTrace();
                    highlighted = "(no snippets yet)";
                }
                searchResult.addResult(rdoc);
                searchResult.setSnippet(rdoc, highlighted);
            }
            searchResult.trimResults(searchQuery.fromDoc());
            return searchResult;
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return new SearchResult(searchQuery);
    }

    private String runSearch(SearchQuery searchQuery, String indexableField) {
        BooleanQuery combinedQuery = new BooleanQuery();
        for (String field : searchQuery.fields()) {
            QueryParser parser = new QueryParser(Version.LUCENE_46, field, analyzer);
            try {
                Query textQuery = parser.parse(QueryParser.escape(searchQuery.queryText()));
                combinedQuery.add(textQuery, BooleanClause.Occur.MUST);
            } catch (ParseException exception) {
                exception.printStackTrace();
            }
        }

        Query luceneQuery = combinedQuery;
        String returnedResult = null;
        try {
            TopDocs docs = indexSearcher.search(luceneQuery, 1);
            ScoreDoc[] hits = docs.scoreDocs;
            Document doc = indexSearcher.doc(hits[0].doc);
            returnedResult = doc.getField(indexableField).stringValue();
        } catch (IOException exception) {
            System.out.println("Error in getting clicked URL");
        }
        return returnedResult;
    }

    /**
     * Create one string of all the extracted snippets from the highlighter
     *
     * @param snippets
     * @return
     */
    private String createOneSnippet(String[] snippets) {
        String result = " ... ";
        for (String s : snippets) {
            result += s + " ... ";
        }
        return result;
    }
}
