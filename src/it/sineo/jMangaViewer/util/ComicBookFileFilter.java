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
public class ComicBookFileFilter extends javax.swing.filechooser.FileFilter implements FileFilter,
		FilenameFilter {

	/*
	 * (non-Javadoc)
	 * @see java.io.FileFilter#accept(java.io.File)
	 */
	public boolean accept(File file) {
		return file.isDirectory() || isExtensionSupported(file.getName());
	}

	/*
	 * (non-Javadoc)
	 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
	 */
	public boolean accept(File dir, String name) {
		return isExtensionSupported(name);
	}

	private static boolean isExtensionSupported(String filename) {
		if (filename.endsWith(".zip") || filename.endsWith(".cbz")) {
			return true;
		}
		try {
			Class.forName("de.innosystec.unrar.protocols.rar.RarURLConnection");
			if (filename.endsWith(".rar") || filename.endsWith(".cbr")) {
				return true;
			}
		} catch (ClassNotFoundException cnfex) {
			/*
			 * RAR support is not enabled.
			 */
		}
		return false;
	}

	@Override
	public String getDescription() {
		return "Comic Book files (*.zip, *.cbz)  and directories";
	}

	public static boolean fileIsComicBookArchive(File file) {
		return isExtensionSupported(file.getName());
	}

}
