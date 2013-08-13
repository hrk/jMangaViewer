package it.sineo.jMangaViewer.util.osx;

import it.sineo.jMangaViewer.Launcher;

import javax.swing.JFrame;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;

public class OSXLauncher extends ApplicationAdapter {

	JFrame frame;
	long t0;

	private Runnable r = new Runnable() {
		public void run() {
			try {
				Thread.sleep(100);
				frame.dispose();
				String[] args = new String[0];
				Launcher.main(args);
			} catch (InterruptedException iex) {
				iex.printStackTrace();
			}
		}
	};
	private Thread thread = new Thread(r);

	public OSXLauncher(JFrame frame) {
		this.frame = frame;
		t0 = System.currentTimeMillis();
	}

	public void handleOpenFile(ApplicationEvent evt) {
		thread.interrupt();
		String args[] = { evt.getFilename() };
		Launcher.main(args);
		frame.dispose();
	}

	public void handleQuit(ApplicationEvent evt) {
		System.exit(0);
	}

	public void handleOpenApplication(ApplicationEvent evt) {
		thread.start();
	}

	public static void main(String[] args) {
		JFrame f = new JFrame("");

		OSXLauncher da = new OSXLauncher(f);
		Application app = new Application();
		app.addApplicationListener(da);

		f.setUndecorated(true);
		f.setVisible(true);
		f.validate();
	}
}
