/**
 * 
 */
package it.sineo.jMangaViewer.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author DMTLS (ing. Luca Santarelli @ Datamat S.p.A)
 *
 */
public class PatternFormatter extends Formatter {

  private final static String defaultPattern = "%d{HH:mm:ss.SSS} %-5p %C{1}#%M(%L): %m%n"; 

  private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

  /* (non-Javadoc)
   * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
   */
  public String format(LogRecord record) {
    
    StringBuffer sb = new StringBuffer(defaultPattern.length() * 3);
    Date d = new Date(record.getMillis());
    synchronized (sdf) {
      sb.append(sdf.format(d));
    }
    sb.append(" ").append(record.getLevel().getLocalizedName());
    sb.append(" ").append(record.getSourceClassName());
    sb.append("#").append(record.getSourceMethodName());
    sb.append("(n/a): ").append(record.getMessage()).append("\n");
    return sb.toString();
  }
}
