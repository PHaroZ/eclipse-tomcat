package com.sysdeo.eclipse.tomcat;

/*
 * (c) Copyright Sysdeo SA 2001, 2002. All Rights Reserved.
 */

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.swt.widgets.Display;

public class ProjectChangeListener implements IResourceChangeListener {

	private TomcatView tomcatView;

	private boolean reloadPlanned = false;

	public ProjectChangeListener(TomcatView tomcatView) {
		super();
		this.tomcatView = tomcatView;
	}

	/*
	 * @see IResourceChangeListener#resourceChanged(IResourceChangeEvent)
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		if (null != event.getDelta()) {
			boolean reloadViewData = false;
			for (IResourceDelta resourceDelta : event.getDelta().getAffectedChildren()) {
				if (IProject.class.isAssignableFrom(resourceDelta.getResource().getClass())) {
					reloadViewData = true;
					break;
				}
			}

			if (reloadViewData) {
				if (!this.reloadPlanned) {
					if (null != ProjectChangeListener.this && null != ProjectChangeListener.this.tomcatView) {
						Display display = ProjectChangeListener.this.tomcatView.getDisplay();
						if (null != display) {
							this.reloadPlanned = true;
							display.timerExec(500, new Runnable() {

								public void run() {
									ProjectChangeListener.this.reloadPlanned = false;
									ProjectChangeListener.this.tomcatView.reloadData();
								}
							});
						} else {
						}
					}
				}
			}

		}
	}

}
