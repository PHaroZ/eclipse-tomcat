package com.sysdeo.eclipse.tomcat;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

public class TomcatView extends ViewPart {

	private class Record {

		private String name;

		private boolean published;

		private TomcatProject tomcatProject;

		private String webPath;

		public Record(TomcatProject tomcatProject, String name, String webPath, boolean published) {
			super();
			this.tomcatProject = tomcatProject;
			this.name = name;
			this.webPath = webPath;
			this.published = published;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return this.name;
		}

		/**
		 * @return the tomcatProject
		 */
		public TomcatProject getTomcatProject() {
			return this.tomcatProject;
		}

		/**
		 * @return the webPath
		 */
		public String getWebPath() {
			return this.webPath;
		}

		/**
		 * @return the published
		 */
		public boolean isPublished() {
			return this.published;
		}

	}

	private class RecordList {

		List<Record> records;

		public RecordList() {
			this.records = new ArrayList<Record>();
		}

		public RecordList(List<Record> records) {
			this.records = records;
		}

		public void addRecord(Record record) {
			this.records.add(record);
		}

		public void clearRecords() {
			this.records.clear();
		}

		public List<Record> getRecords() {
			return this.records;
		}

		public Record[] getRecordsAsArray() {
			return this.records.toArray(new Record[this.records.size()]);
		}
	}

	private class ViewContentProvider implements IStructuredContentProvider {

		public void dispose() {
		}

		public Object[] getElements(Object inputElement) {
			return TomcatView.this.dataView.getRecordsAsArray();
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}

	private class ViewLabelProvider extends LabelProvider implements ITableLabelProvider, IFontProvider {

		public Image getColumnImage(Object element, int columnIndex) {
			return null;
		}

		public String getColumnText(Object element, int columnIndex) {
			String result = "";
			Record record = (Record) element;
			switch (columnIndex) {
				case 0:
					result = record.getName();
					break;
				case 1:
					result = record.getWebPath();
					break;
				case 2:
					result = record.isPublished() ? TomcatPluginResources.VIEW_TABLE_COLUMN2_VALUE_TRUE
							: TomcatPluginResources.VIEW_TABLE_COLUMN2_VALUE_FALSE;
					break;
				default:
					break;
			}
			return result;
		}

		public Font getFont(Object element) {
			Record record = (Record) element;
			Font font = null;
			if (record.isPublished()) {
				FontData fontData = new FontData();
				fontData.setStyle(SWT.BOLD);
				font = new Font(Display.getDefault(), fontData);
			}
			return font;

		}

	}

	private Action actionPublish;

	private Action actionReload;

	private Action actionUnpublish;

	private RecordList dataView;

	private TableViewer viewer;

	/**
	 * The constructor.
	 */
	public TomcatView() {
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	@Override
	public void createPartControl(Composite parent) {
		this.dataView = new RecordList();

		int style = SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION
				| SWT.HIDE_SELECTION | SWT.CENTER | SWT.VERTICAL;

		GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_BOTH);
		GridLayout gridLayout = new GridLayout(2, false);
		gridLayout.marginWidth = 4;

		Table table = new Table(parent, style);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setLayoutData(gridData);
		table.setLayout(gridLayout);
		table.select(0);

		TableColumn column0 = new TableColumn(table, SWT.LEFT, 0);
		column0.setWidth(200);
		column0.setText(TomcatPluginResources.VIEW_TABLE_COLUMN0_TEXT);
		column0.setToolTipText(TomcatPluginResources.VIEW_TABLE_COLUMN0_TOOLTIP);

		TableColumn column1 = new TableColumn(table, SWT.LEFT, 1);
		column1.setWidth(200);
		column1.setText(TomcatPluginResources.VIEW_TABLE_COLUMN1_TEXT);
		column1.setToolTipText(TomcatPluginResources.VIEW_TABLE_COLUMN1_TOOLTIP);

		TableColumn column2 = new TableColumn(table, SWT.LEFT, 2);
		column2.setWidth(60);
		column2.setText(TomcatPluginResources.VIEW_TABLE_COLUMN2_TEXT);
		column2.setToolTipText(TomcatPluginResources.VIEW_TABLE_COLUMN2_TOOLTIP);

		this.viewer = new TableViewer(table);
		this.viewer.setUseHashlookup(true);
		this.viewer.setContentProvider(new ViewContentProvider());
		this.viewer.setLabelProvider(new ViewLabelProvider());
		this.viewer.setInput(ResourcesPlugin.getWorkspace());

		this.makeActions();
		this.hookContextMenu();
		this.hookDoubleClickAction();
		this.contributeToActionBars();
		this.reloadData();
		this.listenProjectsChange();
	}

	public Display getDisplay() {
		try {
			return this.viewer.getTable().getParent().getDisplay();
		} catch (Throwable e) {
			return null;
		}
	}

	public void reloadData() {
		this.dataView.clearRecords();

		TomcatLauncherPlugin.getDefault().getContextsDir();

		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

		for (IProject project : projects) {
			if (project.isOpen()) {
				final TomcatProject tomcatProject = TomcatProject.create(project);
				if (tomcatProject != null) {
					this.dataView.addRecord(new Record(tomcatProject, project.getName(), tomcatProject.getWebPath(),
							tomcatProject.contextExists()));
				}
			}
		}

		this.viewer.refresh();
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		this.viewer.getControl().setFocus();
	}

	private void contributeToActionBars() {
		IActionBars bars = this.getViewSite().getActionBars();
		this.fillLocalPullDown(bars.getMenuManager());
		this.fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(this.actionReload);
		// manager.add(new Separator());
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(this.actionReload);
		// manager.add(new Separator());
	}

	private ImageDescriptor getImageDescriptor(String img) {
		try {
			URL url = new URL(TomcatLauncherPlugin.getDefault().getDescriptor().getInstallURL(), "icons"
					+ File.separator + img);
			return ImageDescriptor.createFromURL(url);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	private Record getSelectedRecord() {
		return this.dataView.getRecords().get(this.viewer.getTable().getSelectionIndex());

	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {

			public void menuAboutToShow(IMenuManager manager) {
				manager.add(TomcatView.this.actionPublish);

				Record record = TomcatView.this.getSelectedRecord();
				if (record.isPublished()) {
					manager.add(TomcatView.this.actionUnpublish);
				}
			}
		});
		Menu menu = menuMgr.createContextMenu(this.viewer.getControl());
		this.viewer.getControl().setMenu(menu);
		this.getSite().registerContextMenu(menuMgr, this.viewer);
	}

	private void hookDoubleClickAction() {
		this.viewer.addDoubleClickListener(new IDoubleClickListener() {

			public void doubleClick(DoubleClickEvent event) {
				Record record = TomcatView.this.getSelectedRecord();
				(record.isPublished() ? TomcatView.this.actionUnpublish : TomcatView.this.actionPublish).run();
			}
		});
	}

	private void listenProjectsChange() {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(new ProjectChangeListener(this),
				IResourceChangeEvent.POST_CHANGE);
	}

	private void makeActions() {
		this.actionReload = new Action() {

			@Override
			public void run() {
				TomcatView.this.reloadData();
			}
		};
		this.actionReload.setText(TomcatPluginResources.VIEW_ACTION_RELOAD_TEXT);
		this.actionReload.setToolTipText(TomcatPluginResources.VIEW_ACTION_RELOAD_TOOLTIP);
		this.actionReload.setImageDescriptor(this.getImageDescriptor("refresh.gif"));

		this.actionPublish = new Action() {

			@Override
			public void run() {
				Record record = TomcatView.this.getSelectedRecord();
				try {
					record.getTomcatProject().updateContext();
					TomcatView.this.reloadData();
				} catch (CoreException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		this.actionPublish.setText(TomcatPluginResources.VIEW_ACTION_PUBLISH_TEXT);

		this.actionUnpublish = new Action() {

			@Override
			public void run() {
				Record record = TomcatView.this.getSelectedRecord();
				try {
					if (record.isPublished()) {
						record.getTomcatProject().removeContext();
					}
					TomcatView.this.reloadData();
				} catch (CoreException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		this.actionUnpublish.setText(TomcatPluginResources.VIEW_ACTION_UNPUBLISH_TEXT);

	}
}
