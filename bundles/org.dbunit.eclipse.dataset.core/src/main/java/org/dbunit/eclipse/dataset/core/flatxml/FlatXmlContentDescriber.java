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
package org.dbunit.eclipse.dataset.core.flatxml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.XMLContentDescriber;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Recognizes dbUnit flat XML dataset files by content, so Eclipse can open them in the dbUnit Dataset
 * Editor without relying on the file extension alone.
 *
 * @since 1.0.0
 */
public final class FlatXmlContentDescriber extends XMLContentDescriber
{
    @Override
    public int describe(final InputStream contents, final IContentDescription description) throws IOException
    {
        final int xmlResult = super.describe(contents, description);
        if (xmlResult == INVALID)
        {
            return INVALID;
        }
        contents.reset();
        if (isBlank(contents))
        {
            return VALID;
        }
        return describeRoot(new InputSource(contents));
    }

    @Override
    public int describe(final Reader contents, final IContentDescription description) throws IOException
    {
        final int xmlResult = super.describe(contents, description);
        if (xmlResult == INVALID)
        {
            return INVALID;
        }
        contents.reset();
        if (isBlank(contents))
        {
            return VALID;
        }
        return describeRoot(new InputSource(contents));
    }

    /**
     * Returns whether the rest of the content is empty or all whitespace, leaving it positioned back at
     * the mark either way: a blank file is a dataset waiting to be created, so it is valid.
     */
    private static boolean isBlank(final InputStream contents) throws IOException
    {
        contents.mark(Integer.MAX_VALUE);
        try
        {
            int value;
            while ((value = contents.read()) != -1)
            {
                if (!Character.isWhitespace(value))
                {
                    return false;
                }
            }
            return true;
        }
        finally
        {
            contents.reset();
        }
    }

    private static boolean isBlank(final Reader contents) throws IOException
    {
        contents.mark(Integer.MAX_VALUE);
        try
        {
            int value;
            while ((value = contents.read()) != -1)
            {
                if (!Character.isWhitespace(value))
                {
                    return false;
                }
            }
            return true;
        }
        finally
        {
            contents.reset();
        }
    }

    private static int describeRoot(final InputSource source)
    {
        final RootHandler handler = new RootHandler();
        try
        {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            final SAXParser parser = factory.newSAXParser();
            parser.parse(source, handler);
            return VALID;
        }
        catch (final DescriptionKnown known)
        {
            return known.result;
        }
        catch (final ParserConfigurationException | SAXException | IOException exception)
        {
            return handler.rootStarted ? VALID : INDETERMINATE;
        }
    }

    /**
     * Thrown by {@link RootHandler} to stop parsing as soon as the result is known, instead of reading the
     * rest of a file that might be large.
     */
    private static final class DescriptionKnown extends SAXException
    {
        private final int result;

        private DescriptionKnown(final int result)
        {
            this.result = result;
        }
    }

    private static final class RootHandler extends DefaultHandler
    {
        private boolean rootStarted;

        private int depth;

        @Override
        public InputSource resolveEntity(final String publicId, final String systemId)
        {
            return new InputSource(new StringReader(""));
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                final Attributes attributes) throws SAXException
        {
            depth++;
            if (depth == 1)
            {
                rootStarted = true;
                if (!"dataset".equals(qName))
                {
                    throw new DescriptionKnown(INVALID);
                }
                return;
            }
            if (depth == 2)
            {
                final boolean isFullXmlDataSetTable =
                        "table".equals(qName) && attributes.getValue("name") != null;
                throw new DescriptionKnown(isFullXmlDataSetTable ? INVALID : VALID);
            }
        }
    }
}
