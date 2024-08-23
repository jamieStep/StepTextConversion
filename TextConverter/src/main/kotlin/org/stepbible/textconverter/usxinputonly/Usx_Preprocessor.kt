package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.X_DataCollection
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Applies any necessary pre-processing to files from InputUsx.
 *
 * USX being a rather complex standard (and at the same time lacking facilities
 * which translators commonly seem to require), it quite often happens that
 * we receive texts which are incorrect in some manner (or at the very least,
 * which do not fit with our processing).
 *
 * One way of addressing this is to apply changes to the text before the
 * converter runs (outside of the knowledge of the converter).  This is
 * certainly acceptable, although obviously it will require you either to apply
 * the changes manually, or else to create standalone processing to handle them.
 *
 * Alternatively, you can use the built-in facilities supplied here.  Of course,
 * you lose the complete flexibility available to you if you do your own thing;
 * but on the other hand, you do avoid having to write standalone processing.
 *
 * The facilities here rely upon you supplying, via one or more configuration
 * parameters, some XSLT fragments, which are applied to the input files.
 *
 * A configuration parameter stepXsltStylesheet defines a backstop sheet to be
 * applied to *all* files.  Sheets with names like stepXsltStylesheet_Gen etc
 * are applied to individual files.  Where both exist, the book-specific form
 * is applied where appropriate instead of the generic one.
 *
 * CAUTION: There is a limitation here.  I have tried to organise things so
 * that it makes no difference whether the USX data comes in one file per
 * book, or all books in a single file.  However, so far as I know, stylesheets
 * can only be applied to an entire document.  If we have per book stylesheets
 * and a document which covers more than one book, this won't work, and I throw
 * an error here.
 *
 * $$$ May want to add support for regex changes too?????????????????????????
 *
 * @author ARA Jamieson
 */

object Usx_Preprocessor
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
  * Applies any preprocessing regexes to the incoming USX.
  *
  * @param inputText Text to be processed.
  */

  fun processRegex (inputText: String): String
  {
    val regexes = ConfigData.getNonOsisRegexes()
    if (regexes.isEmpty()) return inputText

    var revisedText = inputText

    regexes.forEach {
      revisedText = applyRegex(it, revisedText)
    }

    return revisedText
  }


  /****************************************************************************/
  /**
  * Applies any XSLT manipulation to each file, and sets up UsxDataCollection
  * to hold the details.  To state the obvious, this can be called only after
  * the USX text has been read and converted to DOM format.
  *
  * @param usxDataCollection All the documents we may need to process.
  */

  fun processXslt (usxDataCollection: X_DataCollection)
  {
    Dbg.reportProgress("USX: Applying pre-processing if necessary.")
    initialiseXsltStylesheets()
    usxDataCollection.getDocuments().forEach {
      val newDoc = applyXslt(it)
      if (null != newDoc)
      {
        //temp(newDoc)
        usxDataCollection.replaceDocumentStructure(newDoc)
        //Dbg.outputDom(newDoc)
      }
    }
  }


  private fun temp (doc: Document)
  {
    fun wrapInWj (node: Node)
    {
      val wrapper = Dom.createNode(node.ownerDocument, "<char style='wj'/>")
      Dom.insertNodeBefore(node, wrapper)
      Dom.deleteNode(node)
      wrapper.appendChild(node)
    }

    Dom.findNodesByAttributeValue(doc, "char", "style", "bd")
      .forEach {
        if (Dom.hasAncestorNamed(it, "note"))
          Dom.setAttribute(it, "XInNote", "1")
        else
        {
          val textContent = it.textContent.trim()
          if (2 != textContent.length)
            Dom.setAttribute(it, "XBoring", "1")
          else
            if (null == Dom.getChildren(it).find { !Dom.isTextNode(it) } )
            {
              Dom.setAttribute(it, "XNotInNote", "1")
              wrapInWj(it)
            }
            else
              Dom.setAttribute(it, "XBoring", "1")
        }
      }
    //Dbg.outputDom(doc)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Applies any regex processing to the input text.  regexDetails is a pair,
     the first part of which should be a regex pattern, and the second of which
     is a replacement.

     I'm having a little trouble working out how to do this consistently, simply
     and with a reasonable degree of flexibility.  As things stand, unless
     the replacement contains @convertRef, I assume that the pattern and
     replacement are mutually compatible in terms of capturing groups, and apply
     a simple replacement.

     If it does contain @convertRef, I assume that the pattern contains a single
     capturing group which is a reference in vernacular form, and that the
     replacement is made up purely of @convertRef.  In this case I take the
     capturing group and convert it to USX form.

     Actually, it's not @convertRef -- it's either @convertRefVernacularToUsx
     or @convertRefVernacularToOsis.
   */

  private fun applyRegex (regexDetails: Pair<Regex, String>, inputText: String): String
  {
    /**************************************************************************/
    fun convertRefVernacularToOsis (s: String) = RefCollection.rdVernacular(s).toStringUsx()
    fun convertRefVernacularToUsx (s: String) = RefCollection.rdVernacular(s).toStringUsx()
    var converter: ((String) -> String)? = null

    if ("@convertRefVernacularToUsx" in regexDetails.second)
      converter = ::convertRefVernacularToUsx
    else if ("@convertRefVernacularToOsis" in regexDetails.second)
      converter = ::convertRefVernacularToOsis



    /**************************************************************************/
    return if (null == converter)
      inputText.replace(regexDetails.first, regexDetails.second)
    else
      regexDetails.first.replace(inputText) { matchResult -> converter(matchResult.groupValues[1]) }
  }


  /****************************************************************************/
  private fun applyXslt (doc: Document): Document?
  {
    val books = Dom.findNodesByName(doc, "book")
    if (books.size > 1 && m_HavePerBookStylesheet)
      throw StepException("Have per-book stylesheet and more than one book in a file.")

    val bookName = books[0]["code"]!!
    val stylesheetContent = m_Stylesheets[bookName.lowercase()] ?: m_Stylesheets[""] ?: return null

    return if ("xsl:stylesheet" in stylesheetContent)
      Dom.applyStylesheet(doc, stylesheetContent)
    else
      Dom.applyBasicStylesheet(doc, stylesheetContent)
  }


  /****************************************************************************/
  /* Returns a map relating UBS book name to document.  The map follows the
     ordering supplied in any metadata provided to us, or the ordering in any
     details which the user has explicitly supplied in the configuration file,
     or the default UBS ordering.  It contains entries for all of the book names
     supplied by any of these sources; if no data is available for any of these
     books, then the corresponding document entry is null. */

  private fun getInputFilesInOrder (): MutableMap<String, Document?>
  {
    /**************************************************************************/
    val res = mutableMapOf<String, Document?>()



    /**************************************************************************/
    val descriptors = ConfigData.getBookDescriptors()
    if (descriptors.isEmpty())
      BibleBookNamesUsx.getAbbreviatedNameList().forEach {res[it.lowercase()] = null }
    else
      descriptors.forEach { res[it.ubsAbbreviation.lowercase()] = null }


    /**************************************************************************/
    val files = StepFileUtils.getMatchingFilesFromFolder(FileLocations.getInputUsxFolderPath(), ".*\\.${FileLocations.getFileExtensionForUsx()}".toRegex()).map { it.toString() }
    files.forEach {
      val doc = Dom.getDocument(it, retainComments = true)
      val usxAbbreviation = Dom.findNodeByName(doc, "book")!!["code"]!!.lowercase()
      if (Dbg.wantToProcessBookByAbbreviatedName(usxAbbreviation))
        res[usxAbbreviation] = doc
    }



    /**************************************************************************/
    return res
  }


  /****************************************************************************/
  private fun initialiseXsltStylesheets ()
  {
    ConfigData.getKeys()
      .filter { it.lowercase().startsWith("stepnonosisxsltstylesheet") || it.lowercase().startsWith("stepxsltstylesheet") }
      .forEach {
        val value = ConfigData[it]
        if (!value.isNullOrBlank())
        {
          if ("_" in it)
          {
            m_HavePerBookStylesheet = true
            m_Stylesheets[it.split("_")[1].lowercase()] = value
          }
          else
            m_Stylesheets[""] = value
        }
      }
  }


  /****************************************************************************/
  /* Stylesheet information is held in ConfigData as stepXsltStylesheet, or as
     eg stepXsltStylesheet_Gen.  Empty or null values are regarded as flagging
     non-existent information. */

  private val m_Stylesheets: MutableMap<String, String?> = mutableMapOf()
  private var m_HavePerBookStylesheet = false
}
