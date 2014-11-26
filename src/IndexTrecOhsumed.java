import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class IndexTrecOhsumed {

	private IndexTrecOhsumed() {
	}

	public static void main(String[] args) {
		String indexPath = "index";
		String docsPath = null;
		boolean create = true;
		double ramBufferSize = 512.0;
		for (int i = 0; i < args.length; i++) {
			if ("-index".equals(args[i])) {
				indexPath = args[i + 1];
				i++;
			} else if ("-file".equals(args[i])) {
				docsPath = args[i + 1];
				i++;
			} else if ("-update".equals(args[i])) {
				create = false;
			}
		}

		if (docsPath == null) {
			System.err.println("Ivalid arguments!");
			System.exit(1);
		}

		final File docDir = new File(docsPath);
		if (!docDir.exists() || !docDir.canRead()) {
			System.out
					.println("Document directory '"
							+ docDir.getAbsolutePath()
							+ "' does not exist or is not readable, please check the path");
			System.exit(1);
		}

		Date start = new Date();
		try {
			System.out.println("Indexing to directory '" + indexPath + "'...");

			Directory dir = FSDirectory.open(new File(indexPath));
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_41);
			IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_41,
					analyzer);

			if (create) {
				iwc.setOpenMode(OpenMode.CREATE);
			} else {
				iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
			}
			iwc.setRAMBufferSizeMB(ramBufferSize);

			IndexWriter writer = new IndexWriter(dir, iwc);
			readFile(docDir, writer);

			// NOTE: if you want to maximize search performance,
			// you can optionally call forceMerge here. This can be
			// a terribly costly operation, so generally it's only
			// worth it when your index is relatively static (ie
			// you're done adding documents to it):
			//
			writer.forceMerge(1);
			writer.close();

			Date end = new Date();
			System.out.println(end.getTime() - start.getTime()
					+ " total milliseconds");

		} catch (IOException e) {
			System.out.println(" caught a " + e.getClass()
					+ "\n with message: " + e.getMessage());
		}
	}

	private static void readFile(File file, IndexWriter writer)
			throws IOException {
		assert (file.canRead());
		BufferedReader rdr = new BufferedReader(new FileReader(file));
		System.out.println("Reading " + file.toString());
		Document doc = new Document();
		String line;
		boolean in_doc = false, in_title = false;
		int i = 1;
		StringBuffer sb = new StringBuffer();
		while (true) {
			line = rdr.readLine();
			if (line == null) {
				break;
			}
			if (!in_doc && !in_title) {
				if (line.startsWith(".U")) {
					line = rdr.readLine();
					doc.add(new StringField("docno", line.trim(),
							Field.Store.YES));
				} else if (line.startsWith(".T")) {
					in_title = true;
				} else if (line.startsWith(".W")) {
					in_doc = true;
				}
			} else {
				if (!line.startsWith("."))
					sb.append(line);
				else {
					if (in_doc) {
						in_doc = false;
						if (sb.length() > 0) {
							doc.add(new TextField("contents", sb.toString(),
									Field.Store.NO));

							sb.delete(0, sb.length());
							writer.addDocument(doc);
						}
						doc = new Document();
					} else {
						in_title = false;
						if (sb.length() > 0) {
							doc.add(new TextField("title", sb.toString(),
									Field.Store.NO));
							sb.delete(0, sb.length());
						}
						++i;
						if (i % 2000 == 0) {
							System.out.println("Indexed " + (i) + ".");
						}
					}
				}
			}
		}
		rdr.close();
	}
}
