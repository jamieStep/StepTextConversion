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
    m_FileProtocol = dataCollection.getFileProtocol()
    Dbg.withProcessingBooks("Tidying chapters and creating missing ones if necessary ...") {
      dataCollection.getRootNodes().forEach {
        Dbg.withProcessingBook(it["osisID"]!!) {
          insertMissingChapters(it)
          tidyChapters(it)
        }
      }
    }
  }
  




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Just occasionally we may have books which lack chapters -- particularly
     (exclusively?) in the DC.  For example, in the RV text, EsthGr starts at
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
      if (null != footnoteNode) IssueAndInformationRecorder.addGeneratedFootnote(newRef.toString() + " (AddedChapter)")
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
  private fun makeEnclosingTags (parentNode: Node, tagNameToBeProcessed: String)
  {
    /***************************************************************************/
    /* Create a dummy node to make processing more uniform. */

    val dummyNode = Dom.createNode(parentNode.ownerDocument, "<$tagNameToBeProcessed _dummy_='y'/>")
    parentNode.appendChild(dummyNode)



    /***************************************************************************/
    /* Locate the nodes to be processed within the overall collection. */

    val allNodes = Dom.getAllNodesBelow(parentNode)
    val indexes: MutableList<Int> = mutableListOf()
    allNodes.indices
      .filter { tagNameToBeProcessed == Dom.getNodeName(allNodes[it]) }
      .forEach { indexes.add(it) }



    /***************************************************************************/
    /* Turn things into enclosing nodes. */

    for (i in 0..< indexes.size - 1)
    {
      val targetNode = allNodes[indexes[i]]
      val targetNodeParent = Dom.getParent(targetNode)!!
      for (j in indexes[i] + 1 ..< indexes[i + 1])
      {
        val thisNode = allNodes[j]
        if (targetNodeParent == Dom.getParent(thisNode))
        {
          Dom.deleteNode(thisNode)
          targetNode.appendChild(thisNode)
        }
      }
    }



    /***************************************************************************/
    Dom.deleteNode(dummyNode)
  }


  /****************************************************************************/
  /* Turns div:chapter into chapter.  Turns milestone chapters into enclosing
     chapters.  Makes osisID and sID the same. */

  private fun tidyChapters (rootNode: Node)
  {
    /**************************************************************************/
    val doc = rootNode.ownerDocument



    /**************************************************************************/
    /** Make osisID and sID uniform. */

    val chapters = Dom.findNodesByName(doc, "chapter")
    chapters.forEach {
      if ("osisID" in it && "sID" !in it)
        it["sID"] = it["osisID"]!!
      else if ("sID" in it && "osisID" !in it)
        it["osisID"] = it["sID"]!!
    }



    /**************************************************************************/
    makeEnclosingTags(rootNode, "chapter")
    Dom.findNodesByAttributeName(doc, "chapter","eID"). forEach(Dom::deleteNode)



    /**************************************************************************/
    //Dbg.d(doc)
  }


  /****************************************************************************/
  private lateinit var m_FileProtocol: X_FileProtocol
}
