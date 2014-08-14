package it.sineo.jMangaViewer.util;

import java.net.URL;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ComicBookPageComparator implements Comparator<URL> {

	private Pattern pattern = Pattern.compile("(.*)([\\d]+)(.*)");

	private final static Logger log = Logger.getLogger(ComicBookPageComparator.class.getName());

	public int compare(URL url1, URL url2) {
		final String s1 = url1.toExternalForm();
		final String s2 = url2.toExternalForm();

		final Integer lenPaths1 = s1.replaceAll("[^/]", "").length();
		final Integer lenPaths2 = s2.replaceAll("[^/]", "").length();
		int res = lenPaths1.compareTo(lenPaths2);
		if (res == 0) {
			res = s1.compareToIgnoreCase(s2);
		}
		return res;
	}

}
