package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
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
  * Gets the explanation callout for a footnote.
  *
  * @param callout Details of proposed callout.  If null, we use a value from
  *   the configuration data.  If a string, we use that.  Otherwise must be a
  *   marker handler, and we call on that to supply a value.
  *
  * @return Callout.
  */

  fun getCallout (callout: Any?): String
  {
    return when (callout)
      {
        null      -> m_StepExplanationCallout
        is String -> callout
        else      -> (callout as MarkerHandler).get()
      }
  }


  /****************************************************************************/
  /** Iterates over all nodes of a given kind in a document.  This relies
      upon OSIS and USX using the same tags for book, chapter and verse.  In
      fact they don't:

      - OSIS permits an alternative to 'chapter', but I standardise that to use
        chapter early on, so we should be ok.

      - OSIS uses div:book rather than book.  I make temporary changes to force
        that to be 'book', however, so the present methods can in fact be called.

      - The only problematical one is verse.  You can use the processing here in
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
    val stringWriter = StringWriter()
    val bufferedWriter = if (null == filePath)
      BufferedWriter(stringWriter)
    else
      File(filePath).bufferedWriter()

    writeFn(bufferedWriter)
    bufferedWriter.flush()
    bufferedWriter.close()

    return if (null == filePath) stringWriter.toString() else null
  }


  /****************************************************************************/
  /**
   *  Converts a book abbreviation into a standard 'pretty' form for use in
   *  progress reports.
   *
   * @param abbreviation
   * @return Prettified abbreviation.
   */

  fun prettifyBookAbbreviation (abbreviation: String): String
  {
    val number = if (abbreviation[0].isDigit()) abbreviation[0].toString() else ""
    var rest = abbreviation.substring(number.length)
    rest = rest[0].uppercase() + rest.substring(1).lowercase()
    return number + rest
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  private val m_StepExplanationCallout by lazy { ConfigData["stepExplanationCallout"]!! }
}