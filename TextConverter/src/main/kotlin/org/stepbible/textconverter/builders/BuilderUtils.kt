package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefCollection
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.w3c.dom.Document
import java.nio.file.Paths


/******************************************************************************/
/**
 * Utilities for use by Builder instances.
 *
 * @author ARA "Jamie" Jamieson
 */

object BuilderUtils
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Clears and recreates the folder structure needed to contain the external-
  * facing OSIS data.  Returns a discouraging name for use when saving the
  * data.  That way if anything fails, we shan't get confused into thinking
  * that the data is usable.
  *
  * @return filePath for external-facing OSIS file.
  */

  fun createExternalOsisFolderStructure (): String
  {
    StepFileUtils.deleteFileOrFolder(FileLocations.getInputOsisFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInputOsisFolderPath())
    return Paths.get(FileLocations.getInputOsisFolderPath(), FileLocations.getInputOsisTemporaryFileName()).toString()
  }


  /****************************************************************************/
  /**
  * Gets the input files, and checks that the number of input files is ok.
  *
  * @param folderPath
  * @param fileExtension We look for files in the given folder with this extension.
  * @param numberExpected 1 if only a single file is expected, otherwise -1.
  */

  fun getInputFiles (folderPath: String, fileExtension: String, numberExpected: Int): List<String>
  {
    val res = StepFileUtils.getMatchingFilesFromFolder(folderPath, ".*\\.$fileExtension".toRegex()).map { it.toString() }
    if (res.isEmpty()) throw StepExceptionBase("No suitable input files available.")
    if (1 == numberExpected && 1 != res.size) throw StepExceptionBase("Expecting precisely one input file, but ${res.size} files available.")
    return res
  }


  /****************************************************************************/
  /**
  * Applies a defined collection of regexes to a chunk of text.
  *
  * @param inputText
  * @param regexes List of regexes (possibly empty or null).
  * @return Modified text.
  */

  fun processRegexes (inputText: String, regexes: List<Pair<Regex, String>>?): String
  {
    if (regexes.isNullOrEmpty()) return inputText

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
  * @param doc Document to be processed.
  */

  fun processXslt (doc: Document): Document
  {
    val stylesheetContent = ConfigData.getPreprocessingXslt() ?: return doc
    lateinit var res: Document
    Dbg.withReportProgressSub("Applying XSLT preprocessing.") {
      res = applyXslt(doc, stylesheetContent)
    }

    return res
  }


  /****************************************************************************/
  /**
  * Applies any XSLT manipulation to each file, and sets up UsxDataCollection
  * to hold the details.  To state the obvious, this can be called only after
  * the USX text has been read and converted to DOM format.
  *
  * @param dataCollection All the documents we may need to process.
  */

  fun processXslt (dataCollection: X_DataCollection)
  {
    val stylesheetContent = ConfigData.getPreprocessingXslt() ?: return

    dataCollection.getDocuments().forEach {
      val bookName = dataCollection.getFileProtocol().getBookAbbreviation(dataCollection.getFileProtocol().getBookNode(it)!!)
      Dbg.reportProgress("Applying XSLT preprocessing to $bookName.")
      val newDoc = applyXslt(it, stylesheetContent)
      dataCollection.replaceDocumentStructure(newDoc)
    }
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
  private fun applyXslt (doc: Document, stylesheetContent: String): Document
  {
    return if ("xsl:stylesheet" in stylesheetContent)
      Dom.applyStylesheet(doc, stylesheetContent)
    else
      Dom.applyBasicStylesheet(doc, stylesheetContent)
  }
}
