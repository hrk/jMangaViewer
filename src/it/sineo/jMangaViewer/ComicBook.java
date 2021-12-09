package it.sineo.jMangaViewer;

import java.awt.Image;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

public class ComicBook {

	private List<URL> imageUrls;
	private int currentIdx;

	private transient HashMap<URL, Image> images;
	private transient Thread workerThread;

	public ComicBook(List<URL> urls) {
		imageUrls = urls;
		currentIdx = 0;

		images = new HashMap<URL, Image>(imageUrls.size());
		try {
			Image firstImage = ImageIO.read(imageUrls.get(0));
			images.put(imageUrls.get(0), firstImage);
		} catch (IOException ioex) {
			System.err.println(ioex.getMessage());
		}

		workerThread = new Thread(new Runnable() {

			public void run() {
				for (URL url : imageUrls) {
					try {
						Image image = ImageIO.read(url);
						synchronized (images) {
							images.put(url, image);
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				notifyAll();
			}
		});
		// workerThread.start();

	}

	public URL getFirstPageURL() {
		currentIdx = 0;
		return imageUrls.get(currentIdx);
	}

	public URL getLastPageURL() {
		currentIdx = imageUrls.size() - 1;
		return imageUrls.get(currentIdx);
	}

	public Image getFirstPage() {
		return images.get(getFirstPageURL());
	}

	public URL getNextPageURL() {
		return imageUrls.get(++currentIdx);
	}

	public Image getNextPage() {
		if (workerThread.isAlive()) {
			System.out.println("THREAD ANCORA VIVO, MI METTO IN WAIT");
			try {
				wait();
				System.out.println("SONO USCITO DALLA WAIT");
			} catch (InterruptedException iex) {
				iex.printStackTrace();
			}
		}
		return images.get(getNextPageURL());
	}

	public URL getPreviousPageURL() {
		return imageUrls.get(--currentIdx);
	}

	public Image getPreviousImage() {
		if (workerThread.isAlive()) {
			System.out.println("THREAD ANCORA VIVO, MI METTO IN WAIT");
			try {
				wait();
				System.out.println("SONO USCITO DALLA WAIT");
			} catch (InterruptedException iex) {
				iex.printStackTrace();
			}
		}
		return images.get(getPreviousPageURL());
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

	public Image getCurrentPage() {
		if (workerThread.isAlive()) {
			System.out.println("THREAD ANCORA VIVO, MI METTO IN WAIT");
			try {
				wait();
				System.out.println("SONO USCITO DALLA WAIT");
			} catch (InterruptedException iex) {
				iex.printStackTrace();
			}
		}
		return images.get(getCurrentPageURL());
	}

	public Image getImage(URL url) {
		if (workerThread.isAlive()) {
			System.out.println("THREAD ANCORA VIVO, MI METTO IN WAIT");
			try {
				wait();
				System.out.println("SONO USCITO DALLA WAIT");
			} catch (InterruptedException iex) {
				iex.printStackTrace();
			}
		}
		return images.get(url);
	}

}
