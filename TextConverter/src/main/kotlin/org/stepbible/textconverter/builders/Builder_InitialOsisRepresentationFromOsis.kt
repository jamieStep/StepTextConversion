package org.stepbible.textconverter.builders

import org.stepbible.textconverter.protocolagnosticutils.PA_BasicVerseEndInserter
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodeByName
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodesByAttributeValue
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.get
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import java.io.File



/******************************************************************************/
/**
  * Makes existing OSIS available in transformed form.
  *
  * This reads from the InputOsis folder.  The data there represents what I
  * have referred to elsewhere as 'external-facing' OSIS, and has to be
  * retained as-is for potential future use.
  *
  * The result of the processing here supplied as a DOM to the next stage of
  * processing.  It differs from the external OSIS in that it may have been
  * preprocessed using regexes or XSLT fragments, and that a minimal amount of
  * standardisation has been applied (eg to convert div:chapter to chapter).
  *
  * @author ARA "Jamie" Jamieson
  */

object Builder_InitialOsisRepresentationFromOsis: Builder ()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Building initial representation from OSIS."
  override fun commandLineOptions () = null





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Note that (unlike with other Builder_InitialOsisRepresentationFrom...
     builders), we already _have_ something which is to be retained for
     future use as the external-facing OSIS, so nothing below should be
     writing to a file. */

  override fun doIt ()
  {
    Dbg.withReportProgressMain(banner(), ::doIt1)
  }



  /****************************************************************************/
  private fun doIt1 ()
  {
    /**************************************************************************/
    var text =
      File(FileLocations.getInputOsisFilePath()!!).readText()
        .replace("\u000c", "") // DIB uses this in his OSIS because it improves the formatting in the text editor.  However, it's not valid XML.
        .replaceFirst("\\<osis .*?\\>", "<osis>") // OSIS files often have standard xmlns attributes on the <osis> tag.  None of the standard locations still exists, and having namespace information messes up the processing.



    /**************************************************************************/
    text = BuilderUtils.processRegexes(text, ConfigData.getPreprocessingRegexes())
    val doc = Dom.getDocumentFromText(text, true)



    /**************************************************************************/
    /* If this run is to be limited to certain books only, filter out the ones
       which are not of interest. */

    if (Dbg.runningOnPartialCollectionOfBooksOnly())
    {
      var retainedBooks = 0
      Dbg.withReportProgressSub("Filtering for books selected for processing.") {
        doc.findNodesByAttributeValue("div", "type", "book").forEach {
          val abbreviatedName = it["osisID"]!!
          if (Dbg.wantToProcessBook(BibleBookNamesOsis.abbreviatedNameToNumber(abbreviatedName)))
            ++retainedBooks
          else
            Dom.deleteNode(it)
        }
      }

      if (0 == retainedBooks)
        throw StepExceptionWithoutStackTraceAbandonRun("Book-filtering has removed all books from the text.")
    }



    /**************************************************************************/
    /* OSIS permits chapters to be marked as div:chapter or <chapter>.  There
       is no harm in forcing the latter, and it makes later processing more
       uniform. */

    Dbg.withReportProgressSub("Changing div:chapter nodes into <chapter>.")  {
      doc.findNodesByAttributeValue("div", "type", "chapter").forEach {
        Dom.setNodeName(it, "chapter")
        Dom.deleteAttribute(it, "type")
      }
    }



    /**************************************************************************/
    /* Add verse ends if not already present.  This means that the OSIS will be
       aligned with OSIS generated by any of the other classes -- eg from
       USX. */

    val dataCollection = Osis_DataCollection()
    dataCollection.loadFromDocs(listOf(doc))
    PA_BasicVerseEndInserter.process(dataCollection)
    Dom.deleteAllAttributes(doc.findNodeByName("osis")!!) // This removes xmlns, which confuses our processing, and doesn't seem to add anything.



    /**************************************************************************/
    ExternalOsisDoc = BuilderUtils.processXslt(doc)
  }
}
