package it.sineo.jMangaViewer;

import it.sineo.jMangaViewer.util.PatternFormatter;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
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
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class Viewer extends JFrame {

	private final static Logger log = Logger.getLogger(Viewer.class
			.getCanonicalName());
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private JFrame myself;

	private final int maxLoadingTime = 6000;
	private final int loadingSleepTime = 500;
	private final int scrollPollingTime = 100;

	private ComicBook comicBook;
	private Preferences preferences;
	/*
	 * Mouse handling.
	 */
	long mouseLastCheck = 0;
	int mouseX, mouseY;

	/*
	 * Position and scrolling
	 */
	Point pPaint = null, pLastPaint = new Point(0, 0);
	boolean ignoreX = false, ignoreY = false;
	int displayWidth, displayHeight, imageWidth, imageHeight;
	Image screenImage;

	boolean dirty = true;
	boolean isHiDPI = false;
	/*
	 * Shapes
	 */
	private final int strokeWidth = 16;
	private final int arcLength = 80;
	private Thread overlayThread;
	private String zoomFactor;
	private NumberFormat percFormat = NumberFormat.getPercentInstance();

	/*
	 * Paint related objects.
	 */
	AlphaComposite compositeTransparent = AlphaComposite.getInstance(
			AlphaComposite.SRC_OVER, 0.6F);
	AlphaComposite compositeOpaque = AlphaComposite.getInstance(
			AlphaComposite.SRC_OVER, 1.0F);
	Paint blackPaint = Color.BLACK;
	Paint whitePaint = new Color(0xFF, 0xFF, 0xFF, 0xFF);
	Font font = new Font("SansSerif", Font.PLAIN, strokeWidth);

	public Viewer(ComicBook comicBook, Preferences preferences) {
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.ALL);
		ch.setFormatter(new PatternFormatter());
		log.addHandler(ch);
		log.setLevel(Level.ALL);

		this.comicBook = comicBook;
		this.preferences = preferences;

		this.isHiDPI = isHiDPI();

		setupListeners();
		myself = this;

		/*
		 * GUI initialization.
		 */
		this.setUndecorated(true);

		this.setVisible(true);

		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		gd.setFullScreenWindow(myself);
		/*
		 * OSX Lion and above require a workaround where we call setVisible(false)
		 * and then again setVisible(true) in order to catch keyboard events. Since
		 * it's not harmful to other platforms, we don't use a specialized if.
		 */
		myself.setVisible(false);
		myself.setVisible(true);

		this.setBackground(Color.BLACK);

		// getting display resolution: width and height
		displayWidth = this.getWidth();
		displayHeight = this.getHeight();
		log.info("Display resolution: " + displayWidth + "x" + displayHeight);

		showFirstPage();
	}

	private void load(URL imageURL) {
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		log.fine("loading " + imageURL);
		screenImage = null;
		pLastPaint = null;
		pPaint = null;
		ignoreX = false;
		ignoreY = false;
		mouseLastCheck = 0;

		boolean useImageIO = false;
		// screenImage = comicBook.getImage(imageURL);
		Image original = null;
		if (useImageIO) {
			try {
				original = ImageIO.read(imageURL);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		} else {
			original = Toolkit.getDefaultToolkit().createImage(imageURL);
		}
		prepareImage(original, this);
		// screenImage = Toolkit.getDefaultToolkit().createImage(imageURL);
		log.fine("called Toolkit.#.getImage(), checking scale factor");
		switch (preferences.getScaleFactor()) {
			case Preferences.SCALE_HEIGHT: {
				log.fine("scaling to fit height of " + displayHeight);
				screenImage = original.getScaledInstance(-1, displayHeight,
						preferences.getScaleQuality());
				break;
			}
			case Preferences.SCALE_WIDTH: {
				log.fine("scaling to fit width of " + displayWidth);
				screenImage = original.getScaledInstance(displayWidth, -1,
						preferences.getScaleQuality());
				break;
			}
			case Preferences.SCALE_WINDOW: {
				log.fine("scaling to fit window, preparing original image to calculate dimensions");
				prepareImage(original, this);
				long t0 = System.currentTimeMillis();
				while ((checkImage(original, this) & (ImageObserver.HEIGHT | ImageObserver.WIDTH)) == 0) {
					try {
						if (System.currentTimeMillis() - t0 > maxLoadingTime) {
							throw new InterruptedException(
									"Loading time exceeded, problems arised while loading "
											+ imageURL);
						}
						log.fine("image is not yet ready, going to sleep");
						Thread.sleep(loadingSleepTime);
					} catch (InterruptedException iex) {
						iex.printStackTrace();
						break;
					}
				} // end-while
				log.fine("image loaded: " + original.getWidth(this) + "x"
						+ original.getHeight(this));
				if (original.getWidth(this) > original.getHeight(this)) {
					log.fine("scaling to width");
					screenImage = original.getScaledInstance(displayWidth, -1,
							preferences.getScaleQuality());
				} else {
					log.fine("scaling to height");
					screenImage = original.getScaledInstance(-1, displayHeight,
							preferences.getScaleQuality());
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
		log.fine("preparing final image");
		prepareImage(screenImage, this);
		long t0 = System.currentTimeMillis();
		while ((checkImage(screenImage, this) & ImageObserver.ALLBITS) == 0) {
			try {
				if (System.currentTimeMillis() - t0 > maxLoadingTime) {
					throw new InterruptedException(
							"Loading time exceeded, problems arised while loading "
									+ imageURL);
				}
				log.fine("image not yet loaded, going to sleep");
				Thread.sleep(loadingSleepTime);
			} catch (InterruptedException iex) {
				iex.printStackTrace();
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

	private String getCurrentImageName() {
		URL u = this.comicBook.getCurrentPageURL();
		String s = u.toExternalForm();
		int lastSeparator = s.lastIndexOf(File.separatorChar);
		if (lastSeparator != -1) {
			s = s.substring(lastSeparator + 1, s.length());
		}
		return s;
	}

	public void paint(Graphics g) {
		if (screenImage != null) {
			if ((checkImage(screenImage, this) & ImageObserver.ALLBITS) > 0) {
				if (pPaint != null) {
					if (dirty) {
						log.fine("dirty flag is set, we'll repaint no matter what");
					}
					if (!(pPaint.equals(pLastPaint)) || dirty == true) {
						dirty = false;
						g.clearRect(0, 0, displayWidth, displayHeight);
						long original_t0 = System.currentTimeMillis();
						g.drawImage(screenImage, pPaint.x, pPaint.y, this);
						long original_t1 = System.currentTimeMillis();
						log.fine("time to draw original: " + (original_t1 - original_t0)
								+ " ms");
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
							g2.drawImage(screenImage, pPaint.x, pPaint.y, imageWidth,
									imageHeight, this);
							long upscaling_t1 = System.currentTimeMillis();
							log.fine("time to draw upscaled: "
									+ (upscaling_t1 - upscaling_t0) + " ms");
						}
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
								RenderingHints.VALUE_ANTIALIAS_ON);
						/*
						 * OnScreenDisplay: page count and zoom factor.
						 */
						if (preferences.getOnScreenDisplay() == Preferences.ON_SCREEN_DISPLAY_ON) {
							FontRenderContext frc = g2.getFontRenderContext();

							TextLayout tlPageCount = new TextLayout(pageCountText(), font,
									frc);
							double pageCountBoxWidth = tlPageCount.getBounds().getWidth()
									+ strokeWidth * 0.8D;
							double pageCountBoxHeight = tlPageCount.getBounds().getHeight()
									+ strokeWidth * 0.8D;
							double pageCountBoxArc = strokeWidth;
							double pageCountBoxX = displayWidth - pageCountBoxWidth
									- strokeWidth;// strokeWidth;
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

							TextLayout tlFilename = new TextLayout(getCurrentImageName(),
									font, frc);
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
							 * Set a opaque composite, white paint, then draw and fill the
							 * shape.
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
						// g.clearRect(0, 0, displayWidth, displayHeight);
						// g.drawImage(screenImage, pPaint.x, pPaint.y, this);
						// pLastPaint = new Point(pPaint);
					}
				} else {
					log.fine("image is ready, but paint point isn't. skipping paint");
				}
			} else {
				log.fine("image is not ready, skipping paint");
			}
		} else {
			log.fine("image hasn't been set, skipping paint");
		}
	}

	private GeneralPath shape = null;
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
			repaint();
		}
	}

	/**
	 *
	 */
	private void showNextPage() {
		if (this.comicBook.hasNextPage()) {
			if (overlayThread != null) {
				log.fine("interrupting overlayThread due to page change");
				overlayThread.interrupt();
			}
			load(this.comicBook.getNextPageURL());
			updatePaintPosition(-1, -1);
			repaint();
		} else {
			if (overlayThread == null) {
				shape = endOfComicBookPath();
				dirty = true;
				repaint();
				overlayThread = new Thread(new OverlayRunnable());
				overlayThread.start();
			}
		}
	}

	/**
	 *
	 */
	private void showPreviousPage() {
		if (this.comicBook.hasPreviousPage()) {
			if (overlayThread != null) {
				log.fine("interrupting overlayThread due to page change");
				overlayThread.interrupt();
			}
			load(this.comicBook.getPreviousPageURL());
			updatePaintPosition(-1, -1);
			repaint();
		} else {
			if (overlayThread == null) {
				shape = startOfComicBookPath();
				dirty = true;
				repaint();
				overlayThread = new Thread(new OverlayRunnable());
				overlayThread.start();
			}
		}
	}

	private void showFirstPage() {
		if (overlayThread != null) {
			log.fine("interrupting overlayThread due to page change");
			overlayThread.interrupt();
		}
		load(this.comicBook.getFirstPageURL());
		updatePaintPosition(-1, -1);
		repaint();
	}

	private void showLastPage() {
		if (overlayThread != null) {
			log.fine("interrupting overlayThread due to page change");
			overlayThread.interrupt();
		}
		load(this.comicBook.getLastPageURL());
		updatePaintPosition(-1, -1);
		repaint();
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
		repaint();
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

	private int fixedScrollWidth() {
		return (this.getWidth() * 2) / 3;
	}

	private int fixedScrollHeight() {
		return (this.getHeight() * 2) / 3;
	}

	private void setupListeners() {
		// Exiting program on window close
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				preferences.save();
				System.exit(0);
			}
		});

		addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() != MouseEvent.BUTTON1) {
					System.exit(0);
				} else {
					showNextPage();
				}
			}

			public void mousePressed(MouseEvent e) {
			}

			public void mouseReleased(MouseEvent e) {
				mouseLastCheck = 0;
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}
		});

		addMouseWheelListener(new MouseWheelListener() {
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
								repaint();
							} else {
								horizontalScrollAmount += e.getScrollAmount()
										* e.getWheelRotation();
							}
						} else {
							if (System.currentTimeMillis() - mouseLastCheckScrollY >= scrollPollingTime) {
								updatePaintPosition(0, -verticalScrollAmount);
								verticalScrollAmount = 0;
								mouseLastCheckScrollY = System.currentTimeMillis();
								repaint();
							} else {
								verticalScrollAmount += e.getScrollAmount()
										* e.getWheelRotation();
							}
						}
						break;
					}
				}
			}
		});

		addMouseMotionListener(new MouseMotionListener() {

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
						repaint();
					}
				}

			}

			public void mouseMoved(MouseEvent e) {

			}
		});

		addKeyListener(new KeyListener() {
			public void keyPressed(KeyEvent ke) {
				switch (ke.getKeyCode()) {
					case KeyEvent.VK_Q:
					case KeyEvent.VK_ESCAPE: {
						preferences.save();
						System.exit(0);
						break;
					}
					case KeyEvent.VK_PAGE_UP:
					case KeyEvent.VK_UP: {
						updatePaintPosition(0, fixedScrollHeight());
						repaint();
						break;
					}
					case KeyEvent.VK_PAGE_DOWN:
					case KeyEvent.VK_DOWN: {
						updatePaintPosition(0, -fixedScrollHeight());
						repaint();
						break;
					}
					case KeyEvent.VK_SPACE: {
						if (isScrollComplete()) {
							showNextPage();
						} else {
							int deltaX = 0;
							int deltaY = 0;
							if (preferences.getScrollPriority() == Preferences.SCROLL_PRIORITY_VERTICAL) {
								/*
								 * If we are not at the (bottom) end of the image (and we're not
								 * ignoring y), we go down. Otherwise, we move horizontally
								 * (either right or left depending on configuration).
								 */
								if (!isVerticalScrollComplete() && !ignoreY) {
									deltaY = -fixedScrollHeight();
								} else {
									/*
									 * We reset y (if needed) and move horizontally. deltaY must
									 * be brought back to the top of the screen. We know that we
									 * last painted at y = (displayHeight - imageHeight) so that's
									 * what we need to use as deltaY.
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
								 * position). If we're not at the far end of the image
								 * (depending on reading style) and we're not ignoring x
								 * movements, we move horizontally (depending on reading style).
								 * Otherwise, we go down resetting x.
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
							repaint();
						}
						break;
					}
					case KeyEvent.VK_RIGHT: {
						if (preferences.getReadingStyle() == Preferences.READING_RIGHT_TO_LEFT) {
							showPreviousPage();
						} else {
							showNextPage();
						}
						break;
					}
					case KeyEvent.VK_LEFT: {
						if (preferences.getReadingStyle() == Preferences.READING_RIGHT_TO_LEFT) {
							showNextPage();
						} else {
							showPreviousPage();
						}
						break;
					}
					case KeyEvent.VK_HOME: {
						/*
						 * Always first page, regardless of reading style.
						 */
						showFirstPage();
						break;
					}
					case KeyEvent.VK_END: {
						/*
						 * Always last page, regardless of reading style.
						 */
						showLastPage();
						break;
					}
					case KeyEvent.VK_W: {
						preferences.setScaleFactor(Preferences.SCALE_WIDTH);
						load(comicBook.getCurrentPageURL());
						updatePaintPosition(-1, -1);
						repaint();
						break;
					}
					case KeyEvent.VK_H: {
						preferences.setScaleFactor(Preferences.SCALE_HEIGHT);
						load(comicBook.getCurrentPageURL());
						updatePaintPosition(-1, -1);
						repaint();
						break;
					}
					case KeyEvent.VK_O: {
						preferences.setScaleFactor(Preferences.SCALE_ORIGINAL);
						load(comicBook.getCurrentPageURL());
						updatePaintPosition(-1, -1);
						repaint();
						break;
					}
					case KeyEvent.VK_B: {
						preferences.setScaleFactor(Preferences.SCALE_WINDOW);
						load(comicBook.getCurrentPageURL());
						updatePaintPosition(-1, -1);
						repaint();
						break;
					}
					/* Scaling quality */
					case KeyEvent.VK_1: {
						preferences.setScaleQuality(Preferences.QUALITY_FAST);
						load(comicBook.getCurrentPageURL());
						updatePaintPosition(-1, -1);
						repaint();
						break;
					}
					case KeyEvent.VK_2: {
						preferences.setScaleQuality(Preferences.QUALITY_MEDIUM);
						load(comicBook.getCurrentPageURL());
						updatePaintPosition(-1, -1);
						repaint();
						break;
					}
					case KeyEvent.VK_3: {
						preferences.setScaleQuality(Preferences.QUALITY_HIGH);
						load(comicBook.getCurrentPageURL());
						updatePaintPosition(-1, -1);
						repaint();
						break;
					}
					case KeyEvent.VK_L: {
						preferences.setReadingStyle(Preferences.READING_LEFT_TO_RIGHT);
						break;
					}
					case KeyEvent.VK_R: {
						preferences.setReadingStyle(Preferences.READING_RIGHT_TO_LEFT);
						break;
					}
					case KeyEvent.VK_N: {
						preferences.setScrollPriority(Preferences.SCROLL_PRIORITY_VERTICAL);
						break;
					}
					case KeyEvent.VK_Z: {
						preferences
								.setScrollPriority(Preferences.SCROLL_PRIORITY_HORIZONTAL);
						break;
					}
					case KeyEvent.VK_D: {
						if (preferences.getOnScreenDisplay() == Preferences.ON_SCREEN_DISPLAY_OFF) {
							preferences.setOnScreenDisplay(Preferences.ON_SCREEN_DISPLAY_ON);
						} else {
							preferences.setOnScreenDisplay(Preferences.ON_SCREEN_DISPLAY_OFF);
						}
						dirty = true;
						repaint();
						break;
					}
					case KeyEvent.VK_F: {
						GraphicsDevice gd = GraphicsEnvironment
								.getLocalGraphicsEnvironment().getDefaultScreenDevice();
						if (gd.getFullScreenWindow() != null
								&& gd.getFullScreenWindow().equals(myself)) {
							gd.setFullScreenWindow(null);
						} else {
							gd.setFullScreenWindow(myself);
							/*
							 * Fix for OSX Lion and above, losing keyboard events when
							 * switching to full screen.
							 */
							myself.setVisible(false);
							myself.setVisible(true);
							dirty = true;
							repaint();
						}
						break;
					}
					case KeyEvent.VK_P: {
						String s = (String) JOptionPane.showInputDialog(myself,
								"Go to page...", "Customized Dialog",
								JOptionPane.PLAIN_MESSAGE, null, null,
								comicBook.getCurrentPageNumber() + "");
						try {
							int goToPage = Integer.parseInt(s);
							if (comicBook.getNumberOfPages() < goToPage) {
								if (comicBook.getCurrentPageNumber() < goToPage) {
									showPage(goToPage);
								}
							}
						} catch (Exception ex) {
							ex.printStackTrace();
						}
						break;
					}
				}
			}

			public void keyReleased(KeyEvent ke) {
			}

			public void keyTyped(KeyEvent ke) {
			}
		});

		addFocusListener(new FocusListener() {

			public void focusGained(FocusEvent fe) {
			}

			public void focusLost(FocusEvent fe) {
				log.fine("setting dirty flag to true");
				dirty = true;
			}
		});
	}

	protected boolean isHiDPI() {
		Properties p = System.getProperties();
		final String vendor = p.getProperty("java.vm.vendor");
		if (vendor != null) {
			if (vendor.indexOf("Apple") != -1) {
				return "2.0".equals(Toolkit.getDefaultToolkit().getDesktopProperty(
						"apple.awt.contentScaleFactor"));
				/*
				try {
					final boolean[] isRetina = new boolean[1];
					new apple.awt.CImage.HiDPIScaledImage(1, 1,
							BufferedImage.TYPE_INT_ARGB) {
						public void drawIntoImage(BufferedImage image, float v) {
							isRetina[0] = v > 1;
						}
					};
					return isRetina[0];
				} catch (Throwable e) {
					e.printStackTrace();
					return false;
				}
				*/
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
}
