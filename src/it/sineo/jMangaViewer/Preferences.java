package it.sineo.jMangaViewer;

import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

public class Preferences {
	private final static Logger log = Logger.getLogger(Preferences.class.getCanonicalName());

	/**
	 * Scale the image to fir the width of the window.
	 */
	public final static int SCALE_WIDTH = 1;
	/**
	 * Scale the image to fit the height of the window.
	 */
	public final static int SCALE_HEIGHT = 2;
	/**
	 * Scale the image to fit the whole window.
	 */
	public final static int SCALE_WINDOW = 3;
	/**
	 * No scaling, corresponds to the original size of the image.
	 */
	public final static int SCALE_ORIGINAL = 4;

	/**
	 * Scale the image to a (calculated) % of the original (varying using +/-
	 * keys) in order to keep the same screen proportions/size even if the images
	 * have a different size. Useful if you're reading a bunch of comics where
	 * each one has a different size and you don't want to change the zoom factor
	 * at every comic.
	 */
	public final static int SCALE_FIXED = 5;

	private int scaleFactor = SCALE_ORIGINAL;
	private float zoomFactor = 1.0f;

	/**
	 * Corresponds to SCALE_SMOOTH
	 */
	public final static int QUALITY_HIGH = Image.SCALE_SMOOTH;
	/**
	 * Corresponds to SCALE_AREA_AVERAGE
	 */
	public final static int QUALITY_MEDIUM = Image.SCALE_AREA_AVERAGING;
	/**
	 * Corresponds to SCALE_FAST
	 */
	public final static int QUALITY_FAST = Image.SCALE_FAST;

	private int scaleQuality = QUALITY_MEDIUM;

	/**
	 * <p>
	 * Scrolls the image vertically; if the end of the image is reached, vertical
	 * scroll is reset and horizontal scroll is advanced.
	 * </p>
	 * <p>
	 * In a manga style, this is like reading komas in a 'N'-like pattern.
	 * </p>
	 */
	public final static int SCROLL_PRIORITY_VERTICAL = 1;
	/**
	 * <p>
	 * Scrolls the image horizontally; if the end of the image is reached,
	 * horizontal scroll is reset and vertical scroll is advanced.
	 * </p>
	 * <p>
	 * In a manga style, this is like reading komas in a 'S'-like pattern.
	 * </p>
	 */
	public final static int SCROLL_PRIORITY_HORIZONTAL = 2;

	private int scrollPriority = SCROLL_PRIORITY_VERTICAL;

	/**
	 * Pages advance from right to left, as in japanese mangas. This setting does
	 * not affect file ordering, but two page composition and navigation via arrow
	 * keys (left and right).
	 */
	public final static int READING_RIGHT_TO_LEFT = 1;
	/**
	 * Pages advance from left to right, as in western comic books. This setting
	 * does not affect file ordering, but two page composition and navigation via
	 * arrow keys (left and right).
	 */
	public final static int READING_LEFT_TO_RIGHT = 2;

	private int readingStyle = READING_RIGHT_TO_LEFT;

	public final static int ON_SCREEN_DISPLAY_OFF = 0;
	public final static int ON_SCREEN_DISPLAY_ON = 1;

	private int onScreenDisplay = ON_SCREEN_DISPLAY_ON;

	private boolean twoPage = false;

	private boolean transparent = false;

	/*
	 * 
	 */
	public Preferences() {
		try {
			Properties p = new Properties();
			FileInputStream fis = new FileInputStream(
					System.getProperty("user.home", "${user.home}") + "/.jMangaViewer.properties");
			p.load(fis);
			setScaleFactor(read(p, "scaleFactor", SCALE_ORIGINAL));
			setZoomFactor(read(p, "zoomFactor", 1.0f));
			setScaleQuality(read(p, "scaleQuality", QUALITY_HIGH));
			setScrollPriority(read(p, "scrollpriority", SCROLL_PRIORITY_VERTICAL));
			setReadingStyle(read(p, "readingStyle", READING_LEFT_TO_RIGHT));
			setOnScreenDisplay(read(p, "onScreenDisplay", ON_SCREEN_DISPLAY_ON));
			setTransparent(read(p, "transparent", 0) == 1);

		} catch (IOException ioex) {
			log.info("could not load preferences from file: " + ioex.getMessage());
		}
	}

	private Integer read(Properties p, String property, int defaultValue) {
		try {
			return Integer.valueOf(p.getProperty(property));
		} catch (Exception ex) {
			return defaultValue;
		}
	}

	private Float read(Properties p, String property, float defaultValue) {
		try {
			return Float.valueOf(p.getProperty(property));
		} catch (Exception ex) {
			return defaultValue;
		}
	}

	public void save() {
		try {
			File file = new File(
					System.getProperty("user.home", "${user.home}") + "/.jMangaViewer.properties");
			log.info("saving preferences into file " + file);
			Properties p = new Properties();
			p.setProperty("scaleFactor", Integer.toString(getScaleFactor()));
			if (getScaleFactor() == SCALE_FIXED) {
				p.setProperty("zoomFactor", Float.toString(getZoomFactor()));
			} else {
				p.remove("zoomFactor");
			}
			p.setProperty("scaleQuality", Integer.toString(getScaleQuality()));
			p.setProperty("scrollPriority", Integer.toString(getScrollPriority()));
			p.setProperty("readingStyle", Integer.toString(getReadingStyle()));
			p.setProperty("onScreenDisplay", Integer.toString(getOnScreenDisplay()));
			p.setProperty("transparent", isTransparent() ? "1" : "0");
			FileOutputStream fos = new FileOutputStream(file);
			p.store(fos, "jMangaViewer preferences");
		} catch (IOException ioex) {
			log.info("could not save preferences: " + ioex.getMessage());
		}
	}

	/* */
	public float getZoomFactor() {
		return zoomFactor;
	}

	public void setZoomFactor(float zoomFactor) {
		this.zoomFactor = zoomFactor;
	}

	public int getScaleFactor() {
		return scaleFactor;
	}

	public void setScaleFactor(int scaleFactor) {
		switch (scaleFactor) {
			case SCALE_HEIGHT:
			case SCALE_WIDTH:
			case SCALE_ORIGINAL:
			case SCALE_WINDOW:
			case SCALE_FIXED:
				this.scaleFactor = scaleFactor;
				break;
			default: {
				throw new IllegalArgumentException("unmapped value for scale factor: " + scaleFactor);
			}
		}
	}

	public int getScrollPriority() {
		return scrollPriority;
	}

	public void setScrollPriority(int scrollPriority) {
		switch (scrollPriority) {
			case SCROLL_PRIORITY_HORIZONTAL:
			case SCROLL_PRIORITY_VERTICAL: {
				this.scrollPriority = scrollPriority;
				break;
			}
			default: {
				throw new IllegalArgumentException("Unmapped value for scroll priority: " + scrollPriority);
			}
		}

	}

	public int getReadingStyle() {
		return readingStyle;
	}

	public void setReadingStyle(int readingStyle) {
		switch (readingStyle) {
			case READING_LEFT_TO_RIGHT:
			case READING_RIGHT_TO_LEFT: {
				this.readingStyle = readingStyle;
				break;
			}
			default: {
				throw new IllegalArgumentException("Unmapped value for reading style: " + readingStyle);
			}
		}
	}

	public boolean isTwoPage() {
		return twoPage;
	}

	public void setTwoPage(boolean twoPage) {
		this.twoPage = twoPage;
	}

	public int getScaleQuality() {
		return scaleQuality;
	}

	public void setScaleQuality(int scaleQuality) {
		this.scaleQuality = scaleQuality;
	}

	public int getOnScreenDisplay() {
		return onScreenDisplay;
	}

	public void setOnScreenDisplay(int onScreenDisplay) {
		switch (onScreenDisplay) {
			case ON_SCREEN_DISPLAY_OFF:
			case ON_SCREEN_DISPLAY_ON: {
				this.onScreenDisplay = onScreenDisplay;
				break;
			}
			default: {
				throw new IllegalArgumentException(
						"Unmapped value for on screen display: " + onScreenDisplay);
			}
		}
	}

	public boolean isTransparent() {
		return transparent;
	}

	public void setTransparent(boolean transparent) {
		this.transparent = transparent;
	}

}
