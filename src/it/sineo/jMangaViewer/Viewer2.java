package it.sineo.jMangaViewer;

import it.sineo.jMangaViewer.util.PatternFormatter;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** @see http://stackoverflow.com/questions/7456227 */
public class Viewer2 extends JPanel {
	private static final long serialVersionUID = 1L;

	private final static Logger log = Logger.getLogger(Viewer2.class
			.getCanonicalName());

	private final static int NUMBER_OF_BUFFERS = 2;

	private final static String ACTION_KEY = "jMangaViewer_key";

	private final static String EXIT = "exit";
	private final static String SHOW_HIDE = "show_hide";
	private final static String TOGGLE_OSD = "toggle_old";
	private final static String GO_LEFT = "go_left";
	private final static String GO_RIGHT = "go_right";
	private final static String GO_UP = "go_up";
	private final static String GO_DOWN = "go_down";
	private final static String GO_TO_PAGE = "go_to_page";
	private final static String SHOW_FIRST_PAGE = "show_first_page";
	private final static String SHOW_LAST_PAGE = "show_last_page";
	private final static String SCROLL = "scroll";
	/* Scale factor */
	private final static String SCALE_WIDTH = "scale_width";
	private final static String SCALE_HEIGHT = "scale_height";
	private final static String SCALE_ORIGINAL = "scale_original";
	private final static String SCALE_WINDOW = "scale_window";
	/* Scale quality */
	private final static String QUALITY_FAST = "scale_fast";
	private final static String QUALITY_MEDIUM = "scale_medium";
	private final static String QUALITY_HIGH = "scale_high";
	private final static String READING_STYLE_L2R = "reading_l2r";
	private final static String READING_STYLE_R2L = "reading_r2l";
	private final static String SCROLL_PRIORITY_HORIZONTAL = "prio_h";
	private final static String SCROLL_PRIORITY_VERTICAL = "prio_v";

	private JFrame f = new JFrame("jMangaViewer");
	private GraphicsDevice device;

	private boolean isFullScreen = false;
	private boolean isHiDPI;
	private boolean dirty = true;

	private final int scrollPollingTime = 100;
	/*
	 * Mouse handling.
	 */
	long mouseLastCheck = 0;
	int mouseX, mouseY;

	/*
	 * Shapes
	 */
	private final int strokeWidth = 16;
	private final int arcLength = 80;
	private Thread overlayThread;
	private String zoomFactor;
	private NumberFormat percFormat = NumberFormat.getPercentInstance();
	private GeneralPath shape = null;

	/*
	 * Position and scrolling
	 */
	Point pPaint = null, pLastPaint = new Point(0, 0);
	boolean ignoreX = false, ignoreY = false;
	int displayWidth, displayHeight, imageWidth, imageHeight;
	Image screenImage;

	/*
	 * Paint related objects.
	 */
	AlphaComposite compositeTransparent = AlphaComposite.getInstance(
			AlphaComposite.SRC_OVER, 0.6F);
	AlphaComposite compositeOpaque = AlphaComposite.getInstance(
			AlphaComposite.SRC_OVER, 1.0F);
	Paint blackPaint = Color.BLACK;
	Paint whitePaint = new Color(0xFF, 0xFF, 0xFF, 0xFF);
	Font font;
	/*
 * 
 */
	private ComicBook comicBook;
	private Preferences preferences;
	private int messageDuration = 3000;

	protected class OverlayRunnable implements Runnable {
		public void run() {
			try {
				Thread.sleep(messageDuration);
				log.fine("slept successfully, will remove shape and repaint");
			} catch (InterruptedException iex) {
				// Do nothing.
				log.fine("sleeping was interrupted, will remove shape and repaint");
			}
			shape = null;
			dirty = true;
			overlayThread = null;
			// repaint();
			renderWrapper();
		}
	}

	/*
	 * Actions
	 */
	private Action exitAction = new AbstractAction(EXIT) {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			preferences.save();
			f.dispatchEvent(new WindowEvent(f, WindowEvent.WINDOW_CLOSING));
		}
	};

	private Action showHideAction = new AbstractAction(SHOW_HIDE) {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			if (isFullScreen) {
				device.setFullScreenWindow(null);
			} else {
				device.setFullScreenWindow(f);
				dirty = true;
				renderWrapper();
			}
			isFullScreen = !isFullScreen;
		}
	};

	private Action toggleOSDAction = new AbstractAction(TOGGLE_OSD) {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent e) {
			if (preferences.getOnScreenDisplay() == Preferences.ON_SCREEN_DISPLAY_OFF) {
				preferences.setOnScreenDisplay(Preferences.ON_SCREEN_DISPLAY_ON);
			} else {
				preferences.setOnScreenDisplay(Preferences.ON_SCREEN_DISPLAY_OFF);
			}
			dirty = true;
			renderWrapper();
		}
	};

	private class NavigateAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public NavigateAction(String action) {
			putValue(ACTION_KEY, action);
		}

		public void actionPerformed(ActionEvent e) {
			final String key = (String) getValue(ACTION_KEY);
			if (SHOW_FIRST_PAGE.equals(key)) {
				showFirstPage();
			} else if (SHOW_LAST_PAGE.equals(key)) {
				showLastPage();
			} else if (GO_LEFT.equals(key)) {
				/* Depends on reading style */
				if (preferences.getReadingStyle() == Preferences.READING_LEFT_TO_RIGHT) {
					showPreviousPage();
				} else {
					showNextPage();
				}
			} else if (GO_RIGHT.equals(key)) {
				/* Depends on reading style */
				if (preferences.getReadingStyle() == Preferences.READING_LEFT_TO_RIGHT) {
					showNextPage();
				} else {
					showPreviousPage();
				}
			} else if (GO_UP.equals(key)) {
				updatePaintPosition(0, fixedScrollHeight());
				renderWrapper();
			} else if (GO_DOWN.equals(key)) {
				updatePaintPosition(0, -fixedScrollHeight());
				renderWrapper();
			} else if (SCROLL.equals(key)) {
				if (isScrollComplete()) {
					showNextPage();
				} else {
					int deltaX = 0;
					int deltaY = 0;
					if (preferences.getScrollPriority() == Preferences.SCROLL_PRIORITY_VERTICAL) {
						/*
						 * If we are not at the (bottom) end of the image (and we're not
						 * ignoring y), we go down. Otherwise, we move horizontally (either
						 * right or left depending on configuration).
						 */
						if (!isVerticalScrollComplete() && !ignoreY) {
							deltaY = -fixedScrollHeight();
						} else {
							/*
							 * We reset y (if needed) and move horizontally. deltaY must be
							 * brought back to the top of the screen. We know that we last
							 * painted at y = (displayHeight - imageHeight) so that's what we
							 * need to use as deltaY.
							 */
							if (!ignoreY) {
								deltaY = imageHeight - displayHeight;
							}
							if (preferences.getReadingStyle() == Preferences.READING_RIGHT_TO_LEFT) {
								deltaX = fixedScrollWidth();
							} else {
								deltaX = -fixedScrollWidth();
							}
						}
					} else {
						/*
						 * First horizontally, then downward (resetting horizontal
						 * position). If we're not at the far end of the image (depending on
						 * reading style) and we're not ignoring x movements, we move
						 * horizontally (depending on reading style). Otherwise, we go down
						 * resetting x.
						 */
						if (!isHorizontalScrollComplete() && !ignoreX) {
							if (preferences.getReadingStyle() == Preferences.READING_RIGHT_TO_LEFT) {
								deltaX = fixedScrollWidth();
							} else {
								deltaX = -fixedScrollWidth();
							}
						} else {
							/*
							 * Reset x and go down.
							 */
							if (!ignoreX) {
								if (preferences.getReadingStyle() == Preferences.READING_RIGHT_TO_LEFT) {
									/*
									 * We last painted at x = 0.
									 */
									deltaX = displayWidth - imageWidth;
								} else {
									/*
									 * We last painted at - (imageWidth - displayWidth)
									 */
									deltaX = imageWidth - displayWidth;
								}
							}
							deltaY = -fixedScrollHeight();
						}
					}
					updatePaintPosition(deltaX, deltaY);
					renderWrapper();
				}
			} // end-if: SCROLL
			else if (GO_TO_PAGE.equals(key)) {
				String s = (String) JOptionPane.showInputDialog(f, "Go to page...",
						"jMangaViewer", JOptionPane.PLAIN_MESSAGE, null, null,
						comicBook.getCurrentPageNumber() + "");
				try {
					int goToPage = Integer.parseInt(s);
					if (goToPage <= comicBook.getNumberOfPages()) {
						showPage(goToPage);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} // end-if: GO_TO_PAGE
		}

	}

	private class ScaleFactorAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public ScaleFactorAction(String action) {
			putValue(ACTION_KEY, action);
		}

		public void actionPerformed(ActionEvent e) {
			final String key = (String) getValue(ACTION_KEY);

			if (SCALE_WIDTH.equals(key)) {
				preferences.setScaleFactor(Preferences.SCALE_WIDTH);
			} else if (SCALE_HEIGHT.equals(key)) {
				preferences.setScaleFactor(Preferences.SCALE_HEIGHT);
			} else if (SCALE_ORIGINAL.equals(key)) {
				preferences.setScaleFactor(Preferences.SCALE_ORIGINAL);
			} else if (SCALE_WINDOW.equals(key)) {
				preferences.setScaleFactor(Preferences.SCALE_WINDOW);
			}
			load(comicBook.getCurrentPageURL());
			updatePaintPosition(-1, -1);
			renderWrapper();
		}
	}

	private class ScaleQualityAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public ScaleQualityAction(String action) {
			putValue(ACTION_KEY, action);
		}

		public void actionPerformed(ActionEvent e) {
			final String key = (String) getValue(ACTION_KEY);

			if (QUALITY_FAST.equals(key)) {
				preferences.setScaleQuality(Preferences.QUALITY_FAST);
			} else if (QUALITY_MEDIUM.equals(key)) {
				preferences.setScaleQuality(Preferences.QUALITY_MEDIUM);
			} else if (QUALITY_HIGH.equals(key)) {
				preferences.setScaleQuality(Preferences.QUALITY_HIGH);
			}
			load(comicBook.getCurrentPageURL());
			updatePaintPosition(-1, -1);
			renderWrapper();
		}
	}

	private class ReadingStyleAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public ReadingStyleAction(String action) {
			putValue(ACTION_KEY, action);
		}

		public void actionPerformed(ActionEvent e) {
			final String key = (String) getValue(ACTION_KEY);
			if (READING_STYLE_L2R.equals(key)) {
				preferences.setReadingStyle(Preferences.READING_LEFT_TO_RIGHT);
			} else if (READING_STYLE_R2L.equals(key)) {
				preferences.setReadingStyle(Preferences.READING_RIGHT_TO_LEFT);
			}
		}
	}

	private class ScrollPriorityAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public ScrollPriorityAction(String action) {
			putValue(ACTION_KEY, action);
		}

		public void actionPerformed(ActionEvent e) {
			final String key = (String) getValue(ACTION_KEY);
			if (SCROLL_PRIORITY_HORIZONTAL.equals(key)) {
				preferences.setScrollPriority(Preferences.SCROLL_PRIORITY_HORIZONTAL);
			} else if (SCROLL_PRIORITY_VERTICAL.equals(key)) {
				preferences.setScrollPriority(Preferences.SCROLL_PRIORITY_VERTICAL);
			}
		}
	}

	/*
	 * Constructor
	 */
	public Viewer2(ComicBook cb, Preferences preferences) {
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.ALL);
		ch.setFormatter(new PatternFormatter());
		log.addHandler(ch);
		log.setLevel(Level.ALL);

		this.comicBook = cb;
		this.preferences = preferences;

		this.isHiDPI = isHiDPI();

		setupFont();
		/* Keystrokes */
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), EXIT);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), EXIT);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), SHOW_HIDE);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), GO_RIGHT);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), GO_LEFT);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), GO_UP);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), GO_UP);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), GO_DOWN);
		getInputMap()
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), GO_DOWN);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0),
				SHOW_FIRST_PAGE);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0),
				SHOW_LAST_PAGE);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0), GO_TO_PAGE);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), SCROLL);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), SCALE_WIDTH);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), SCALE_HEIGHT);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_O, 0), SCALE_ORIGINAL);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), SCALE_WINDOW);

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), QUALITY_FAST);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0), QUALITY_MEDIUM);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0), QUALITY_HIGH);

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0),
				READING_STYLE_L2R);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0),
				READING_STYLE_R2L);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0),
				SCROLL_PRIORITY_VERTICAL);
		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0),
				SCROLL_PRIORITY_HORIZONTAL);

		getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), TOGGLE_OSD);

		/* Actions */
		getActionMap().put(EXIT, exitAction);
		getActionMap().put(SHOW_HIDE, showHideAction);

		getActionMap().put(GO_LEFT, new NavigateAction(GO_LEFT));
		getActionMap().put(GO_RIGHT, new NavigateAction(GO_RIGHT));
		getActionMap().put(GO_UP, new NavigateAction(GO_UP));
		getActionMap().put(GO_DOWN, new NavigateAction(GO_DOWN));
		getActionMap().put(GO_TO_PAGE, new NavigateAction(GO_TO_PAGE));
		getActionMap().put(SHOW_FIRST_PAGE, new NavigateAction(SHOW_FIRST_PAGE));
		getActionMap().put(SHOW_LAST_PAGE, new NavigateAction(SHOW_LAST_PAGE));
		getActionMap().put(SCROLL, new NavigateAction(SCROLL));

		getActionMap().put(SCALE_WIDTH, new ScaleFactorAction(SCALE_WIDTH));
		getActionMap().put(SCALE_HEIGHT, new ScaleFactorAction(SCALE_HEIGHT));
		getActionMap().put(SCALE_ORIGINAL, new ScaleFactorAction(SCALE_ORIGINAL));
		getActionMap().put(SCALE_WINDOW, new ScaleFactorAction(SCALE_WINDOW));

		getActionMap().put(QUALITY_FAST, new ScaleQualityAction(QUALITY_FAST));
		getActionMap().put(QUALITY_MEDIUM, new ScaleQualityAction(QUALITY_MEDIUM));
		getActionMap().put(QUALITY_HIGH, new ScaleQualityAction(QUALITY_HIGH));

		getActionMap().put(READING_STYLE_L2R,
				new ReadingStyleAction(READING_STYLE_L2R));
		getActionMap().put(READING_STYLE_R2L,
				new ReadingStyleAction(READING_STYLE_R2L));

		getActionMap().put(SCROLL_PRIORITY_HORIZONTAL,
				new ScrollPriorityAction(SCROLL_PRIORITY_HORIZONTAL));
		getActionMap().put(SCROLL_PRIORITY_VERTICAL,
				new ScrollPriorityAction(SCROLL_PRIORITY_VERTICAL));

		getActionMap().put(TOGGLE_OSD, toggleOSDAction);

		/*
		 * Mouse listeners
		 */
		this.addMouseMotionListener(new MouseMotionListener() {
			public void mouseDragged(MouseEvent e) {
				if (mouseLastCheck == 0) {
					mouseX = e.getX();
					mouseY = e.getY();
					mouseLastCheck = e.getWhen();
				} else {
					int _dx = 0, _dy = 0;
					if (e.getWhen() - mouseLastCheck > scrollPollingTime) {
						mouseLastCheck = e.getWhen();
						if (!ignoreX) {
							_dx = e.getX() - mouseX;
						}
						mouseX = e.getX();
						if (!ignoreY) {
							_dy = e.getY() - mouseY;
						}
						mouseY = e.getY();
						log.fine("detected mouse drag: dx = " + _dx + ", dy = " + _dy);
						updatePaintPosition(_dx, _dy);
						renderWrapper();
					}
				}
			}

			public void mouseMoved(MouseEvent e) {

			}
		});

		this.addMouseWheelListener(new MouseWheelListener() {
			int horizontalScrollAmount = 0, verticalScrollAmount = 0;
			long mouseLastCheckScrollX = 0, mouseLastCheckScrollY = 0;
			final int maskHorizontalScroll = MouseWheelEvent.SHIFT_DOWN_MASK;

			public void mouseWheelMoved(MouseWheelEvent e) {
				switch (e.getScrollType()) {
					case MouseWheelEvent.WHEEL_BLOCK_SCROLL: {
						log.severe("Unhandled scroll type: WHEEL_BLOCK_SCROLL; event="
								+ e.toString());
						break;
					}
					case MouseWheelEvent.WHEEL_UNIT_SCROLL: {
						log.severe(e.toString());
						if ((e.getModifiersEx() & maskHorizontalScroll) == maskHorizontalScroll) {
							if (System.currentTimeMillis() - mouseLastCheckScrollX >= scrollPollingTime) {
								updatePaintPosition(-horizontalScrollAmount, 0);
								horizontalScrollAmount = 0;
								mouseLastCheckScrollX = System.currentTimeMillis();
								renderWrapper();
							} else {
								horizontalScrollAmount += e.getScrollAmount()
										* e.getWheelRotation() * 20;
							}
						} else {
							if (System.currentTimeMillis() - mouseLastCheckScrollY >= scrollPollingTime) {
								updatePaintPosition(0, -verticalScrollAmount);
								verticalScrollAmount = 0;
								mouseLastCheckScrollY = System.currentTimeMillis();
								renderWrapper();
							} else {
								verticalScrollAmount += e.getScrollAmount()
										* e.getWheelRotation() * 20;
							}
						}
						break;
					}
				}
			}
		});
		display();
	}

	private void display() {
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice dev = env.getDefaultScreenDevice();
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setBackground(Color.darkGray);
		f.setResizable(false);
		f.setUndecorated(true);
		f.setIgnoreRepaint(true);
		f.setBackground(Color.BLACK);
		this.setIgnoreRepaint(true);
		this.setBackground(Color.PINK);
		f.add(this);
		// f.pack();
		dev.setFullScreenWindow(f);
		isFullScreen = true;
		device = dev;
		f.createBufferStrategy(NUMBER_OF_BUFFERS);

		displayWidth = (int) f.getBounds().getWidth();
		displayHeight = (int) f.getBounds().getHeight();
		showFirstPage();
	}

	protected void setupFont() {
		String[] fontNames = { "Calibri", "Tahoma", "Ubuntu", "Droid Sans",
				"SansSerif" };
		for (String fontName : fontNames) {
			Font f = new Font(fontName, Font.PLAIN, strokeWidth);
			if (!"Dialog".equals(f.getFamily())) {
				font = f;
				break;
			}
		}
	}

	private void load(URL imageURL) {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		long t0 = System.currentTimeMillis();
		log.fine("loading " + imageURL);
		screenImage = null;
		pLastPaint = null;
		pPaint = null;
		ignoreX = false;
		ignoreY = false;
		mouseLastCheck = 0;

		BufferedImage original = null;
		try {
			original = ImageIO.read(imageURL);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		int _imageWidth = original.getWidth();
		int _imageHeight = original.getHeight();
		log.fine("called Toolkit.#.getImage(), checking scale factor");
		Object hint = null;
		switch (preferences.getScaleQuality()) {
			case Preferences.QUALITY_FAST: {
				hint = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
				break;
			}
			case Preferences.QUALITY_MEDIUM: {
				hint = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
				break;
			}
			case Preferences.QUALITY_HIGH: {
				hint = RenderingHints.VALUE_INTERPOLATION_BICUBIC;
				break;
			}
		}
		switch (preferences.getScaleFactor()) {
			case Preferences.SCALE_HEIGHT: {
				log.fine("scaling to fit height of " + displayHeight);
				// screenImage = original.getScaledInstance(-1, displayHeight,
				// preferences.getScaleQuality());
				screenImage = getScaledInstance(original,
						(displayHeight * _imageWidth / _imageHeight), displayHeight, hint,
						false);
				break;
			}
			case Preferences.SCALE_WIDTH: {
				log.fine("scaling to fit width of " + displayWidth);
				// screenImage = original.getScaledInstance(displayWidth, -1,
				// preferences.getScaleQuality());
				screenImage = getScaledInstance(original, displayWidth, (displayWidth
						* _imageHeight / _imageWidth), hint, false);
				break;
			}
			case Preferences.SCALE_WINDOW: {
				log.fine("scaling to fit window, preparing original image to calculate dimensions");
				log.fine("image loaded: " + imageWidth + "x" + imageHeight);
				if (_imageWidth > _imageHeight) {
					log.fine("scaling to width");
					// screenImage = original.getScaledInstance(displayWidth, -1,
					// preferences.getScaleQuality());
					screenImage = getScaledInstance(original, displayWidth, (displayWidth
							* _imageHeight / _imageWidth), hint, false);
				} else {
					log.fine("scaling to height");
					// screenImage = original.getScaledInstance(-1, displayHeight,
					// preferences.getScaleQuality());
					screenImage = getScaledInstance(original, (displayHeight
							* _imageWidth / _imageHeight), displayHeight, hint, false);
				}
				break;
			}
			case Preferences.SCALE_ORIGINAL: {
				log.fine("not scaling due to original size request");
				// No action.
				screenImage = original;
				break;
			}
		}
		// screenImage is ready.
		imageWidth = screenImage.getWidth(this);
		imageHeight = screenImage.getHeight(this);
		log.fine("image loaded: " + imageWidth + "x" + imageHeight);

		int originalWidth = original.getWidth(this);
		log.fine("original width: " + originalWidth);
		zoomFactor = percFormat
				.format((double) imageWidth / (double) originalWidth);

		if (imageWidth <= displayWidth) {
			ignoreX = true;
		}
		if (imageHeight <= displayHeight) {
			ignoreY = true;
		}
		long t1 = System.currentTimeMillis();
		log.fine("total time to load image: " + (t1 - t0) + " ms");
		setCursor(Cursor.getDefaultCursor());
	}

	private synchronized void updatePaintPosition(int deltaX, int deltaY) {
		if (overlayThread != null) {
			log.fine("interrupting overlayThread due to image display change");
			overlayThread.interrupt();
		}
		if (pLastPaint == null) {
			log.fine("first display of image, deltaX=" + deltaX + ", deltaY="
					+ deltaY);
			/*
			 * First display of the image.
			 */
			int x = 0; // Default for READING_LEFT_TO_RIGHT.
			if (ignoreX) {
				x = (displayWidth - imageWidth) / 2;
			} else if (preferences.getReadingStyle() == Preferences.READING_RIGHT_TO_LEFT) {
				x = displayWidth - imageWidth;
			}

			int y = 0; // Always the right default.
			if (ignoreY) {
				y = (displayHeight - imageHeight) / 2;
			}
			pPaint = new Point(x, y);
			log.fine("calculated starting point " + pPaint);
		} else {
			log.fine("subsequent display of image, deltaX=" + deltaX + ", deltaY="
					+ deltaY);
			int x = pLastPaint.x;

			if (!ignoreX) {
				if (deltaX < 0 && (x + deltaX + imageWidth < displayWidth)) {
					/*
					 * Movement to the left: the right border of the image cannot be
					 * inside the screen.
					 */
					x = displayWidth - imageWidth;
				} else if (deltaX > 0 && (x + deltaX > 0)) {
					/*
					 * Movement to the right: x must be at most 0. the screen.
					 */
					x = 0;
				} else {
					x += deltaX;
				}
			}
			/*
			 * The same logic applies to vertical movements.
			 */
			int y = pLastPaint.y;
			if (!ignoreY) {
				if (deltaY < 0 && (y + deltaY + imageHeight < displayHeight)) {
					y = displayHeight - imageHeight;
				} else if (deltaY > 0 && (y + deltaY > 0)) {
					y = 0;
				} else {
					y += deltaY;
				}
			}
			pPaint = new Point(x, y);
			log.fine("calculated painting point " + pPaint);
		}

	}

	private void showFirstPage() {
		if (overlayThread != null) {
			log.fine("interrupting overlayThread due to page change");
			overlayThread.interrupt();
		}
		load(this.comicBook.getFirstPageURL());
		updatePaintPosition(-1, -1);
		renderWrapper();
	}

	private void showLastPage() {
		if (overlayThread != null) {
			log.fine("interrupting overlayThread due to page change");
			overlayThread.interrupt();
		}
		load(this.comicBook.getLastPageURL());
		updatePaintPosition(-1, -1);
		renderWrapper();
	}

	private void showPage(int page) {
		if (overlayThread != null) {
			log.fine("interrupting overlayThread due to page change");
			overlayThread.interrupt();
		}
		this.comicBook.getFirstPageURL();
		while (this.comicBook.getCurrentPageNumber() < page) {
			this.comicBook.getNextPageURL();
		}
		load(this.comicBook.getCurrentPageURL());
		updatePaintPosition(-1, -1);
		renderWrapper();
	}

	private void showNextPage() {
		if (this.comicBook.hasNextPage()) {
			if (overlayThread != null) {
				log.fine("interrupting overlayThread due to page change");
				overlayThread.interrupt();
			}
			load(this.comicBook.getNextPageURL());
			updatePaintPosition(-1, -1);
			renderWrapper();
		} else {
			if (overlayThread == null) {
				shape = endOfComicBookPath();
				dirty = true;
				renderWrapper();
				overlayThread = new Thread(new OverlayRunnable());
				overlayThread.start();
			}
		}
	}

	private void showPreviousPage() {
		if (this.comicBook.hasPreviousPage()) {
			if (overlayThread != null) {
				log.fine("interrupting overlayThread due to page change");
				overlayThread.interrupt();
			}
			load(this.comicBook.getPreviousPageURL());
			updatePaintPosition(-1, -1);
			// repaint();
			renderWrapper();
		} else {
			if (overlayThread == null) {
				shape = startOfComicBookPath();
				dirty = true;
				renderWrapper();
				overlayThread = new Thread(new OverlayRunnable());
				overlayThread.start();
			}
		}
	}

	/*
	 * 
	 */
	protected void renderWrapper() {
		BufferStrategy strategy = f.getBufferStrategy();
		Graphics g = strategy.getDrawGraphics();
		render(g);
		strategy.show();
		g.dispose();
	}

	protected void render(Graphics g) {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		if (screenImage != null) {
			// if (pPaint != null) {
			if (dirty) {
				log.fine("dirty flag is set, we'll repaint no matter what");
			}
			if (!(pPaint.equals(pLastPaint)) || dirty == true) {
				dirty = false;
				g.clearRect(0, 0, displayWidth, displayHeight);
				g.fillRect(0, 0, displayWidth, displayHeight);
				long original_t0 = System.currentTimeMillis();
				boolean complete = g.drawImage(screenImage, pPaint.x, pPaint.y, /* this */
						f);
				long original_t1 = System.currentTimeMillis();
				log.fine("time to draw original: " + (original_t1 - original_t0)
						+ " ms (complete? " + complete + ")");
				/*
				 * Shape and page info preparations.
				 */
				Graphics2D g2 = (Graphics2D) g;
				g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
						RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
				if (this.isHiDPI) {
					/*
					 * HiDPI ("retina") display upscaling.
					 */
					long upscaling_t0 = System.currentTimeMillis();
					switch (preferences.getScaleQuality()) {
						case Preferences.QUALITY_FAST: {
							g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
									RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
							break;
						}
						case Preferences.QUALITY_MEDIUM: {
							g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
									RenderingHints.VALUE_INTERPOLATION_BILINEAR);
							break;
						}
						case Preferences.QUALITY_HIGH: {
							g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
									RenderingHints.VALUE_INTERPOLATION_BICUBIC);
							break;
						}
					}
					complete = g2.drawImage(screenImage, pPaint.x, pPaint.y, imageWidth,
							imageHeight, f);
					long upscaling_t1 = System.currentTimeMillis();
					log.fine("time to draw upscaled: " + (upscaling_t1 - upscaling_t0)
							+ " ms (complete? " + complete + ")");
				}
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				/*
				 * OnScreenDisplay: page count and zoom factor.
				 */
				if (preferences.getOnScreenDisplay() == Preferences.ON_SCREEN_DISPLAY_ON) {
					FontRenderContext frc = g2.getFontRenderContext();

					TextLayout tlPageCount = new TextLayout(pageCountText(), font, frc);
					double pageCountBoxWidth = tlPageCount.getBounds().getWidth()
							+ strokeWidth * 0.8D;
					double pageCountBoxHeight = tlPageCount.getBounds().getHeight()
							+ strokeWidth * 0.8D;
					double pageCountBoxArc = strokeWidth;
					double pageCountBoxX = displayWidth - pageCountBoxWidth - strokeWidth;// strokeWidth;
					double pageCountBoxY = displayHeight - pageCountBoxHeight
							- strokeWidth; // strokeWidth;
					double pageCountX = pageCountBoxX + pageCountBoxWidth / 2
							- tlPageCount.getBounds().getWidth() / 2
							- tlPageCount.getBounds().getX();
					double pageCountY = pageCountBoxY + pageCountBoxHeight / 2
							- tlPageCount.getBounds().getHeight() / 2
							- tlPageCount.getBounds().getY();
					Shape boxPageCount = new RoundRectangle2D.Double(pageCountBoxX,
							pageCountBoxY, pageCountBoxWidth, pageCountBoxHeight,
							pageCountBoxArc, pageCountBoxArc);

					TextLayout tlZoomFactor = new TextLayout(zoomFactor, font, frc);
					double zoomFactorBoxWidth = tlZoomFactor.getBounds().getWidth()
							+ strokeWidth * 0.8D;
					double zoomFactorBoxHeight = tlZoomFactor.getBounds().getHeight()
							+ strokeWidth * 0.8D;
					double zoomFactorBoxArc = strokeWidth;
					double zoomFactorBoxX = displayWidth - zoomFactorBoxWidth
							- strokeWidth;
					double zoomFactorBoxY = strokeWidth;
					double zoomFactorX = zoomFactorBoxX + zoomFactorBoxWidth / 2
							- tlZoomFactor.getBounds().getWidth() / 2
							- tlZoomFactor.getBounds().getX();
					double zoomFactorY = zoomFactorBoxY + zoomFactorBoxHeight / 2
							- tlZoomFactor.getBounds().getHeight() / 2
							- tlZoomFactor.getBounds().getY();
					Shape boxZoomFactor = new RoundRectangle2D.Double(zoomFactorBoxX,
							zoomFactorBoxY, zoomFactorBoxWidth, zoomFactorBoxHeight,
							zoomFactorBoxArc, zoomFactorBoxArc);

					TextLayout tlFilename = new TextLayout(getCurrentImageName(), font,
							frc);
					double filenameBoxWidth = tlFilename.getBounds().getWidth()
							+ strokeWidth * 0.8D;
					double filenameBoxHeight = tlFilename.getBounds().getHeight()
							+ strokeWidth * 0.8D;
					double filenameBoxArc = strokeWidth;
					double filenameBoxX = strokeWidth;
					double filenameBoxY = displayHeight - pageCountBoxHeight
							- strokeWidth; // strokeWidth;
					double filenameX = filenameBoxX + filenameBoxWidth / 2
							- tlFilename.getBounds().getWidth() / 2
							- tlFilename.getBounds().getX();
					double filenameY = filenameBoxY + filenameBoxHeight / 2
							- tlFilename.getBounds().getHeight() / 2
							- tlFilename.getBounds().getY();
					Shape boxFilename = new RoundRectangle2D.Double(filenameBoxX,
							filenameBoxY, filenameBoxWidth, filenameBoxHeight,
							filenameBoxArc, filenameBoxArc);

					g2.setComposite(compositeTransparent);
					g2.setPaint(blackPaint);
					g2.fill(boxPageCount);
					g2.fill(boxZoomFactor);
					g2.fill(boxFilename);
					g2.setComposite(compositeOpaque);
					g2.setPaint(whitePaint);
					tlPageCount.draw(g2, (float) pageCountX, (float) pageCountY);
					tlZoomFactor.draw(g2, (float) zoomFactorX, (float) zoomFactorY);
					tlFilename.draw(g2, (float) filenameX, (float) filenameY);
				}
				if (shape != null) {
					/*
					 * Prepare a square background bounding box for the shape.
					 */
					int maxDimension = shape.getBounds().height > shape.getBounds().width ? shape
							.getBounds().height : shape.getBounds().width;
					int boundingBoxSize = maxDimension + strokeWidth * 6;
					Shape boundingBox = new RoundRectangle2D.Float(
							(displayWidth - boundingBoxSize) / 2,
							(displayHeight - boundingBoxSize) / 2, boundingBoxSize,
							boundingBoxSize, arcLength, arcLength);
					/*
					 * Set a transparent composite, then draw/fill the background box.
					 */
					g2.setComposite(compositeTransparent);
					g2.setPaint(blackPaint);
					g2.fill(boundingBox);
					/*
					 * Set a opaque composite, white paint, then draw and fill the shape.
					 */
					g2.setComposite(compositeOpaque);
					g2.setPaint(whitePaint);
					g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND,
							BasicStroke.JOIN_ROUND));

					AffineTransform center = AffineTransform.getTranslateInstance(
							(displayWidth - shape.getBounds().width) / 2,
							(displayHeight - shape.getBounds().height) / 2);
					shape.transform(center);
					g2.draw(shape);
					g2.fill(shape);
				}

				pLastPaint = new Point(pPaint);
			} else {
				log.fine("not repainting since position didn't change");
				g.clearRect(0, 0, displayWidth, displayHeight);
				g.drawImage(screenImage, pPaint.x, pPaint.y, this);
				pLastPaint = new Point(pPaint);
			}
			// } else {
			// log.fine("image is ready, but paint point isn't. skipping paint");
			// }
			// } else {
			// log.fine("image is not ready, skipping paint");
			// }
		} else {
			log.fine("image hasn't been set, skipping paint");
		}
		setCursor(Cursor.getDefaultCursor());
	}

	private String pageCountText() {
		String of = Integer.toString(this.comicBook.getNumberOfPages(), 10);
		NumberFormat nf = NumberFormat.getIntegerInstance();
		String pg = nf.format(this.comicBook.getCurrentPageNumber());
		boolean pad = false;
		if (pad) {
			// Pad with spaces:
			byte[] b = new byte[of.length()];
			Arrays.fill(b, 0, b.length, (byte) ' ');
			System
					.arraycopy(pg.getBytes(), 0, b, b.length - pg.length(), pg.length());
			pg = new String(b);
		}
		// TODO: move this to a resources file.
		String fmt = "pag. {0} of {1}";
		Object[] args = { pg, of };
		String text = MessageFormat.format(fmt, args);
		return text;
	}

	private GeneralPath endOfComicBookPath() {
		int u = Math.round(strokeWidth * 1.3F);

		GeneralPath p = new GeneralPath();
		/*
		 * Vertical line:
		 */
		p.moveTo(0, 0);
		p.lineTo(0, 6);
		/*
		 * Arrow:
		 */
		p.moveTo(2, 3);
		p.lineTo(4, 1);
		p.lineTo(4, 2);
		p.lineTo(6, 2);
		p.lineTo(6, 4);
		p.lineTo(4, 4);
		p.lineTo(4, 5);
		p.closePath();

		AffineTransform resize = AffineTransform.getScaleInstance(u, u);
		p.transform(resize);

		if (preferences.getReadingStyle() == Preferences.READING_LEFT_TO_RIGHT) {
			AffineTransform mirror = new AffineTransform(-1, 0, 0, 1,
					p.getBounds().width, 0);
			p.transform(mirror);
		}
		return p;
	}

	private GeneralPath startOfComicBookPath() {
		GeneralPath p = endOfComicBookPath();

		AffineTransform mirror = new AffineTransform(-1, 0, 0, 1,
				p.getBounds().width, 0);
		p.transform(mirror);

		return p;
	}

	private String getCurrentImageName() {
		URL u = this.comicBook.getCurrentPageURL();
		String s = u.toExternalForm();
		int lastSeparator = s.lastIndexOf('/');
		if (lastSeparator != -1) {
			s = s.substring(lastSeparator + 1, s.length());
		}
		return s;
	}

	@Override
	public void paint(Graphics g) {
		dirty = true;
		render(g);
	}

	protected boolean isHiDPI() {
		Properties p = System.getProperties();
		final String vendor = p.getProperty("java.vm.vendor");
		if (vendor != null) {
			if (vendor.indexOf("Apple") != -1) {
				return "2.0".equals(Toolkit.getDefaultToolkit().getDesktopProperty(
						"apple.awt.contentScaleFactor"));
			} else if (vendor.indexOf("Oracle") != -1) {
				GraphicsEnvironment env = GraphicsEnvironment
						.getLocalGraphicsEnvironment();
				final GraphicsDevice device = env.getDefaultScreenDevice();
				try {
					Field field = device.getClass().getDeclaredField("scale");

					if (field != null) {
						field.setAccessible(true);
						Object scale = field.get(device);

						if (scale instanceof Integer && ((Integer) scale).intValue() == 2) {
							return true;
						}
					}
				} catch (Exception ignore) {
				}
			}
		}
		return false;
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {

			public void run() {
				new Viewer2(null, new Preferences()).display();
			}
		});
	}

	/**
	 * Convenience method that returns a scaled instance of the provided
	 * {@code BufferedImage}.
	 * 
	 * @param img
	 *          the original image to be scaled
	 * @param targetWidth
	 *          the desired width of the scaled instance, in pixels
	 * @param targetHeight
	 *          the desired height of the scaled instance, in pixels
	 * @param hint
	 *          one of the rendering hints that corresponds to
	 *          {@code RenderingHints.KEY_INTERPOLATION} (e.g.
	 *          {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
	 *          {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
	 *          {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
	 * @param higherQuality
	 *          if true, this method will use a multi-step scaling technique that
	 *          provides higher quality than the usual one-step technique (only
	 *          useful in downscaling cases, where {@code targetWidth} or
	 *          {@code targetHeight} is smaller than the original dimensions, and
	 *          generally only when the {@code BILINEAR} hint is specified)
	 * @return a scaled version of the original {@code BufferedImage}
	 */
	public BufferedImage getScaledInstance(BufferedImage img, int targetWidth,
			int targetHeight, Object hint, boolean higherQuality) {
		int type = (img.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB
				: BufferedImage.TYPE_INT_ARGB;
		BufferedImage ret = (BufferedImage) img;
		int w, h;
		if (higherQuality) {
			// Use multi-step technique: start with original size, then
			// scale down in multiple passes with drawImage()
			// until the target size is reached
			w = img.getWidth();
			h = img.getHeight();
		} else {
			// Use one-step technique: scale directly from original
			// size to target size with a single drawImage() call
			w = targetWidth;
			h = targetHeight;
		}

		do {
			if (higherQuality && w > targetWidth) {
				w /= 2;
				if (w < targetWidth) {
					w = targetWidth;
				}
			}

			if (higherQuality && h > targetHeight) {
				h /= 2;
				if (h < targetHeight) {
					h = targetHeight;
				}
			}

			BufferedImage tmp = new BufferedImage(w, h, type);
			Graphics2D g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			g2.drawImage(ret, 0, 0, w, h, null);
			g2.dispose();

			ret = tmp;
		} while (w != targetWidth || h != targetHeight);

		return ret;
	}

	private int fixedScrollWidth() {
		return (this.getWidth() * 2) / 3;
	}

	private int fixedScrollHeight() {
		return (this.getHeight() * 2) / 3;
	}

	/**
	 * @return
	 */
	private boolean isScrollComplete() {
		return isHorizontalScrollComplete() && isVerticalScrollComplete();
	}

	/**
	 * @return
	 */
	private boolean isVerticalScrollComplete() {
		if (ignoreY) {
			/*
			 * Automatically complete.
			 */
			return true;
		}
		return (pLastPaint.y == displayHeight - imageHeight);
	}

	/**
	 * @return
	 */
	private boolean isHorizontalScrollComplete() {
		if (ignoreX) {
			/*
			 * Automatically complete.
			 */
			return true;
		}
		/*
		 * Slightly more complicated than vertical scroll.
		 */
		if (this.preferences.getReadingStyle() == Preferences.READING_RIGHT_TO_LEFT) {
			return (pLastPaint.x == 0);
		}
		if (this.preferences.getReadingStyle() == Preferences.READING_LEFT_TO_RIGHT) {
			return (pLastPaint.x == displayWidth - imageWidth);
		}
		return false;
	}

}