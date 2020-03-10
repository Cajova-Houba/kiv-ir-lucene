package cz.zcu.kiv.nlp.ir.rpol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class RPolLucene {

    public static final String STORAGE_DIR = "./storage";
    private static final String STOPWORDS_FILE = "stopwords-en.txt";
    private static final String INDEX_FILE = "rpol.index.lucene";
    private static final String SOURCE_JSON = "rpol-comments.json";

    private static final int HITS_PER_PAGE = 10;
    private static final int MAX_RESULTS = 100;


    public static void main(String[] args) throws IOException, ParseException {
        // 0. Specify the analyzer for tokenizing text.
        //    The same analyzer should be used for indexing and searching
        // 0.1 load data
        Analyzer analyzer = createAnalyzer();
        List<Comment> comments = loadComments();

        // 1. create the index or load the existing one
        Directory index = getIndex(analyzer, comments);

        // 2. queries
        Map<String, Query> queries = prepareQueries(analyzer);

        performQueries(queries, index);
    }

    /**
     * Perform given queries.
     * @param queries Map of query name -> query.
     * @param index Index to perform queries over.
     * @throws IOException
     */
    private static void performQueries(Map<String, Query> queries, Directory index) throws IOException {
        IndexReader reader = DirectoryReader.open(index);

        for(String queryName : queries.keySet()) {
            performQuery(queryName, queries.get(queryName), reader);
        }

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();
    }

    /**
     * Performs and prints one query.
     *
     * @param queryName
     * @param query
     * @param reader
     * @throws IOException
     */
    private static void performQuery(String queryName, Query query, IndexReader reader) throws IOException {
        System.out.println("Performing query: "+queryName);
        int hitsPerPage = HITS_PER_PAGE;
        IndexSearcher searcher = new IndexSearcher(reader);
        int page = 0;
        TopDocs docs = performQueryPage(query, searcher, hitsPerPage, page);
        ScoreDoc[] hits = docs.scoreDocs;

        while(hits.length > 0) {
            System.out.println("Page "+(page+1)+"\n"+hits.length+" hits.\n=======================");
            printPage(page, hits, searcher);

            page++;
            docs = performQueryPage(query, searcher, hitsPerPage, page);
            hits = docs.scoreDocs;
            System.out.println("=======================\n");
        }
        System.out.println("Done.\n\n\n");
    }

    private static void printPage(int page, ScoreDoc[] hits, IndexSearcher searcher) throws IOException {
        for(int i=0;i<hits.length;++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            System.out.println((i+1)+": \nusername: "+d.get("username")+"\ntext: "+d.get("text")+"\nscore: "+d.get("score"));
        }
    }

    private static TopDocs performQueryPage(Query q, IndexSearcher searcher, int hitsPerPage, int page) throws IOException {
        TopScoreDocCollector collector = TopScoreDocCollector.create(MAX_RESULTS, MAX_RESULTS);

        searcher.search(q, collector);

        int startIndex = page*hitsPerPage;
        return collector.topDocs(startIndex, hitsPerPage);
    }

    private static Map<String, Query> prepareQueries(Analyzer analyzer) throws ParseException {
        System.out.println("Preparing queries.");
        Map<String, Query> queries = new HashMap<>();
        queries.put("Trump query", new QueryParser("text", analyzer).parse("Trump"));
        queries.put("Trump and Putin query", new QueryParser("text", analyzer).parse("\"Trump\" AND \"Putin\""));
        queries.put("Wildcard query", new QueryParser("text", analyzer).parse("(c*ism) OR (n*ism) OR (f*ism) OR (p*ism)"));
        queries.put("Trump impeachment", new QueryParser("text", analyzer).parse("\"Trump impeachment\"~5"));
        queries.put("Russia and elections", new QueryParser("text", analyzer).parse("(\"Trump\" OR \"Russia\") AND \"elections\""));
        queries.put("China trade war", new QueryParser("text", analyzer).parse("(\"China\" OR \"USA\") AND (\"tradewar\" OR \"trade war\")"));
        return queries;
    }

    private static Directory getIndex(Analyzer analyzer, List<Comment> comments) throws IOException {
        System.out.println("Getting index.");
        Path p = Paths.get(STORAGE_DIR+"\\"+INDEX_FILE);
        boolean indexExists = Files.exists(p);
        Directory index = FSDirectory.open(p);
        if(!indexExists) {
            System.out.println("No existing index found, creating new one.");
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter w = new IndexWriter(index, config);
            indexComments(w, comments);
            w.close();
        }

        return index;
    }

    private static void indexComments(IndexWriter w, List<Comment> comments) throws IOException {
        System.out.println("Indexing "+comments.size()+" comments.");
        for(Comment comment : comments) {
            Document doc = new Document();
            doc.add(new StringField("username", comment.getUsername(), Field.Store.YES));
            doc.add(new TextField("text", comment.getText(), Field.Store.YES));
            doc.add(new StringField("score", Integer.toString(comment.getScore()), Field.Store.YES));
            doc.add(new StringField("timestamp", comment.getTimestamp(), Field.Store.YES));
            w.addDocument(doc);
        }
    }

    private static List<Comment> loadComments() throws IOException {
        String filename = getStorageFilePath(SOURCE_JSON);
        System.out.println("Loading comments from: "+filename);
        ObjectMapper mapper = new ObjectMapper();
        List<HashMap<String, Object>> mappedData = mapper.readValue(new File(filename), new ArrayList().getClass());
        List<Comment> confessions = new ArrayList<Comment>(mappedData.size());
        for(HashMap<String, Object> mappedConfession : mappedData) {
            confessions.add(new Comment(
                    mappedConfession.get("username").toString(),
                    mappedConfession.get("text").toString(),
                    (Integer)mappedConfession.get("score"),
                    mappedConfession.get("timestamp") == null ? "" : mappedConfession.get("timestamp").toString()
            ));
        }

        return confessions;
    }

    private static Analyzer createAnalyzer() throws IOException {
        System.out.println("Creating analyzer.");
        return new EnglishAnalyzer(new CharArraySet(loadStopwords(STOPWORDS_FILE), true));
    }

    /**
     * Returns path to file in storage folder.
     * @param filename
     * @return
     */
    private static String getStorageFilePath(String filename) {
        return STORAGE_DIR+"\\"+ filename;
    }


    /**
     * Load stopwords from file in storage directory.
     * @param filename
     * @return
     */
    private static Collection<String> loadStopwords(String filename) throws IOException {
        return Files.readAllLines(Paths.get(STORAGE_DIR+"\\"+filename));
    }
}
