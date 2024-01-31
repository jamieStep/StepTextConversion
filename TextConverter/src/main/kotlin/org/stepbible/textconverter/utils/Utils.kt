package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.set
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter

/****************************************************************************/
/**
 * Miscellaneous application-specific utilities (as opposed to the general-
 * purpose utilities in the support project).
 *
 * @author ARA "Jamie" Jamieson
 */

object Utils
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Adds a temporary attribute to a node, and records the fact that it has done
  * so, to make removal of temporaries easier later.
  *
  * @param node Node to which attribute is to be attached.
  * @param attributeName
  * @param attributeValue
  */

  fun addTemporaryAttribute (node: Node, attributeName: String, attributeValue: String)
  {
    node[attributeName] = attributeValue
    node["_t"] = "y"
  }


  /****************************************************************************/
  /**
  * Deletes all temporary attributes from nodes which are marked as having
  * temporary attributes.
  *
  * @param doc The document from which the attributes are to be deleted.
  */

  fun deleteTemporaryAttributes (doc: Document)
  {
    Dom.getNodesInTree(doc)
      .filter { null != it["_t"] }
      .forEach { node -> Dom.getAttributes(node).keys.filter { it.startsWith("_temp_") }.forEach { Dom.deleteAttribute(node, it) } }
  }


  /****************************************************************************/
  /** Iterators over all nodes of a given kind in a document.  This relies
      upon OSIS and USX using the same tags for book, chapter and verse.  In
      fact they don't:

      -- OSIS permits an alternative to 'chapter', but I standardise that to use
         chapter early on, so we should be ok.

      -- OSIS uses div:book rather than book.  I make temporary changes to force
         that to be 'book', however, so the present methods can in fact be called.

      -- The only problematical one is verse.  You can use the processing here in
         OSIS, but not in USX. */

  fun iterateOverBooks    (doc: Document, fn: (book   : Node) -> Unit) = Dom.findNodesByName(doc, "book").forEach { fn(it) }
  fun iterateOverChapters (doc: Document, fn: (chapter: Node) -> Unit) = Dom.findNodesByName(doc, "chapter").forEach { fn(it) }
  fun iterateOverVerses   (doc: Document, fn: (verse  : Node) -> Unit) = Dom.findNodesByName(doc, "verse"  ).forEach { fn(it) }


  /****************************************************************************/
  /**
  * Calls a writer function to write either to a file or to a string.
  *
  * @param filePath File to write to, or null, in which case output goes to a
  *   string.
  * @param writeFn Method which does the writing.
  */

  fun outputToFileOrString (filePath: String?, writeFn: (BufferedWriter) -> Unit): String?
  {
    var stringWriter = StringWriter()
    val bufferedWriter = if (null == filePath)
      BufferedWriter(stringWriter)
    else
      File(filePath).bufferedWriter()

    writeFn(bufferedWriter)
    bufferedWriter.flush()
    bufferedWriter.close()

    return if (null == filePath) stringWriter.toString() else null
  }
}