package com.personal.dashboard.nas.adapter;

import com.personal.dashboard.global.WorkspaceException;
import java.io.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.*;

/** Bounded DAV XML parsing; DTD/entity expansion and external resources are disabled. */
public final class DavXml {
  private DavXml() {}

  public static Document parse(byte[] bytes) {
    if (bytes.length == 0) return null;
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute("jdk.xml.maxElementDepth", 64);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var builder = factory.newDocumentBuilder();
      builder.setErrorHandler(
          new org.xml.sax.helpers.DefaultHandler() {
            @Override
            public void fatalError(SAXParseException error) throws SAXException {
              throw error;
            }
          });
      return builder.parse(new ByteArrayInputStream(bytes));
    } catch (Exception error) {
      throw new WorkspaceException(400, "잘못된 WebDAV XML입니다.");
    }
  }

  public static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  public static String element(String namespace, String name, String value) {
    if (namespace.isEmpty()) return "<" + name + ">" + value + "</" + name + ">";
    return "<p:" + name + " xmlns:p=\"" + escape(namespace) + "\">" + value + "</p:" + name + ">";
  }
}
