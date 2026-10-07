/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.eclipse.dataset.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Dictionary;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Guards what the About dialog needs to show the feature. It finds the feature icon in the about.ini of the
 * bundle that the plugin attribute of the feature names, and lists the name and provider from the manifest
 * of that bundle. The tests that read the feature run under Maven, whose UI pom passes its directory.
 */
class BundleBrandingTest
{
    private static final String ABOUT_INI = "$nl$/about.ini";

    private static final String FEATURE_IMAGE_KEY = "featureImage";

    private static final String FEATURE_NAME_KEY = "featureName";

    private static final String PROVIDER_NAME_KEY = "providerName";

    private static final String DOUBLE_SIZE_SUFFIX = "@2x";

    private static final String FEATURE_DIRECTORY_PROPERTY = "dbunit.feature.directory";

    private static final String ABOUT_INI_ADVICE =
            "about.ini must be at the root of the bundle: list it in bin.includes of build.properties.";

    private static final String FEATURE_IMAGE_KEY_ADVICE =
            "about.ini must set featureImage to the path of the feature image.";

    private static final String FEATURE_IMAGE_ADVICE =
            "The feature image must be in the bundle: list its folder in bin.includes of build.properties.";

    private static final String IMAGE_SIZE_ADVICE =
            "The About dialog shows the image at its own size, so it must be 32 by 32 pixels.";

    private static final String DOUBLE_SIZE_IMAGE_ADVICE =
            "The feature image needs a copy named with @2x before its extension, for high-DPI displays.";

    private static final String DOUBLE_SIZE_ADVICE =
            "A 200% display shows the @2x image at half its size, so it must be 64 by 64 pixels.";

    private static final String BRANDING_ADVICE =
            "The About dialog lists the feature by the Bundle-Name and Bundle-Vendor of its branding"
                    + " bundle, so they must equal featureName and providerName in feature.properties.";

    private static final String FEATURE_PLUGIN_ADVICE =
            "The plugin attribute of feature.xml must name the bundle that has about.ini,"
                    + " or the About dialog leaves the feature out.";

    private static final String FEATURE_DIRECTORY_ADVICE = "The UI pom passes the feature directory as the"
            + " system property " + FEATURE_DIRECTORY_PROPERTY + ".";

    private record Branding(String name, String provider)
    {
    }

    @Test
    void testAboutIni_whenLocatedLikeTheAboutDialog_isPackagedAtTheRootOfTheBundle()
    {
        final Bundle bundle = FrameworkUtil.getBundle(DatasetUiPlugin.class);

        final URL aboutIni = findAboutIni(bundle);

        assertThat(aboutIni).as(ABOUT_INI_ADVICE).isNotNull();
    }

    @Test
    void testFeatureImage_whenResolvedLikeTheAboutDialog_isAnImageOf32By32Pixels() throws IOException
    {
        final Bundle bundle = FrameworkUtil.getBundle(DatasetUiPlugin.class);
        final String featureImage = readFeatureImagePath(bundle);

        final Point size = readImageSize(bundle, featureImage, FEATURE_IMAGE_ADVICE);

        assertThat(size).as(IMAGE_SIZE_ADVICE).isEqualTo(new Point(32, 32));
    }

    @Test
    void testFeatureImage_whenResolvedForHighDpiDisplays_isAnImageOf64By64Pixels() throws IOException
    {
        final Bundle bundle = FrameworkUtil.getBundle(DatasetUiPlugin.class);
        final String featureImage = readFeatureImagePath(bundle);
        final String doubleSizeImage = withDoubleSizeSuffix(featureImage);

        final Point size = readImageSize(bundle, doubleSizeImage, DOUBLE_SIZE_IMAGE_ADVICE);

        assertThat(size).as(DOUBLE_SIZE_ADVICE).isEqualTo(new Point(64, 64));
    }

    @Test
    void testBundleHeaders_whenListedByTheAboutDialog_matchTheNameAndProviderOfTheFeature() throws IOException
    {
        final Bundle bundle = FrameworkUtil.getBundle(DatasetUiPlugin.class);
        final Dictionary<String, String> headers = bundle.getHeaders();
        final String bundleName = headers.get(Constants.BUNDLE_NAME);
        final String bundleVendor = headers.get(Constants.BUNDLE_VENDOR);
        final Branding bundleBranding = new Branding(bundleName, bundleVendor);
        final Properties featureProperties = readFeatureProperties();
        final String featureName = featureProperties.getProperty(FEATURE_NAME_KEY);
        final String providerName = featureProperties.getProperty(PROVIDER_NAME_KEY);
        final Branding featureBranding = new Branding(featureName, providerName);

        assertThat(bundleBranding).as(BRANDING_ADVICE).isEqualTo(featureBranding);
    }

    @Test
    void testFeature_whenReadFromTheFeatureProject_namesTheUiBundleAsItsBrandingPlugin()
            throws IOException, ParserConfigurationException, SAXException
    {
        final Path featureXml = featureDirectory().resolve("feature.xml");
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final String brandingPlugin;
        try (InputStream stream = Files.newInputStream(featureXml))
        {
            final Document feature = builder.parse(stream);
            final Element root = feature.getDocumentElement();
            brandingPlugin = root.getAttribute("plugin");
        }

        assertThat(brandingPlugin).as(FEATURE_PLUGIN_ADVICE).isEqualTo(DatasetUiPlugin.PLUGIN_ID);
    }

    private static URL findAboutIni(final Bundle bundle)
    {
        final IPath aboutIniPath = IPath.fromOSString(ABOUT_INI);
        return FileLocator.find(bundle, aboutIniPath, null);
    }

    private static Properties readAboutIni(final Bundle bundle) throws IOException
    {
        final URL aboutIni = findAboutIni(bundle);
        assertThat(aboutIni).as(ABOUT_INI_ADVICE).isNotNull();
        final Properties properties = new Properties();
        try (InputStream stream = aboutIni.openStream())
        {
            properties.load(stream);
        }
        return properties;
    }

    private static String readFeatureImagePath(final Bundle bundle) throws IOException
    {
        final Properties aboutIni = readAboutIni(bundle);
        final String featureImage = aboutIni.getProperty(FEATURE_IMAGE_KEY);
        assertThat(featureImage).as(FEATURE_IMAGE_KEY_ADVICE).isNotNull();
        return featureImage;
    }

    private static Point readImageSize(final Bundle bundle, final String path, final String advice)
            throws IOException
    {
        final IPath imagePath = IPath.fromOSString(path);
        final URL imageUrl = FileLocator.find(bundle, imagePath, null);
        assertThat(imageUrl).as(advice).isNotNull();
        try (InputStream stream = imageUrl.openStream())
        {
            final ImageData image = new ImageData(stream);
            return new Point(image.width, image.height);
        }
    }

    private static String withDoubleSizeSuffix(final String imagePath)
    {
        final int extensionStart = imagePath.lastIndexOf('.');
        final String name = imagePath.substring(0, extensionStart);
        final String extension = imagePath.substring(extensionStart);
        return name + DOUBLE_SIZE_SUFFIX + extension;
    }

    private static Properties readFeatureProperties() throws IOException
    {
        final Path featureProperties = featureDirectory().resolve("feature.properties");
        final Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(featureProperties))
        {
            properties.load(stream);
        }
        return properties;
    }

    private static Path featureDirectory()
    {
        final String directory = System.getProperty(FEATURE_DIRECTORY_PROPERTY);
        assumeTrue(directory != null, FEATURE_DIRECTORY_ADVICE);
        final Path featureDirectory = Path.of(directory);
        return featureDirectory.normalize();
    }
}
