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
	private boolean fullClassName = true;

	public PatternFormatter() {
		fullClassName = false;
	}

	public PatternFormatter(boolean useShortName) {
		fullClassName = !useShortName;
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
	 */
	public String format(LogRecord record) {
		String className = record.getSourceClassName();
		if (!fullClassName) {
			className = shrinkClassName(className);
		}
		return MessageFormat.format("{0} {1} {2}#{3}: {4}\n", sdf.format(record.getMillis()),
				record.getLevel(), className, record.getSourceMethodName(), record.getMessage());
	}

	protected String shrinkClassName(String className) {
		return className.substring(className.lastIndexOf(".") + 1);
	}
}
