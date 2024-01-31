package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.get
import org.w3c.dom.Document


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
  * Applies any XSLT manipulation to each file, and returns a map relating UBS
  * book name to document.
  *
  * The map is ordered according to the book order of any metadata which has
  * been supplied, or failing that according to standard UBS order.  The
  * map includes slots for *all* UBS books; if there is no data for a particular
  * book, then the corresponding document entry is null.
  *
  * @return Mapping from UBS book name to document (lower case).
  */

  fun process (): Map<String, Document?>
  {
    val res = getInputFilesInOrder()
    Dbg.reportProgress("USX: Applying pre-processing if necessary.")

    initialiseXsltStylesheets()
    res.forEach {
      val (bookName, doc) = it
      if (null != doc) applyXslt(bookName, doc)
    }

    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun applyXslt (bookName: String, doc: Document)
  {
    val stylesheetContent = m_Stylesheets[bookName.lowercase()] ?: m_Stylesheets[""] ?: return

    if ("xsl:stylesheet" in stylesheetContent)
      Dom.applyStylesheet(doc, stylesheetContent)
    else
      Dom.applyBasicStylesheet(doc, stylesheetContent)
  }


  /****************************************************************************/
  private fun initialiseXsltStylesheets ()
  {
    ConfigData.getKeys()
      .filter { it.lowercase().startsWith("stepxsltstylesheet") }
      .forEach {
        val value = ConfigData[it]
        if (!value.isNullOrBlank())
        {
          if ("_" in it)
            m_Stylesheets[it.split("_")[1].lowercase()] = value
          else
            m_Stylesheets[""] = value
        }
      }
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
      val doc = Dom.getDocumentFromText(it, retainComments = true)
      val usxAbbreviation = Dom.findNodeByName(doc, "book")!!["code"]!!.lowercase()
      if (Dbg.wantToProcessBookByAbbreviatedName(usxAbbreviation))
        res[usxAbbreviation] = doc
    }



    /**************************************************************************/
    return res
  }


  /****************************************************************************/
  /* Stylesheet information is held in ConfigData as stepXsltStylesheet, or as
     eg stepXsltStylesheet_Gen.  Empty or null values are regarded as flagging
     non-existent information. */

  private val m_Stylesheets: MutableMap<String, String?> = mutableMapOf()
}
