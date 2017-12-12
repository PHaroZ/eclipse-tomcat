# Eclipse plugin to drive tomcat

### **Installation**

*   This plugin does not contain Tomcat.  
    (Download and install Tomcat before using this plugin).  
    This is a design choice not to include Tomcat in the plugin distribution, this way the same plugin version can works with any Tomcat version.  

*   Download tomcatPluginVxxx.zip

*   Unzip it in :  
    - Eclipse_Home/dropins for Eclipse 3.4, 3.5 and 3.6  
    - Eclipse_Home/plugins for Eclipse 2.1, 3.0, 3.1, 3.2 and 3.3  

*   Plugin activation for Eclipse 3.x :  
    - launch eclipse once using this option : **-clean**  
    - if Tomcat icons are not shown in toolbar : select menu 'Window>Customize Perspective...>Commands', and check 'Tomcat' in 'Available command groups'  

*   Set Tomcat version and Tomcat home : Workbench -> Preferences, select Tomcat and set Tomcat version and Tomcat home (Tomcat version and Tomcat home are the only required fields, other settings are there for advanced configuration).

*   This plugin launches Tomcat using the default JRE checked in Eclipe preferences window.  
    To set a JDK as default JRE for Eclipse open the preference window : Window -> Preferences -> Java -> Installed JREs.  
    This JRE must be a JDK (This is a Tomcat prerequisite).  

*   The plugin sets itself Tomcat classpath and bootclasspath. Use Preferences -> Tomcat ->JVM Settings, only if you need specific settings.


Original source : Sysdeo Eclipse Tomcat Launcher plugin web site at : [http://www.eclipsetotale.com/tomcatPlugin.html](http://www.eclipsetotale.com/tomcatPlugin.html)
