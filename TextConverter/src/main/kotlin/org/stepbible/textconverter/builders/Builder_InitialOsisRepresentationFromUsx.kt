package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodeByName
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.get
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.stepbible.textconverter.usxinputonly.Usx_OsisCreator
import org.stepbible.textconverter.usxinputonly.Usx_Tidier
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.protocolagnosticutils.PA_BasicVerseEndInserter
import java.io.File


/******************************************************************************/
/**
* Takes data from InputUsx, applies any necessary pre-processing, and then
* converts the result to expanded OSIS.
*
* In a previous implementation, I did a lot of work here.  In this latest
* incarnation all of that work has been deferred to later in the processing
* chain, and is applied not to the USX but to the OSIS generated from it.
* It is important that the work in this present class should therefore be kept
* to an absolute minimum.  It's ok to apply simple modifications to the data,
* for example to correct systematic errors in it; but you should avoid changing
* the verse structure, expanding elisions etc.
*
* (I have in fact left in the code some provision for moving this more
* sophisticated processing back into USX should we ever need to do so -- see the
* discussion in the user and maintenance guide.  However there are good reasons
* at present for retaining it in the OSIS side of the system, and so long as
* this remains the case, you should avoid doing anything very much here.)
*
* ## Preprocessing and filtering
* USX processing works with a Document representation, and therefore supports
* XSLT preprocessing (both for the overall text and also selectively per book).
*
* It also supports filtering by book, so you can limit the number of books
* processed on a given run (particularly where you are debugging).
*
* @author ARA "Jamie" Jamieson
*/

object Builder_InitialOsisRepresentationFromUsx: Builder()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Preparing USX and converting to OSIS"
  override fun commandLineOptions () = null





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun doIt ()
  {
    Dbg.withReportProgressMain(banner(), ::doIt1)
  }


  /****************************************************************************/
  private fun doIt1 ()
  {
    BuilderUtils.getInputFiles(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx(), -1).forEach(::loadFile)
    BuilderUtils.processXslt(UsxDataCollection)
    Usx_Tidier.process(UsxDataCollection)
    RefBase.setBibleStructure(UsxDataCollection.getBibleStructure())
    ConfigData.makeBibleDescriptionAsItAppearsOnBibleList(UsxDataCollection.getBookNumbers())
    Usx_OsisCreator.process(UsxDataCollection)
    PA_BasicVerseEndInserter.process(ExternalOsisDoc, Osis_FileProtocol)
    RefBase.setBibleStructure(null)
    //Dbg.d(ExternalOsisDoc)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun loadFile (filePath: String)
  {
    val text = Builder_Master.processRegexes(File(filePath).bufferedReader().readText(), ConfigData.getPreprocessingRegexes())
    val doc = Dom.getDocumentFromText(text, true)
    val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(doc.findNodeByName("book")!!["code"]!!)
    if (Dbg.wantToProcessBook(bookNo))
      UsxDataCollection.addFromDoc(doc)
  }
}
