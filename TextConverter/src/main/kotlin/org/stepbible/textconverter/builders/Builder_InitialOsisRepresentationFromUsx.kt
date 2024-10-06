package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.stepbible.textconverter.usxinputonly.Usx_OsisCreator
import org.stepbible.textconverter.usxinputonly.Usx_Tidier
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.protocolagnosticutils.PA_Utils.convertToEnclosingTags
import org.w3c.dom.Document
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
    Rpt.report(0, banner())
    Rpt.reportWithContinuation(level = 1, "Loading data ...") {
      loadFiles(BuilderUtils.getInputFiles(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx(), -1))
    }

    Usx_Tidier.process(UsxDataCollection)
    RefBase.setBibleStructure(UsxDataCollection.getBibleStructure())
    ConfigData.makeBibleDescriptionAsItAppearsOnBibleList(UsxDataCollection.getBookNumbers())
    Usx_OsisCreator.process(UsxDataCollection)
    RefBase.setBibleStructure(null)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun loadFiles (filePaths: List<String>)
  {
    // I deliberately do not use the common pattern elsewhere, where I create
    // a separate class instance to handle the processing for each book.  There
    // is no need for this here, because there is no local storage to worry
    // about.

    with(ParallelRunning(true)) {
      run {
        filePaths.forEach { filePath ->
          asyncable {
            val text = Builder_Master.processRegexes(File(filePath).bufferedReader().readText(), ConfigData.getPreprocessingRegexes())
            var doc = Dom.getDocumentFromText(text, true)
            doc = BuilderUtils.processXslt(doc, Usx_FileProtocol)
            val bookNode = doc.findNodeByName("book")!!
            val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(bookNode["code"]!!)
            if (Dbg.wantToProcessBook(bookNo))
            {
              Dom.deleteChildren(bookNode) // USX book nodes contain a rather meaningless string, but otherwise are not enclosing nodes.
              convertToEnclosingTags(doc.documentElement, "book")
              sortOutSidsEtc(doc)
              UsxDataCollection.addFromDoc(doc)
            } // if
          } // asyncable
        } // forEach
      } // run
    } // with
  } // fun


  /****************************************************************************/
  private fun sortOutSidsEtc (doc: Document)
  {
    var book = ""
    var chapter = ""

    doc.getAllNodesBelow().forEach { node ->
      when (Dom.getNodeName(node))
      {
        "book" ->
        {
          book = node["code"]!!
          node["sid"] = book
          node -= "style"
        }


        "chapter" ->
        {
          if ("number" in node)
          {
            chapter = node["number"]!!
            node["sid"] = "$book $chapter"
          }

          node -= "style"
          node -= "number"
        }


        "verse" ->
        {
          if ("number" in node)
            node["sid"] = "$book $chapter:${node["number"]}"

          node -= "style"
          node -= "number"
        }
      }
    }
  }
}