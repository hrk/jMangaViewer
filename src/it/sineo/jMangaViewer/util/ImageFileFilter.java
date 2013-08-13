/**
 * 
 */
package it.sineo.jMangaViewer.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;

/**
 * @author wesia92
 */
public class ImageFileFilter implements FileFilter, FilenameFilter {

	/*
	 * (non-Javadoc)
	 * @see java.io.FileFilter#accept(java.io.File)
	 */
	public boolean accept(final File file) {
		return file.isFile() && fileIsSupportedImage(file.getName());
	}

	/*
	 * (non-Javadoc)
	 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
	 */
	public boolean accept(final File dir, final String name) {
		return fileIsSupportedImage(name);
	}

	public static boolean fileIsSupportedImage(final String filename) {
		String n = filename.toLowerCase();
		return (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif"));
	}

}
