package com.sysdeo.eclipse.tomcat;

/*
 * (c) Copyright Sysdeo SA 2001, 2002. All Rights Reserved.
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.sysdeo.eclipse.tomcat.editors.ProjectListElement;

public class TomcatProject extends PlatformObject implements IProjectNature {

	private static final String extraBeginTag = "<!-- Extra info begin -->";

	private static final String extraEndTag = "<!-- Extra info end -->";

	private static final String KEY_EXPORTSOURCE = "exportSource";

	private static final String KEY_EXTRAINFO = "extraInfo";

	private static final String KEY_REDIRECTLOGGER = "redirectLogger";

	private static final String KEY_RELOADABLE = "reloadable";

	private static final String KEY_ROOTDIR = "rootDir";

	private static final String KEY_UPDATEXML = "updateXml";

	private static final String KEY_WARLOCATION = "warLocation";

	private static final String KEY_WEBCLASSPATH = WebClassPathEntries.TAG_NAME;

	private static final String KEY_WEBPATH = "webPath";

	// Persistence properties of projects
	private static final String PROPERTIES_FILENAME = ".tomcatplugin";

	// private static final QualifiedName QN_WEBPATH = new QualifiedName("TomcatProject", KEY_WEBPATH);
	// private static final QualifiedName QN_UPDATEXML = new QualifiedName("TomcatProject", KEY_UPDATEXML);
	// private static final QualifiedName QN_EXPORTSOURCE = new QualifiedName("TomcatProject",
	// KEY_EXPORTSOURCE);
	// private static final QualifiedName QN_RELOADABLE = new QualifiedName("TomcatProject", KEY_RELOADABLE);
	// private static final QualifiedName QN_REDIRECTLOGGER = new QualifiedName("TomcatProject",
	// KEY_REDIRECTLOGGER);
	// private static final QualifiedName QN_WARLOCATION = new QualifiedName("TomcatProject", KEY_WARLOCATION);
	// private static final QualifiedName QN_ROOTDIR = new QualifiedName("TomcatProject", KEY_ROOTDIR);

	static public void addTomcatNature(IJavaProject project) {
		try {
			JDTUtil.addNatureToProject(project.getProject(), TomcatLauncherPlugin.NATURE_ID);
		} catch (CoreException ex) {
			TomcatLauncherPlugin.log(ex.getMessage());
		}
	}

	/**
	 * Return a TomcatProject if this javaProject has the tomcat nature Return null if Project has not tomcat
	 * nature
	 */
	static public TomcatProject create(IJavaProject javaProject) {
		TomcatProject result = null;
		try {
			result = (TomcatProject) javaProject.getProject().getNature(TomcatLauncherPlugin.NATURE_ID);
			if (result != null) {
				result.setJavaProject(javaProject);
			}
		} catch (CoreException ex) {
			TomcatLauncherPlugin.log(ex.getMessage());
		}
		return result;
	}

	/**
	 * Return a TomcatProject if this Project has the tomcat nature Return null if Project has not tomcat nature
	 */
	static public TomcatProject create(IProject project) {

		IJavaProject javaProject = JavaCore.create(project);
		if (javaProject != null) {
			return TomcatProject.create(javaProject);
		} else {
			return null;
		}
	}

	static public void removeTomcatNature(IJavaProject project) {
		try {
			JDTUtil.removeNatureToProject(project.getProject(), TomcatLauncherPlugin.NATURE_ID);
		} catch (CoreException ex) {
			TomcatLauncherPlugin.log(ex.getMessage());
		}
	}

	protected boolean exportSource;

	protected String extraInfo = "";

	protected IJavaProject javaProject;

	/**
	 * The platform project this <code>TomcatProject</code> is based on
	 */
	protected IProject project;

	protected boolean redirectLogger = false;

	protected boolean reloadable = true;

	protected String rootDir = "";

	protected IFolder rootDirFolder;

	protected boolean updateXml;

	protected String warLocation = "";

	protected WebClassPathEntries webClassPathEntries;

	protected String webPath = "";

	/*
	 * Add servlet.jar and jasper.jar to project classpath
	 */
	public void addTomcatJarToProjectClasspath() throws CoreException {
		TomcatBootstrap tb = TomcatLauncherPlugin.getDefault().getTomcatBootstrap();

		IClasspathEntry[] entries = this.javaProject.getRawClasspath();
		List cp = new ArrayList(entries.length + 1);

		for (int i = 0; i < entries.length; i++) {
			IClasspathEntry entry = entries[i];
			if (!((entry.getEntryKind() == IClasspathEntry.CPE_VARIABLE) && (entry.getPath().toOSString()
					.startsWith(TomcatLauncherPlugin.getDefault().getTomcatIPath().toOSString())))) {
				cp.add(entry);
			}
		}

		cp.addAll(tb.getTomcatJars());

		this.javaProject.setRawClasspath((IClasspathEntry[]) cp.toArray(new IClasspathEntry[cp.size()]), null);
	}

	/*
	 * Add all jar in WEB-INF/lib to project classpath
	 */
	public void addWEBINFLibJarFilesToProjectClasspath() throws CoreException {
		IFolder libFolder = this.getWebInfFolder().getFolder("lib");
		IResource[] libFiles = libFolder.members();

		IClasspathEntry[] entries = this.javaProject.getRawClasspath();
		List cp = new ArrayList(entries.length + 1);
		cp.addAll(Arrays.asList(entries));

		for (int i = 0; i < libFiles.length; i++) {
			if ((libFiles[i].getType() == IResource.FILE)
					&& (libFiles[i].getFileExtension().equalsIgnoreCase("jar"))) {
				cp.add(JavaCore.newLibraryEntry(libFiles[i].getFullPath(), null, null));
			}
		}

		this.javaProject.setRawClasspath((IClasspathEntry[]) cp.toArray(new IClasspathEntry[cp.size()]), null);
	}

	/**
	 * Backup Tomcat server.xml file using the following algorithm : - Initial server.xml is backuped to
	 * server.xml.backup - Before updating server.xml create a copy named server.xml.old
	 */
	public void backupServerXML() throws CoreException, IOException {
		String backup = this.getServerXMLLocation() + ".backup";
		String old = this.getServerXMLLocation() + ".old";

		if (!this.getServerXML().exists()) {
			String msg = "Tomcat server.xml file is not found in " + this.getServerXML().getAbsolutePath();
			Status status = new Status(IStatus.ERROR, TomcatLauncherPlugin.getDefault().getDescriptor()
					.getUniqueIdentifier(), IStatus.ERROR, msg, null);
			throw new CoreException(status);
		}

		File backupFile = new File(backup);
		if (!backupFile.exists()) {
			FileUtil.copy(this.getServerXML(), backupFile);
		}

		FileUtil.copy(this.getServerXML(), new File(old));
	}

	public void clearDefaultSourceEntries() throws CoreException {
		IClasspathEntry[] entries = this.javaProject.getRawClasspath();
		List cp = new ArrayList(entries.length + 1);
		for (int i = 0; i < entries.length; i++) {
			if (entries[i].getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				cp.add(entries[i]);
			}
		}
		this.javaProject.setRawClasspath((IClasspathEntry[]) cp.toArray(new IClasspathEntry[cp.size()]), null);
	}

	/*
	 * @see IProjectNature#configure()
	 */
	public void configure() throws CoreException {
	}

	public boolean contextExists() {
		File contextFile = this.getContextFile();
		if (null != contextFile && contextFile.exists()) {
			return true;
		}

		if (TomcatLauncherPlugin.getDefault().getConfigMode().equals(TomcatLauncherPlugin.SERVERXML_MODE)) {
			try {
				if (this.contextExistsInXML(FileUtil.readTextFile(this.getServerXML()))) {
					return true;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return false;
	}

	public void createWEBINFFolder() throws CoreException {
		IFolder webinfFolder = this.getWebInfFolder();
		this.createFolder(webinfFolder);
		this.createFolder(webinfFolder.getFolder("classes"));
		this.createFolder(webinfFolder.getFolder("lib"));

		// Create .cvsignore for classes
		this.createFile(webinfFolder.getFile(".cvsignore"), "classes");
	}

	public void createWEBINFSrcFolder() throws CoreException {
		this.createFolder(this.getWebInfFolder().getFolder("src"));
	}

	public void createWorkFolder() throws CoreException {
		IFolder folderHandle = this.getWorkFolder();
		this.createFolder(folderHandle);

		String tomcatVersion = TomcatLauncherPlugin.getDefault().getTomcatVersion();
		if (tomcatVersion.equals(TomcatLauncherPlugin.TOMCAT_VERSION4)
				|| tomcatVersion.equals(TomcatLauncherPlugin.TOMCAT_VERSION41)) {
			folderHandle = folderHandle.getFolder("org");
			this.createFolder(folderHandle);
			folderHandle = folderHandle.getFolder("apache");
			this.createFolder(folderHandle);
			folderHandle = folderHandle.getFolder("jsp");
			this.createFolder(folderHandle);
		}

		// Add a .cvsignore file in work directory
		this.createFile(this.project.getFile(".cvsignore"), "work");

	}

	/*
	 * @see IProjectNature#deconfigure()
	 */
	public void deconfigure() throws CoreException {
	}

	public void exportToWar() throws IOException {

		File warFile = new File(this.getWarLocation());

		File directory = null;
		if (this.getRootDirFolder() == null) {
			directory = this.getProject().getLocation().toFile();
		} else {
			directory = this.getRootDirFolder().getLocation().toFile();
		}

		Zipper zipper = new TomcatProjectZipper(warFile, directory, this.getExportSource());
		zipper.zip();
	}

	/**
	 * Run all the steps to configure a JavaProject as a TomcatProject
	 */
	public void fullConfiguration() throws CoreException, IOException {
		if (!this.rootDir.equals("")) {
			this.initRootDirFolder(true);
		}

		this.addProjectToSourcePathPref();
		this.createWEBINFFolder();
		this.createWEBINFSrcFolder();
		this.createWorkFolder();

		this.addTomcatJarToProjectClasspath();
		this.addWEBINFLibJarFilesToProjectClasspath();

		this.clearDefaultSourceEntries();
		this.setClassesAsOutputFolder();
		if (this.classesContainsJavaFiles()) {
			this.setClassesAsSourceFolder();
		}

		this.setSrcAsSourceFolder();
		this.setWEBINFSrcAsSourceFolder();
		this.setWorkAsSourceFolder();

		this.updateContext();
	}

	/**
	 * Gets the updateXml.
	 * 
	 * @return Returns a boolean
	 */
	public boolean getExportSource() {
		return new Boolean(this.readProperty(TomcatProject.KEY_EXPORTSOURCE)).booleanValue();
	}

	/**
	 * Gets the warfile.
	 * 
	 * @return Returns a String
	 */
	public String getExtraInfo() {
		return URLDecoder.decode(this.readProperty(TomcatProject.KEY_EXTRAINFO));
	}

	/*
	 * @see IProjectNature#getProject()
	 */
	public IJavaProject getJavaProject() {
		return this.javaProject;
	}

	/**
	 * Gets the project.
	 * 
	 * @return Returns a IProject
	 */
	public IProject getProject() {
		return this.project;
	}

	/**
	 * Gets the reloadable
	 * 
	 * @return Returns a boolean
	 */
	public boolean getRedirectLogger() {
		String redirectLoggerProperty = this.readProperty(TomcatProject.KEY_REDIRECTLOGGER);
		// Set default value to false
		if (redirectLoggerProperty.equals("")) {
			redirectLoggerProperty = "false";
		}
		return new Boolean(redirectLoggerProperty).booleanValue();
	}

	/**
	 * Gets the reloadable
	 * 
	 * @return Returns a boolean
	 */
	public boolean getReloadable() {
		String reloadableProperty = this.readProperty(TomcatProject.KEY_RELOADABLE);
		// Set default value to true
		if (reloadableProperty.equals("")) {
			reloadableProperty = "true";
		}
		return new Boolean(reloadableProperty).booleanValue();
	}

	/**
	 * Gets the rootDir.
	 * 
	 * @return Returns a String
	 */
	public String getRootDir() {
		return this.readProperty(TomcatProject.KEY_ROOTDIR);
	}

	public IFolder getRootDirFolder() {
		if (this.rootDirFolder == null) {
			this.initRootDirFolder(false);
		}
		return this.rootDirFolder;
	}

	/**
	 * Gets the updateXml.
	 * 
	 * @return Returns a boolean
	 */
	public boolean getUpdateXml() {
		return new Boolean(this.readProperty(TomcatProject.KEY_UPDATEXML)).booleanValue();
	}

	/**
	 * Gets the warfile.
	 * 
	 * @return Returns a String
	 */
	public String getWarLocation() {
		return this.readProperty(TomcatProject.KEY_WARLOCATION);
	}

	/**
	 * return the webclasspath entries
	 */
	public WebClassPathEntries getWebClassPathEntries() {
		try {
			return WebClassPathEntries.xmlUnmarshal(FileUtil.readTextFile(this.getPropertiesFile()));
		} catch (IOException ioEx) {
			return null;
		}
	}

	public IFolder getWebInfFolder() {
		if (this.getRootDirFolder() == null) {
			return this.project.getFolder("WEB-INF");
		} else {
			return this.getRootDirFolder().getFolder("WEB-INF");
		}
	}

	/**
	 * Gets the webpath.
	 * 
	 * @return Returns a String
	 */
	public String getWebPath() {
		return this.readProperty(TomcatProject.KEY_WEBPATH);
	}

	public IFolder getWorkFolder() {
		// if (getRootDirFolder() == null) {
		// return project.getFolder("work");
		// } else {
		// return getRootDirFolder().getFolder("work");
		// }

		return this.project.getFolder("work");

	}

	public void removeContext() throws CoreException, IOException {
		// Always call removeContext file because Tomcat create it automatically when using server.xml
		this.removeContextFile();

		if (TomcatLauncherPlugin.getDefault().getConfigMode().equals(TomcatLauncherPlugin.SERVERXML_MODE)) {
			this.removeContextInServerXML();
		}
	}

	/*
	 * Store exportSource in project persistent properties
	 */
	public void saveProperties() {
		try {
			StringBuffer fileContent = new StringBuffer();
			fileContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			fileContent.append("<tomcatProjectProperties>\n");
			fileContent.append("    <rootDir>" + this.rootDir + "</rootDir>\n");
			fileContent.append("    <exportSource>" + this.exportSource + "</exportSource>\n");
			fileContent.append("    <reloadable>" + this.reloadable + "</reloadable>\n");
			fileContent.append("    <redirectLogger>" + this.redirectLogger + "</redirectLogger>\n");
			fileContent.append("    <updateXml>" + this.updateXml + "</updateXml>\n");
			fileContent.append("    <warLocation>" + this.warLocation + "</warLocation>\n");
			fileContent.append("    <extraInfo>" + URLEncoder.encode(this.extraInfo) + "</extraInfo>\n");
			fileContent.append("    <webPath>" + this.webPath + "</webPath>\n");
			if (this.webClassPathEntries != null) {
				fileContent.append(this.webClassPathEntries.xmlMarshal(4));
			}
			fileContent.append("</tomcatProjectProperties>\n");
			FileUtil.toTextFile(this.getPropertiesFile(), fileContent.toString());
			// refresh the project files.
			this.project.refreshLocal(IResource.DEPTH_ONE, null);

			// Notification for VCM (Team plug-in)
			IFile propertiesIFile = this.getProject().getFile(TomcatProject.PROPERTIES_FILENAME);
			ResourcesPlugin.getWorkspace().validateEdit(new IFile[] { propertiesIFile }, null);

		} catch (Exception ex) {
			TomcatLauncherPlugin.log(ex.getMessage());
		}
	}

	public void setClassesAsOutputFolder() throws CoreException {
		IFolder classesFolder = this.getWebInfFolder().getFolder("classes");
		this.javaProject.setOutputLocation(classesFolder.getFullPath(), null);
	}

	public void setClassesAsSourceFolder() throws CoreException {
		IFolder classesFolder = this.getWebInfFolder().getFolder("classes");
		this.setFolderAsSourceEntry(classesFolder, null);
	}

	/**
	 * Sets the exportSource.
	 * 
	 * @param exportSource
	 *          The exportSource to set
	 */
	public void setExportSource(boolean exportSource) {
		this.exportSource = exportSource;
	}

	/**
	 * Sets the warfile
	 * 
	 * @param warfile
	 *          The warfile to set
	 */
	public void setExtraInfo(String extra) {
		this.extraInfo = extra;
	}

	/*
	 * @see IProjectNature#setProject(IProject)
	 */
	public void setJavaProject(IJavaProject javaProject) {
		this.javaProject = javaProject;
		this.setProject(javaProject.getProject());
	}

	/**
	 * Sets the project.
	 * 
	 * @param project
	 *          The project to set
	 */
	public void setProject(IProject project) {
		this.project = project;
	}

	/**
	 * Sets the reloadable
	 * 
	 * @param reloadable
	 *          The reloadable to set
	 */
	public void setRedirectLogger(boolean redirectLogger) {
		this.redirectLogger = redirectLogger;
	}

	/**
	 * Sets the reloadable
	 * 
	 * @param reloadable
	 *          The reloadable to set
	 */
	public void setReloadable(boolean reloadable) {
		this.reloadable = reloadable;
	}

	/**
	 * Sets the rootDir.
	 * 
	 * @param rootDir
	 *          The rootDir to set
	 */
	public void setRootDir(String rd) {
		this.rootDir = rd;
		this.rootDirFolder = null;
	}

	public void setSrcAsSourceFolder() throws CoreException {
		// this.setFolderAsSourceEntry(project.getFolder("src"));
	}

	/**
	 * Sets the updateXml.
	 * 
	 * @param updateXml
	 *          The updateXml to set
	 */
	public void setUpdateXml(boolean updateXml) {
		this.updateXml = updateXml;
	}

	/**
	 * Sets the warfile
	 * 
	 * @param warfile
	 *          The warfile to set
	 */
	public void setWarLocation(String wl) {
		this.warLocation = wl;
	}

	/**
	 * set the classpath entries which shall be loaded by the webclassloader
	 * 
	 * @param entries
	 *          List of WebClasspathEntry objects
	 */
	public void setWebClassPathEntries(WebClassPathEntries entries) {
		this.webClassPathEntries = entries;
	}

	public void setWEBINFSrcAsSourceFolder() throws CoreException {
		this.setFolderAsSourceEntry(this.getWebInfFolder().getFolder("src"), null);
	}

	/**
	 * Sets the webpath.
	 * 
	 * @param webpath
	 *          The webpath to set
	 */
	public void setWebPath(String wp) {
		this.webPath = wp;
	}

	public void setWorkAsSourceFolder() throws CoreException {
		this.setFolderAsSourceEntry(this.getWorkFolder(), this.getWorkFolder());
	}

	/**
	 * Add or update a Context definition
	 */
	public void updateContext() throws CoreException, IOException {
		if (TomcatLauncherPlugin.getDefault().getConfigMode().equals(TomcatLauncherPlugin.SERVERXML_MODE)) {
			this.updateServerXML();
		} else {
			this.updateContextFile();
		}
	}

	public void updateWebPath(String newWebPath) throws Exception {
		this.setWebPath(newWebPath);
		if (!newWebPath.equals(this.getWebPath())) {
			if (this.getUpdateXml()) {
				this.removeContext();
			}
		}
	}

	private void addContextToServerXML() throws IOException {
		String xml = FileUtil.readTextFile(this.getServerXML());
		String tag = TomcatLauncherPlugin.getDefault().getTomcatBootstrap().getXMLTagAfterContextDefinition();

		int tagIndex = xml.indexOf(tag);
		int insertIndex = (xml.substring(0, tagIndex)).lastIndexOf('\n');

		StringBuffer out = new StringBuffer(xml.substring(0, insertIndex));
		out.append(this.createContextDefinition());
		out.append(xml.substring(insertIndex, xml.length()));

		FileUtil.toTextFile(this.getServerXML(), out.toString());
	}

	private String addDocBaseToContext(String context) {
		int reloadableIndex = context.indexOf(this.getContextReloadable());
		int firstDoubleQuoteIndex = context.indexOf('"', reloadableIndex) + 1;
		int docBaseIndex = context.indexOf('"', firstDoubleQuoteIndex) + 1;
		StringBuffer out = new StringBuffer(context.substring(0, docBaseIndex));
		out.append(' ');
		out.append(this.getContextDocBase());
		out.append(' ');
		out.append(context.substring(docBaseIndex, context.length()));

		return out.toString();
	}

	private String addExtraInfoToContext(String context) {
		context = this.formatContextEndTag(context);
		int endContextStartTagIndex = context.indexOf(">");
		int extraInfoIndex = endContextStartTagIndex + 1;
		StringBuffer out = new StringBuffer(context.substring(0, extraInfoIndex));
		out.append('\n');
		out.append(TomcatProject.extraBeginTag);
		out.append('\n');
		out.append(this.getExtraInfo());
		out.append('\n');
		out.append(TomcatProject.extraEndTag);
		out.append(context.substring(extraInfoIndex, context.length()));

		return out.toString();
	}

	private String addLoaderToContext(String context) {
		context = this.formatContextEndTag(context);
		int endContextStartTagIndex = context.indexOf(">");
		int loaderIndex = endContextStartTagIndex + 1;
		StringBuffer out = new StringBuffer(context.substring(0, loaderIndex));
		out.append(this.getContextWebAppClassLoader());
		out.append(context.substring(loaderIndex, context.length()));

		return out.toString();
	}

	private String addLoggerToContext(String context) {
		context = this.formatContextEndTag(context);
		int endContextStartTagIndex = context.indexOf(">");
		int loggerIndex = endContextStartTagIndex + 1;
		StringBuffer out = new StringBuffer(context.substring(0, loggerIndex));
		out.append(this.getContextLogger());
		out.append(context.substring(loggerIndex, context.length()));

		return out.toString();
	}

	/*
	 * A new Tomcat project should be checked by default in source path preference page
	 */
	private void addProjectToSourcePathPref() {
		List projects = TomcatLauncherPlugin.getDefault().getProjectsInSourcePath();
		ProjectListElement ple = new ProjectListElement(this.getProject());
		if (!projects.contains(ple)) {
			projects.add(ple);
			TomcatLauncherPlugin.getDefault().setProjectsInSourcePath(projects);
		}
	}

	private String addReloadableToContext(String context) {
		int pathIndex = context.indexOf(this.getContextPath());
		int firstDoubleQuoteIndex = context.indexOf('"', pathIndex) + 1;
		int reloadableIndex = context.indexOf('"', firstDoubleQuoteIndex) + 1;

		StringBuffer out = new StringBuffer(context.substring(0, reloadableIndex));
		out.append(' ');
		out.append(this.getContextReloadable());
		out.append(' ');
		out.append(context.substring(reloadableIndex, context.length()));

		return out.toString();
	}

	private String addWorkToContext(String context) {
		int docBaseIndex = context.indexOf("docBase");
		int firstDoubleQuoteIndex = context.indexOf('"', docBaseIndex) + 1;
		int workIndex = context.indexOf('"', firstDoubleQuoteIndex) + 1;
		StringBuffer out = new StringBuffer(context.substring(0, workIndex));
		out.append(' ');
		out.append(this.getContextWorkDir());
		out.append(' ');
		out.append(context.substring(workIndex, context.length()));

		return out.toString();
	}

	/*
	 * if WEB-INF classes contains Java files add it to source folders Otherwise Eclipse will delete all those
	 * files
	 */
	private boolean classesContainsJavaFiles() {
		IFolder webinfFolder = this.getWebInfFolder();
		IFolder classesFolder = webinfFolder.getFolder("classes");
		File f = classesFolder.getLocation().toFile();

		if (!f.exists()) {
			return false;
		}
		if (!f.isDirectory()) {
			return false;
		}

		return FileUtil.dirContainsFiles(f, "java", true);
	}

	/**
	 * Quick and dirty implementations : using an XML Parser would be better
	 */
	private boolean contextExistsInXML(String xml) throws IOException {
		return (this.getContextTagIndex(xml) != -1);
	}

	private String createContextDefinition() {
		StringBuffer contextBuffer = new StringBuffer();
		contextBuffer.append(this.getContextStartTag());
		contextBuffer.append(' ');
		contextBuffer.append(this.getContextPath());
		contextBuffer.append(' ');
		contextBuffer.append(this.getContextReloadable());
		contextBuffer.append(' ');
		contextBuffer.append(this.getContextDocBase());
		contextBuffer.append(' ');
		contextBuffer.append(this.getContextWorkDir());
		contextBuffer.append(" />\n");

		String context = contextBuffer.toString();
		if (this.getWebClassPathEntries() != null) {
			context = this.addLoaderToContext(context);
		}
		if (this.getRedirectLogger()) {
			context = this.addLoggerToContext(context);
		}
		if (!(this.getExtraInfo().equals(""))) {
			context = this.addExtraInfoToContext(context);
		}

		return context;

	}

	private void createFile(IFile fileHandle, String content) throws CoreException {
		try {
			fileHandle.create(new ByteArrayInputStream(content.getBytes()), 0, null);
		} catch (CoreException e) {
			// If the file already existed locally, just refresh to get contents
			if (e.getStatus().getCode() == IResourceStatus.PATH_OCCUPIED) {
				fileHandle.refreshLocal(IResource.DEPTH_INFINITE, null); // new SubProgressMonitor(monitor, 500));
			} else {
				throw e;
			}
		}
	}

	private void createFolder(IFolder folderHandle) throws CoreException {
		try {
			// Create the folder resource in the workspace
			folderHandle.create(false, true, null); // new SubProgressMonitor(monitor, 500));
		} catch (CoreException e) {
			// If the folder already existed locally, just refresh to get contents
			if (e.getStatus().getCode() == IResourceStatus.PATH_OCCUPIED) {
				folderHandle.refreshLocal(IResource.DEPTH_INFINITE, null); // new SubProgressMonitor(monitor, 500));
			} else {
				throw e;
			}
		}
	}

	// Add </Context> instead of />, and \n if needed
	private String formatContextEndTag(String context) {
		int endContextStartTagIndex = context.indexOf(">");
		StringBuffer newContext = new StringBuffer();
		if (context.charAt(endContextStartTagIndex - 1) == '/') {
			newContext.append(context.substring(0, endContextStartTagIndex - 1));
			newContext.append(">");
			newContext.append("\n");
			newContext.append(this.getContextEndTag());
			newContext.append("\n");
			context = newContext.toString();
			endContextStartTagIndex--;
		} else {
			int endContextTagIndex = context.indexOf(this.getContextEndTag());
			if (context.charAt(endContextTagIndex - 1) != '\n') {
				newContext.append(context.substring(0, endContextTagIndex));
				newContext.append("\n");
				newContext.append(context.substring(endContextTagIndex));
			} else {
				return context;
			}
		}

		return newContext.toString();
	}

	private String getContextDocBase() {
		String docBaseLocation = "";
		if (this.getRootDirFolder() == null) {
			docBaseLocation = this.project.getLocation().toOSString();
		} else {
			docBaseLocation = this.getRootDirFolder().getLocation().toOSString();
		}
		return ("docBase=" + '"' + docBaseLocation + '"');
	}

	private String getContextEndTag() {
		return "</Context>";

	}

	private File getContextFile() {
		File contextFile = new File(TomcatLauncherPlugin.getDefault().getContextsDir() + File.separator
				+ this.getContextFileName());
		return contextFile;
	}

	private String getContextFileName() {
		String contextFileName = this.getWebPath();
		if (contextFileName.startsWith("/")) {
			contextFileName = contextFileName.substring(1);
		}

		// Tomcat 5 converts / to # in context file name
		contextFileName = contextFileName.replace('/', '#');

		return contextFileName + ".xml";
	}

	private String getContextLogger() {
		return "\n\t<Logger className=\"org.apache.catalina.logger.SystemOutLogger\" verbosity=\"4\" timestamp=\"true\"/>";
	}

	private String getContextPath() {
		return ("path=" + '"' + this.getWebPath() + '"');
	}

	private String getContextReloadable() {
		return ("reloadable=" + '"' + this.getReloadable() + '"');
	}

	private String getContextStartTag() {
		return ("<Context");
	}

	private int getContextTagIndex(String xml) throws IOException {
		int pathIndex = xml.indexOf(this.getContextPath());

		if (pathIndex == -1) {
			return -1;
		}

		int tagIndex = (xml.substring(0, pathIndex)).lastIndexOf('<');
		String tag = xml.substring(tagIndex, tagIndex + 8);
		if (!tag.equalsIgnoreCase(this.getContextStartTag())) {
			return -1;
		}

		return tagIndex;
	}

	private String getContextWebAppClassLoader() {
		return "\n\t<Loader className=\"org.apache.catalina.loader.DevLoader\" reloadable=\"true\" debug=\"1\" useSystemClassLoaderAsParent=\"false\" />";
	}

	private String getContextWorkDir() {
		String workFolderLocation = this.getWorkFolder().getLocation().toOSString();
		return (TomcatLauncherPlugin.getDefault().getTomcatBootstrap().getContextWorkDir(workFolderLocation));
	}

	private File getPropertiesFile() {
		return (this.getProject().getLocation().append(TomcatProject.PROPERTIES_FILENAME).toFile());
	}

	private File getServerXML() {
		return new File(this.getServerXMLLocation());
	}

	private String getServerXMLLocation() {
		return TomcatLauncherPlugin.getDefault().getConfigFile();
	}

	private void initRootDirFolder(boolean create) {
		StringTokenizer tokenizer = new StringTokenizer(this.getRootDir(), "/\\:");
		IFolder folder = null;
		try {
			while (tokenizer.hasMoreTokens()) {
				String each = tokenizer.nextToken();
				if (folder == null) {
					folder = this.project.getFolder(each);
				} else {
					folder = folder.getFolder(each);
				}
				if (create) {
					this.createFolder(folder);
				}
			}
		} catch (CoreException ex) {
			TomcatLauncherPlugin.log(ex);
			folder = null;
			this.setRootDir("/");
		}
		this.rootDirFolder = folder;

	}

	private String readProperty(String key) {
		String result = null;
		try {
			result = FileUtil.readPropertyInXMLFile(this.getPropertiesFile(), key);
		} catch (IOException e) {
			try {
				result = this.getJavaProject().getCorrespondingResource()
						.getPersistentProperty(new QualifiedName("TomcatProject", key));
			} catch (Exception e2) {
				TomcatLauncherPlugin.log(e2);
			}
		}

		if (result == null) {
			result = "";
		}

		return result;
	}

	private void removeContextFile() {
		this.getContextFile().delete();
	}

	private void removeContextInServerXML() throws CoreException, IOException {
		this.backupServerXML();

		String xml = FileUtil.readTextFile(this.getServerXML());
		if (this.contextExistsInXML(xml)) {
			int contextTagIdx = this.getContextTagIndex(xml);
			int endTagIndex = xml.indexOf("</Context>", contextTagIdx);
			boolean hasNoBody = false;
			if (endTagIndex < 0) {
				endTagIndex = xml.indexOf('>', contextTagIdx);
				hasNoBody = true;
			} else {
				endTagIndex += "</Context>".length();
			}

			StringBuffer out = null;

			out = new StringBuffer(xml.substring(0, contextTagIdx));
			out.append(xml.substring(endTagIndex + 1, xml.length()));

			if (out != null) {
				FileUtil.toTextFile(this.getServerXML(), out.toString());
			}

		}
	}

	private String removeExtraInfoInContext(String context) {
		int endContextStartTagIndex = context.indexOf(">");
		int startIndex = context.indexOf(TomcatProject.extraBeginTag, endContextStartTagIndex);
		if (context.charAt(startIndex - 1) == '\t') {
			startIndex--;
		}
		if (context.charAt(startIndex - 1) == '\n') {
			startIndex--;
		}
		int extraEndTagStartIndex = context.indexOf(TomcatProject.extraEndTag, startIndex);
		StringBuffer out = new StringBuffer(context.substring(0, startIndex));
		int endIndex = extraEndTagStartIndex + TomcatProject.extraEndTag.length();
		out.append(context.substring(endIndex, context.length()));

		return out.toString();
	}

	private String removeLoaderInContext(String context) {
		int endContextStartTagIndex = context.indexOf(">");
		int startIndex = context.indexOf("<Loader", endContextStartTagIndex);
		if (context.charAt(startIndex - 1) == '\t') {
			startIndex--;
		}
		if (context.charAt(startIndex - 1) == '\n') {
			startIndex--;
		}
		int endIndex = context.indexOf("/>", startIndex + 1) + 1;
		StringBuffer out = new StringBuffer(context.substring(0, startIndex));
		out.append(context.substring(endIndex + 1, context.length()));

		return out.toString();
	}

	private String removeLoggerInContext(String context) {
		int endContextStartTagIndex = context.indexOf(">");
		int startIndex = context.indexOf("<Logger", endContextStartTagIndex);
		if (context.charAt(startIndex - 1) == '\t') {
			startIndex--;
		}
		if (context.charAt(startIndex - 1) == '\n') {
			startIndex--;
		}
		int endIndex = context.indexOf("/>", startIndex) + 1;
		StringBuffer out = new StringBuffer(context.substring(0, startIndex));
		out.append(context.substring(endIndex + 1, context.length()));

		return out.toString();
	}

	/*
	 * ouput could be null (project default output will be used)
	 */
	private void setFolderAsSourceEntry(IFolder folderHandle, IFolder output) throws CoreException {
		IClasspathEntry[] entries = this.javaProject.getRawClasspath();
		IClasspathEntry[] newEntries = new IClasspathEntry[entries.length + 1];
		System.arraycopy(entries, 0, newEntries, 0, entries.length);
		IPath outputPath = null;
		if (output != null) {
			outputPath = output.getFullPath();
		}

		IPath[] emptyPath = {};
		newEntries[entries.length] = JavaCore.newSourceEntry(folderHandle.getFullPath(), emptyPath, outputPath);

		this.javaProject.setRawClasspath(newEntries, null);
	}

	private String updateContextDefinition(String context) {

		// if reloadable param not set
		int reloadableIndex = context.indexOf("reloadable");
		if (reloadableIndex == -1) {
			context = this.addReloadableToContext(context);
		} else {
			context = this.updateReloadableInContext(context);
		}

		// update docBase if set
		int docBaseIndex = context.indexOf("docBase");
		if (docBaseIndex == -1) {
			context = this.addDocBaseToContext(context);
		} else {
			context = this.updateDocBaseInContext(context);
		}

		// if work param not set
		int workIndex = context.indexOf("workDir");
		if (workIndex == -1) {
			context = this.addWorkToContext(context);
		} else {
			context = this.updateWorkInContext(context);
		}

		// if loader not set
		int loaderIndex = context.indexOf("<Loader");
		if ((loaderIndex == -1) && (this.getWebClassPathEntries() != null)) {
			context = this.addLoaderToContext(context);
		}
		if ((loaderIndex != -1) && (this.getWebClassPathEntries() == null)) {
			context = this.removeLoaderInContext(context);
		}
		if ((loaderIndex != -1) && (this.getWebClassPathEntries() != null)) {
			context = this.updateLoaderInContext(context);
		}

		// if logger not set
		int loggerIndex = context.indexOf("<Logger");
		if ((loggerIndex == -1) && this.getRedirectLogger()) {
			context = this.addLoggerToContext(context);
		}
		if ((loggerIndex != -1) && !this.getRedirectLogger()) {
			context = this.removeLoggerInContext(context);
		}
		if ((loggerIndex != -1) && this.getRedirectLogger()) {
			context = this.updateLoggerInContext(context);
		}

		// Extra info
		int extraInfoIndex = context.indexOf(TomcatProject.extraBeginTag);
		if ((extraInfoIndex == -1) && !(this.getExtraInfo().equals(""))) {
			context = this.addExtraInfoToContext(context);
		}
		if ((extraInfoIndex != -1) && this.getExtraInfo().equals("")) {
			context = this.removeExtraInfoInContext(context);
		}
		if ((extraInfoIndex != -1) && !(this.getExtraInfo().equals(""))) {
			context = this.updateExtraInfoInContext(context);
		}
		return context;
	}

	private void updateContextDefinitionInFile(File xmlFile) throws IOException {
		String xml = FileUtil.readTextFile(xmlFile);
		int contextTagIndex = this.getContextTagIndex(xml);

		// If context doesn't exist do nothing
		if (contextTagIndex == -1) {
			return;
		}

		// Get context
		int endContextTagIndex = xml.indexOf(">", contextTagIndex);
		if (xml.charAt(endContextTagIndex - 1) != '/') {
			endContextTagIndex = xml.indexOf(this.getContextEndTag(), contextTagIndex)
					+ this.getContextEndTag().length() - 1;
		}
		String context = xml.substring(contextTagIndex, endContextTagIndex + 1);

		StringBuffer out = new StringBuffer(xml.substring(0, contextTagIndex));
		out.append(this.updateContextDefinition(context));
		out.append(xml.substring(endContextTagIndex + 1));
		FileUtil.toTextFile(xmlFile, out.toString());
	}

	/*
	 * create or update a Context file
	 */
	private void updateContextFile() throws CoreException, IOException {

		if (this.getUpdateXml()) {
			File contextFile = this.getContextFile();
			if (!contextFile.exists()) {
				FileUtil.toTextFile(contextFile, this.createContextDefinition());
			} else {
				this.updateContextDefinitionInFile(contextFile);
			}
		}
	}

	private String updateDocBaseInContext(String context) {
		int docBaseIndex = context.indexOf("docBase");
		int startIndex = context.indexOf('"', docBaseIndex);
		int endIndex = context.indexOf('"', startIndex + 1);
		StringBuffer out = new StringBuffer(context.substring(0, docBaseIndex));
		out.append(this.getContextDocBase());
		out.append(context.substring(endIndex + 1, context.length()));

		return out.toString();
	}

	private String updateExtraInfoInContext(String context) {
		int endContextStartTagIndex = context.indexOf(">");
		int startIndex = context.indexOf(TomcatProject.extraBeginTag, endContextStartTagIndex);
		if (context.charAt(startIndex - 1) == '\t') {
			startIndex--;
		}
		if (context.charAt(startIndex - 1) == '\n') {
			startIndex--;
		}
		int extraEndTagStartIndex = context.indexOf(TomcatProject.extraEndTag, startIndex);
		StringBuffer out = new StringBuffer(context.substring(0, startIndex));
		out.append('\n');
		out.append(TomcatProject.extraBeginTag);
		out.append('\n');
		out.append(this.getExtraInfo());
		out.append('\n');
		out.append(context.substring(extraEndTagStartIndex, context.length()));

		return out.toString();
	}

	private String updateLoaderInContext(String context) {
		context = this.formatContextEndTag(context);
		int endContextStartTagIndex = context.indexOf(">");
		int startIndex = context.indexOf("<Loader", endContextStartTagIndex);
		if (context.charAt(startIndex - 1) == '\t') {
			startIndex--;
		}
		if (context.charAt(startIndex - 1) == '\n') {
			startIndex--;
		}
		int endIndex = context.indexOf("/>", startIndex + 1) + 1;
		StringBuffer out = new StringBuffer(context.substring(0, startIndex));
		out.append(this.getContextWebAppClassLoader());
		out.append(context.substring(endIndex + 1, context.length()));

		return out.toString();
	}

	private String updateLoggerInContext(String context) {
		int endContextStartTagIndex = context.indexOf(">");
		int startIndex = context.indexOf("<Logger", endContextStartTagIndex);
		if (context.charAt(startIndex - 1) == '\t') {
			startIndex--;
		}
		if (context.charAt(startIndex - 1) == '\n') {
			startIndex--;
		}
		int endIndex = context.indexOf("/>", startIndex) + 1;
		StringBuffer out = new StringBuffer(context.substring(0, startIndex));
		out.append(this.getContextLogger());
		out.append(context.substring(endIndex + 1, context.length()));

		return out.toString();
	}

	private String updateReloadableInContext(String context) {
		int reloadableIndex = context.indexOf("reloadable");
		int startIndex = context.indexOf('"', reloadableIndex);
		int endIndex = context.indexOf('"', startIndex + 1);
		StringBuffer out = new StringBuffer(context.substring(0, reloadableIndex));
		out.append(this.getContextReloadable());
		out.append(context.substring(endIndex + 1, context.length()));

		return out.toString();
	}

	/*
	 * Add or update a Context entry on Tomcat server.xml file
	 */
	private void updateServerXML() throws CoreException, IOException {

		if (this.getUpdateXml()) {
			this.backupServerXML();

			String xml = FileUtil.readTextFile(this.getServerXML());
			if (!this.contextExistsInXML(xml)) {
				this.addContextToServerXML();
			} else {
				this.updateContextDefinitionInFile(this.getServerXML());
			}
		}
	}

	private String updateWorkInContext(String context) {
		int workDirIndex = context.indexOf("workDir");
		int startIndex = context.indexOf('"', workDirIndex);
		int endIndex = context.indexOf('"', startIndex + 1);
		StringBuffer out = new StringBuffer(context.substring(0, workDirIndex));
		out.append(this.getContextWorkDir());
		out.append(context.substring(endIndex + 1, context.length()));

		return out.toString();
	}
}
