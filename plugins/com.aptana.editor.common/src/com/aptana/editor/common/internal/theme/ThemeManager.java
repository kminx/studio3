package com.aptana.editor.common.internal.theme;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.osgi.service.prefs.BackingStoreException;

import com.aptana.editor.common.CommonEditorPlugin;
import com.aptana.editor.common.theme.IThemeManager;
import com.aptana.editor.common.theme.Theme;

public class ThemeManager implements IThemeManager
{
	/**
	 * Character used to separate listing of theme names stored under {@link #THEME_LIST_PREF_KEY}
	 */
	private static final String THEME_NAMES_DELIMETER = ","; //$NON-NLS-1$

	/**
	 * Preference key used to store the list of known themes.
	 */
	private static final String THEME_LIST_PREF_KEY = "themeList"; //$NON-NLS-1$

	/**
	 * Preference key used to save the active theme.
	 */
	private static final String ACTIVE_THEME = "ACTIVE_THEME"; //$NON-NLS-1$

	/**
	 * Node in preferences used to store themes under. Each theme is a key value pair under this node. The key is the
	 * theme name, value is XML format java Properties object.
	 */
	public static final String THEMES_NODE = "themes"; //$NON-NLS-1$
	// TODO Don't expose this node name. Fold saving/loading of themes into this impl

	private Theme fCurrentTheme;
	private HashMap<String, Theme> fThemeMap;
	private HashSet<String> fBuiltins;
	private Map<WeakReference<Token>, String> fTokens;

	private static ThemeManager fgInstance;

	private ThemeManager()
	{
		fTokens = new HashMap<WeakReference<Token>, String>();
	}

	public static ThemeManager instance()
	{
		if (fgInstance == null)
		{
			fgInstance = new ThemeManager();
		}
		return fgInstance;
	}

	private TextAttribute getTextAttribute(String name)
	{
		if (getCurrentTheme() != null)
			return getCurrentTheme().getTextAttribute(name);
		return new TextAttribute(CommonEditorPlugin.getDefault().getColorManager().getColor(new RGB(255, 255, 255)));
	}

	public Theme getCurrentTheme()
	{
		if (fCurrentTheme == null)
		{
			String activeThemeName = Platform.getPreferencesService().getString(CommonEditorPlugin.PLUGIN_ID,
					ACTIVE_THEME, null, null);
			if (activeThemeName != null)
				fCurrentTheme = getTheme(activeThemeName);
			if (fCurrentTheme == null)
				setCurrentTheme(getThemeMap().values().iterator().next());
		}
		return fCurrentTheme;
	}

	public void setCurrentTheme(Theme theme)
	{
		fCurrentTheme = theme;
		adaptTokens();

		// Set the find in file search color
		IEclipsePreferences prefs = new InstanceScope().getNode("org.eclipse.search");
		prefs.put("org.eclipse.search.potentialMatch.fgColor", toString(theme.getSearchResultColor()));
		try
		{
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
			CommonEditorPlugin.logError(e);
		}

		// Set the color for the search result annotation, the pref key is "searchResultIndicationColor"
		prefs = new InstanceScope().getNode("org.eclipse.ui.editors");
		prefs.put("searchResultIndicationColor", toString(theme.getSearchResultColor()));
		try
		{
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
			CommonEditorPlugin.logError(e);
		}

		// Also set the standard eclipse editor props, like fg, bg, selection fg, bg
		prefs = new InstanceScope().getNode(CommonEditorPlugin.PLUGIN_ID);
		prefs.putBoolean(AbstractTextEditor.PREFERENCE_COLOR_SELECTION_FOREGROUND_SYSTEM_DEFAULT, false);
		prefs.put(AbstractTextEditor.PREFERENCE_COLOR_SELECTION_FOREGROUND, toString(theme.getSelection()));

		prefs.putBoolean(AbstractTextEditor.PREFERENCE_COLOR_BACKGROUND_SYSTEM_DEFAULT, false);
		prefs.put(AbstractTextEditor.PREFERENCE_COLOR_BACKGROUND, toString(theme.getBackground()));

		prefs.put(AbstractTextEditor.PREFERENCE_COLOR_FOREGROUND, toString(theme.getForeground()));
		prefs.putBoolean(AbstractTextEditor.PREFERENCE_COLOR_FOREGROUND_SYSTEM_DEFAULT, false);

		prefs.put(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_CURRENT_LINE_COLOR, toString(theme
				.getLineHighlight()));

		prefs.put(ACTIVE_THEME, theme.getName());
		prefs.putLong(THEME_CHANGED, System.currentTimeMillis());
		try
		{
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
			CommonEditorPlugin.logError(e);
		}
	}

	private static String toString(RGB selection)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(selection.red).append(THEME_NAMES_DELIMETER).append(selection.green).append(
				THEME_NAMES_DELIMETER).append(selection.blue);
		return builder.toString();
	}

	public Theme getTheme(String name)
	{
		return getThemeMap().get(name);
	}

	private Map<String, Theme> getThemeMap()
	{
		if (fThemeMap == null)
		{
			loadThemes();
		}
		return fThemeMap;
	}

	public Set<String> getThemeNames()
	{
		return getThemeMap().keySet();
	}

	private void loadThemes()
	{
		fThemeMap = new HashMap<String, Theme>();
		// Load builtin themes stored in properties files
		loadBuiltinThemes();
		// Load themes from the preferences
		loadUserThemes();

		saveThemeList();
	}

	private void saveThemeList()
	{
		StringBuilder builder = new StringBuilder();
		for (String themeName : fThemeMap.keySet())
		{
			// FIXME What if the themeName contains our delimeter?!
			builder.append(themeName).append(THEME_NAMES_DELIMETER);
		}
		builder.deleteCharAt(builder.length() - 1);
		IEclipsePreferences prefs = new InstanceScope().getNode(CommonEditorPlugin.PLUGIN_ID);
		prefs.put(THEME_LIST_PREF_KEY, builder.toString());
		try
		{
			prefs.flush();
		}
		catch (BackingStoreException e)
		{
			CommonEditorPlugin.logError(e);
		}
	}

	private void loadUserThemes()
	{
		String themeNames = Platform.getPreferencesService().getString(CommonEditorPlugin.PLUGIN_ID,
				THEME_LIST_PREF_KEY, null, null);
		if (themeNames == null)
			return;
		StringTokenizer tokenizer = new StringTokenizer(themeNames, THEME_NAMES_DELIMETER);
		while (tokenizer.hasMoreElements())
		{
			try
			{
				String themeName = tokenizer.nextToken();
				String xmlProps = Platform.getPreferencesService().getString(CommonEditorPlugin.PLUGIN_ID,
						THEMES_NODE + "/" + themeName, null, null); //$NON-NLS-1$
				if (xmlProps == null || xmlProps.trim().length() == 0)
					continue;
				Properties props = new Properties();
				props.loadFromXML(new ByteArrayInputStream(xmlProps.getBytes("UTF-8"))); //$NON-NLS-1$
				Theme theme = new Theme(CommonEditorPlugin.getDefault().getColorManager(), props);
				fThemeMap.put(theme.getName(), theme);
			}
			catch (Exception e)
			{
				CommonEditorPlugin.logError(e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void loadBuiltinThemes()
	{
		fBuiltins = new HashSet<String>();
		Enumeration<URL> urls = CommonEditorPlugin.getDefault().getBundle()
				.findEntries("themes", "*.properties", false); //$NON-NLS-1$ //$NON-NLS-2$
		if (urls == null)
			return;
		while (urls.hasMoreElements())
		{
			URL url = urls.nextElement();
			try
			{
				Theme theme = loadTheme(url.openStream());
				if (theme != null)
				{
					fThemeMap.put(theme.getName(), theme);
					fBuiltins.add(theme.getName());
				}
			}
			catch (Exception e)
			{
				CommonEditorPlugin.logError(url.toString(), e);
			}
		}
	}

	private static Theme loadTheme(InputStream stream) throws IOException
	{
		try
		{
			Properties props = new Properties();
			props.load(stream);
			return new Theme(CommonEditorPlugin.getDefault().getColorManager(), props);
		}
		finally
		{
			try
			{
				stream.close();
			}
			catch (IOException e)
			{
				// ignore
			}
		}
	}

	public IToken getToken(String string)
	{
		Token token = new Token(getTextAttribute(string));
		fTokens.put(new WeakReference<Token>(token), string);
		return token;
	}

	private void adaptTokens()
	{
		for (Map.Entry<WeakReference<Token>, String> entry : fTokens.entrySet())
		{
			Token token = entry.getKey().get();
			if (token != null)
				token.setData(getTextAttribute(entry.getValue()));
		}
	}

	public void addTheme(Theme newTheme)
	{
		getThemeMap().put(newTheme.getName(), newTheme);
		newTheme.save();
		saveThemeList();
	}

	public void removeTheme(Theme theme)
	{
		Theme activeTheme = getCurrentTheme();
		getThemeMap().remove(theme.getName());
		saveThemeList();
		// change active theme if we just removed it
		if (activeTheme.getName().equals(theme.getName()))
		{
			setCurrentTheme(fThemeMap.values().iterator().next());
		}
	}

	public boolean isBuiltinTheme(String themeName)
	{
		return fBuiltins.contains(themeName);
	}

	@Override
	public IStatus validateThemeName(String name)
	{
		if (name == null || name.trim().length() == 0)
			return new Status(IStatus.ERROR, CommonEditorPlugin.PLUGIN_ID, Messages.ThemeManager_NameNonEmptyMsg);
		if (getThemeNames().contains(name.trim()))
			return new Status(IStatus.ERROR, CommonEditorPlugin.PLUGIN_ID, Messages.ThemeManager_NameAlreadyExistsMsg);
		if (name.contains(THEME_NAMES_DELIMETER))
			return new Status(IStatus.ERROR, CommonEditorPlugin.PLUGIN_ID, MessageFormat.format(
					Messages.ThemeManager_InvalidCharInThemeName, THEME_NAMES_DELIMETER));
		return Status.OK_STATUS;
	}
}
