/**
 * 
 */
package it.sineo.jMangaViewer.util;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author Luca Santarelli
 * 
 */
public class PatternFormatter extends Formatter {

	private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

	/*
	 * (non-Javadoc)
	 * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
	 */
	public String format(LogRecord record) {
		return MessageFormat.format("{0} {1} {2}#{3}(n/a): {4}\n", sdf.format(record.getMillis()),
				record.getLevel(), record.getSourceClassName(), record.getSourceMethodName(),
				record.getMessage());
	}
}
