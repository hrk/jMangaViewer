package it.sineo.jMangaViewer;

import it.sineo.jMangaViewer.util.ComicBookFileFilter;
import it.sineo.jMangaViewer.util.ComicBookPageComparator;
import it.sineo.jMangaViewer.util.ImageFileFilter;

import java.awt.FileDialog;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;
import de.innosystec.unrar.protocols.rar.Handler;
import de.innosystec.unrar.rarfile.FileHeader;

public class Launcher {

	private static Logger log = Logger.getLogger(Launcher.class.getCanonicalName());

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.ALL);
		log.addHandler(ch);
		log.setLevel(Level.ALL);

		File selectedFile = null;

		switch (args.length) {
			case 0: {
				if ("Mac OS X".compareTo(System.getProperty("os.name", "")) != 0) {
					JFileChooser jfc = new JFileChooser();
					jfc.setMultiSelectionEnabled(false);
					jfc.setFileFilter(new ComicBookFileFilter());
					jfc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

					if (jfc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						selectedFile = jfc.getSelectedFile();
					} else {
						log.fine("no file selected");
					}
				} else {
					/*
					 * On Mac OS X, we mimic the Mac UI with the FileDialog class.
					 */
					log.fine("using FileDialog to adapt to Mac OS X UI");
					JFrame f = new JFrame();
					f.setUndecorated(true);
					f.validate();
					FileDialog fd = new FileDialog(f, "Open Comic Book");
					fd.setFilenameFilter(new ComicBookFileFilter());
					fd.setVisible(true);
					String directory = fd.getDirectory();
					String file = fd.getFile();
					if (file != null && file.length() > 0 && directory != null && directory.length() > 0) {
						selectedFile = new File(new File(directory), file);
					} else {
						log.fine("no file selected");
					}
					f.dispose();
				}
				break;
			}
			case 1: {
				selectedFile = new File(args[0]);
				break;
			}
			default: {
				break;
			}
		}
		if (selectedFile == null) {
			System.err.println("Usage: java " + Launcher.class.getCanonicalName() + " [file | directory]");
			System.exit(-1);
		} else {
			log.fine("selected file: " + selectedFile);
			if (!selectedFile.exists()) {
				System.err.println("File " + selectedFile.getName() + " does not exist.");
				System.exit(-1);
			}
			try {
				ComicBook cb = buildComicBook(selectedFile);
				new Viewer(cb, new Preferences());
			} catch (IOException ioex) {
				ioex.printStackTrace();
				JOptionPane.showMessageDialog(null, selectedFile + " could not be opened. Error is: " + ioex);
			}
		}
	}

	private static ComicBook buildComicBook(File file) throws IOException {
		ComicBook cb = null;
		if (file.isDirectory()) {
			log.fine("TODO: improve directory reading");

			File[] files = file.listFiles((FileFilter) new ImageFileFilter());
			System.out.println("found " + files.length + " files");
			List<URL> urls = new ArrayList<URL>(files.length);
			for (int i = 0; i < files.length; i++) {
				URL url = new URL(files[i].toURI().toURL().toString());
				log.fine("adding " + url.toString());
				urls.add(url);
			}
			Collections.sort(urls, new ComicBookPageComparator());
			cb = new ComicBook(urls);
		} else if (ComicBookFileFilter.fileIsComicBookArchive(file)) {
			List<URL> urls = new ArrayList<URL>();
			if (file.getName().endsWith(".zip") || file.getName().endsWith(".cbz")) {
				JarFile jf = new JarFile(file);
				Enumeration<JarEntry> entries = jf.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (ImageFileFilter.fileIsSupportedImage(entry.getName())) {
						String name = "jar:" + buildPath(file, entry.getName());
						URL url = new URL(name);
						// log.fine("adding " + url.toString());
						urls.add(url);
					}
				}
			} else if (file.getName().endsWith(".rar") || file.getName().endsWith(".cbr")) {
				try {
					Archive rarFile = new Archive(file);
					FileHeader entry = null;
					while ((entry = rarFile.nextFileHeader()) != null) {
						if (entry.isDirectory()) {
							continue;
						}
						if (ImageFileFilter.fileIsSupportedImage(entry.getFileNameString())) {
							File fDummy = new File(entry.getFileNameString());
							log.fine("fDummy: " + fDummy.toURI());
							URL url = new URL("rar", null, -1, buildPath(file, entry.getFileNameString()), new Handler());
							// log.fine("adding " + url.toString());
							urls.add(url);
						}
					}
				} catch (RarException rex) {
					rex.printStackTrace();
				}
			}
			Collections.sort(urls, new ComicBookPageComparator());
			cb = new ComicBook(urls);
		} else if (ImageFileFilter.fileIsSupportedImage(file.getName())) {
			List<URL> urls = new ArrayList<URL>(1);
			String name = file.toURI().toURL().toString();
			URL url = new URL(name);
			urls.add(url);
			cb = new ComicBook(urls);
		} else {
			log.severe("file is not a supported comic book file");
		}
		if (cb != null && cb.getNumberOfPages() == 0) {
			throw new IllegalArgumentException("The selected file doesn't contain images");
		}
		return cb;
	}

	private static String buildPath(File archiveFile, String imageName) {
		String res = imageName.replaceAll("%", "%25");
		res = res.replaceAll("#", "%23");
		// res = res.replaceAll(" ", "%20");
		return archiveFile.toURI().toString().replaceAll("!", "%21") + "!/" + res;
	}

}
