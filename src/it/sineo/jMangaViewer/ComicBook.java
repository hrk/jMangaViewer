package it.sineo.jMangaViewer;

import java.net.URL;
import java.util.List;

public class ComicBook {

	private List<URL> imageUrls;
	private int currentIdx;

	public ComicBook(List<URL> urls) {
		imageUrls = urls;
		currentIdx = 0;
	}

	public URL getFirstPageURL() {
		currentIdx = 0;
		return imageUrls.get(currentIdx);
	}

	public URL getLastPageURL() {
		currentIdx = imageUrls.size() - 1;
		return imageUrls.get(currentIdx);
	}

	public URL getNextPageURL() {
		return imageUrls.get(++currentIdx);
	}

	public URL getPreviousPageURL() {
		return imageUrls.get(--currentIdx);
	}

	public boolean hasNextPage() {
		return (imageUrls.size() > 0 && currentIdx < imageUrls.size() - 1);
	}

	public boolean hasPreviousPage() {
		return (imageUrls.size() > 0 && currentIdx > 0);
	}

	public URL getCurrentPageURL() {
		return imageUrls.get(currentIdx);
	}

	public int getNumberOfPages() {
		return imageUrls.size();
	}

	public int getCurrentPageNumber() {
		return currentIdx + 1;
	}

}
