package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.applicationspecificutils.IssueAndInformationRecorder
import org.stepbible.textconverter.applicationspecificutils.Permissions
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.applicationspecificutils.X_FileProtocol
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefCollection
import org.stepbible.textconverter.protocolagnosticutils.PA_Utils.convertToEnclosingTags
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Applies any necessary pre-processing to the input OSIS to sort out the
 * basic chapter and verse structure
 *
 * @author ARA Jamieson
 */

object Osis_ChapterAndVerseStructurePreprocessor
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
  * Carries out general tidying.
  * 
  * @param dataCollection Data to be processed.
  */
  
  fun process (dataCollection: X_DataCollection)
  {
    Rpt.reportWithContinuation(level = 1, "Tidying chapters and creating missing ones if necessary ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            Rpt.reportBookAsContinuation(rootNode["osisID"]!!)
            asyncable { Osis_ChapterAndVerseStructurePreprocessorPerBook(dataCollection.getFileProtocol()).processRootNode(rootNode) }
          } // forEach
        } // run
      } // with
    } // withProcessingBook
  } // fun
}





/******************************************************************************/
private class Osis_ChapterAndVerseStructurePreprocessorPerBook (val m_FileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    insertMissingChapters(rootNode)
    tidyChapters(rootNode)
  }


  /****************************************************************************/
  /* Just occasionally we may have books which lack chapters -- particularly
     (exclusively?) in the DC.  For example, in the RV text, AddEsth starts at
     chapter 10 (actually at 10:4).  Having missing chapters appears to be a
     no-no, so this processing creates any missing chapters. */

  private fun insertMissingChapters (rootNode: Node)
  {
    /**************************************************************************/
    val doc = rootNode.ownerDocument



    /**************************************************************************/
    fun addChapter (chapterNo: Int, firstExistingChapterNode: Node, ref: Ref)
    {
      val newRef = Ref.rd(ref)
      newRef.setC(chapterNo)
      newRef.clearV()

      val newChapterNode = doc.createNode("<chapter/>")
      newChapterNode["osisID"] = newRef.toStringOsis("bc")
      newChapterNode["sID"] = newChapterNode["osisID"]!!

      val newVerseSidNode = doc.createNode("<verse/>")
      newRef.setV(1)
      newVerseSidNode["osisID"] = newRef.toStringOsis()
      newVerseSidNode["sID"] = newRef.toStringOsis()

      val footnoteNode = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToVerseGeneratedBecauseWeNeedToAddAChapter, doc, newRef.toRefKey(), TranslatableFixedText.stringFormat(Language.Vernacular, "V_emptyContentFootnote_chapterMissingInThisTranslation"))
      val textNode = doc.createTextNode(TranslatableFixedText.stringFormatWithLookup("V_contentForEmptyVerse_verseWasMissing"))

      val newVerseEidNode = doc.createNode("<verse/>")
      newVerseEidNode["eID"] = newRef.toStringOsis()

      newChapterNode.appendChild(newVerseSidNode)
      if (null != footnoteNode) newChapterNode.appendChild(footnoteNode)
      newChapterNode.appendChild(textNode)
      newChapterNode.appendChild(newVerseEidNode)

      Dom.insertNodeBefore(firstExistingChapterNode, newChapterNode)

      IssueAndInformationRecorder.addChapterWhichWasMissingInTheRawText(newRef.toString())
      if (null != footnoteNode) IssueAndInformationRecorder.addGeneratedFootnote("$newRef (AddedChapter)")
    }



    /**************************************************************************/
    val firstExistingChapterNode = rootNode.findNodeByName("chapter", false)!!
    val ref = Ref.rdOsis((firstExistingChapterNode["osisID"] ?: firstExistingChapterNode["sID"])!!)
    val firstChapterNo = ref.getC()
    val newRef = Ref.rdOsis(rootNode["osisID"]!!)

    for (chapterNo in 1 ..< firstChapterNo)
    {
      newRef.setC(chapterNo)
      Logger.info(newRef.toRefKey(), "Created chapter which was missing from the original text.")
      addChapter(chapterNo, firstExistingChapterNode, ref)
    }

    //Dbg.d(doc)
  }


  /****************************************************************************/
  /* Turns div:chapter into chapter.  Turns milestone chapters into enclosing
     chapters.  Makes osisID and sID the same. */

  private fun tidyChapters (rootNode: Node)
  {
    /**************************************************************************/
    val doc = rootNode.ownerDocument



    /**************************************************************************/
    /* Make osisID and sID uniform.  This is a frustratingly fiddly piece of
       code, which may well still be wrong or inadequate.  It responds to the
       fact that different texts do different things in terms of osisID, sID
       and eID, and it is convenient for the sake of uniformity to have
       an osisId as well as a sID or an eID, and to have all of them contain
       the actual reference (the OSIS spec does not require this -- sID and
       eID can be arbitrary strings, so long as the sID and eID of an element
       are the same as each other and are otherwise unique). */

    val chapters = Dom.findNodesByName(doc, "chapter")
    chapters.forEach {
      if (it.hasChildNodes() && "osisID" in it && "sID" !in it) // An enclosing chapter which has an osisID and no sID.  Use the osisID as the sID.
      {
        it["sID"] = it["osisID"]!!
        return@forEach
      }


      val isSid = "sID" in it
      val sidOrEidSelector = if (isSid) "sID" else "eID"
      val sidOrEidRefAsString = it[sidOrEidSelector]!!

      val sidOrEidRef = RefCollection.rdOsisOrNull(sidOrEidRefAsString)

      if (null == sidOrEidRef)
        it[sidOrEidSelector] = it["osisID"]!!
      else
        it["osisID"] = sidOrEidRefAsString
    }



    /**************************************************************************/
    convertToEnclosingTags(rootNode, "chapter")
    Dom.findNodesByAttributeName(doc, "chapter","eID"). forEach(Dom::deleteNode)



    /**************************************************************************/
    //Dbg.d(doc)
  }
}
