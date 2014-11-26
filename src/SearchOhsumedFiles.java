import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.AfterEffectL;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelP;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.NormalizationH2;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class SearchOhsumedFiles {

	public static class SearchResult {
		public String queryId;
		public String results[];
		public double scores[];
	}

	private SearchOhsumedFiles() {
	}

	public static void main(String[] args) throws Exception {
		String index = null;
		String queries = null;
		String runId = null;
		float threshold = 0.0f;

		for (int i = 0; i < args.length; i++) {
			if ("-index".equals(args[i])) {
				index = args[i + 1];
				i++;
			} else if ("-queries".equals(args[i])) {
				queries = args[i + 1];
				i++;
			} else if ("-runid".equals(args[i])) {
				runId = args[i + 1];
				i++;
			} else if ("-threshold".equals(args[i])) {
				threshold = Float.parseFloat(args[i + 1]);
				i++;
			}
		}

		Similarity simfn = null;
		if ("bm25".equals(runId)) {
			simfn = new BM25Similarity();
		} else if ("dfr".equals(runId)) {
			simfn = new DFRSimilarity(new BasicModelP(), new AfterEffectL(),
					new NormalizationH2());
		} else if ("lm".equals(runId)) {
			simfn = new LMDirichletSimilarity();
		} else {
			throw new IllegalArgumentException("No such similarity model: "
					+ runId);
		}
		runSearch(index, queries, runId, threshold, simfn);
	}

	public static Map<String, String[]> runSearch(String index, String queries,
			String runId, float threshold, Similarity simfn)
			throws IOException, ParseException {
		HashMap<String, String[]> results = new HashMap<String, String[]>();
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(
				index)));
		IndexSearcher searcher = new IndexSearcher(reader);
		searcher.setSimilarity(simfn);
		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_41);

		BufferedReader in = null;
		if (queries != null) {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(
					queries), "UTF-8"));
		} else {
			in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
		}

		MultiFieldQueryParser queryParser = new MultiFieldQueryParser(
				Version.LUCENE_41, new String[] { "contents", "title" },
				analyzer);

		String titleQ = null, contentQ = null;
		SearchResult searchResult = null;
		List<SearchResult> resultsList = new ArrayList<SearchResult>();

		while (true) {
			assert (queries != null);
			String line = in.readLine();
			if (line == null || line.length() == -1) {
				break;
			}

			if (line.contains("<top>")) {
				searchResult = new SearchResult();
				resultsList.add(searchResult);
				continue;
			} else if (line.startsWith("<num>")) {
				assert (searchResult != null);
				searchResult.queryId = line.split(":")[1].trim();
			} else if (line.startsWith("<title>")) {
				titleQ = line.split(">")[1].trim();
			} else if (line.startsWith("<desc>")) {
				line = in.readLine();
				contentQ = line;
			} else if (line.startsWith("</top>")) {
				// Query query = queryParser.parse(QueryParser.escape(titleQ) +
				// " " +
				// QueryParser.escape(contentQ));
				BooleanQuery query = new BooleanQuery();
				query.add(queryParser.parse(QueryParser.escape(titleQ)),
						BooleanClause.Occur.SHOULD);
				query.add(queryParser.parse(QueryParser.escape(contentQ)),
						BooleanClause.Occur.SHOULD);
				/*
				 * Query query = queryParser.parse(Version.LUCENE_41, new
				 * String[] { QueryParser.escape(contentQ),
				 * QueryParser.escape(titleQ) }, new String[] { "contents",
				 * "title" }, analyzer);
				 */
				// System.out.println("Searching for: " +
				// query.toString(field));

				results.put(
						searchResult.queryId,
						performSearch(in, searcher, query,
								searchResult.queryId, runId == null ? "null"
										: runId, threshold));
			} else {
				continue;
			}
		}
		reader.close();
		return results;
	}

	public static String[] performSearch(BufferedReader in,
			IndexSearcher searcher, Query query, String queryId, String runId,
			float threshold) throws IOException {
		ArrayList<String> resultsList = new ArrayList<String>();
		TopDocs results = searcher.search(query, 100);
		ScoreDoc[] hits = results.scoreDocs;
		// not sure if dataset may have duplicates
		HashSet<String> docSet = new HashSet<String>();

		int start = 0;
		int end = 100;
		for (int i = start; i < end; i++) {
			Document doc = searcher.doc(hits[i].doc);
			String docno = doc.get("docno");
			assert (docno != null);
			if (docSet.contains(docno))
				continue;
			docSet.add(docno);
			if (isRelevant(threshold, hits[i].score / results.getMaxScore())) {
				String result = queryId + " Q0 " + docno + " " + i + " "
						+ hits[i].score + " " + runId;
				resultsList.add(result);
				System.out.println(result);
			} else
				break;
		}
		return (String[]) resultsList.toArray(new String[resultsList.size()]);
	}

	private static boolean isRelevant(float threshold, float normalizedScore) {
		if ((normalizedScore - threshold) > 0.0000001)
			return true;
		return false;
	}
}
