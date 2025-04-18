package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefCollection
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.w3c.dom.Node


/****************************************************************************/
/**
 * End-of-processing validation.  Used to pick up things which have caused
 * us grief at one point or another, and which we need to be certain are no
 * longer present.
 *
 * @author ARA "Jamie" Jamieson
 */

object _UNUSED_PA_FinalValidator: PA(), ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    Rpt.reportWithContinuation(level = 1, "Final health check ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable { PA_FinalValidatorPerBook(m_FileProtocol).processRootNode(rootNode) }
          } // forEach
        } // run
      } // parallel
    } // report
  } // fun
} // object




/******************************************************************************/
private class PA_FinalValidatorPerBook (val m_FileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    /**************************************************************************/
    Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
    checkForCrossBoundaryMarkup(rootNode)
    checkForSubversesAndMissingVerses(rootNode)
    checkForNotesOutsideOfVerses(rootNode)



    /**************************************************************************/
    fun outOfOrderError  (refKey: RefKey, text: String) = Logger.error   (refKey, text)
    fun outOfOrderWarning (refKey: RefKey, text: String) = Logger.warning(refKey, text)
    val outOfOrderReporter = if (ConfigData.getAsBoolean("stepValidationReportOutOfOrderAsError", "y")) ::outOfOrderError else ::outOfOrderWarning



    /**************************************************************************/
    m_Subverses.forEach { Logger.error( it.toRefKey(), "Subverse.") }
    m_MismatchedEidsAndSids.forEach { Logger.error( it.getFirstAsRefKey(),"Mismatched sid/eid near here.") }
    m_MissingVerses.forEach { outOfOrderReporter(it.toRefKey(), "Missing / out of order verse.") }
    m_MissingSubverses.forEach { outOfOrderReporter(it.toRefKey(), "Missing / out of order subverse.") }
  }


  /****************************************************************************/
  private fun checkForCrossBoundaryMarkup (rootNode: Node)
  {
    fun checkChapter (chapter: Node)
    {
      //Dbg.d(rootNode.ownerDocument)
      val verses = chapter.findNodesByName("verse")
      for (i in verses.indices step 2)
        if (!Dom.isSiblingOf(verses[i], verses[i + 1]))
          IssueAndInformationRecorder.crossVerseBoundaryMarkup(Ref.rdOsis(verses[i]["sID"]!!).toRefKey())
    }

    rootNode.findNodesByName("chapter").forEach(::checkChapter)
  }


  /****************************************************************************/
  private fun checkForNotesOutsideOfVerses (rootNode: Node)
  {
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { checkForNotesOutsideOfVerses_1(it) }
  }


  /****************************************************************************/
  private fun checkForNotesOutsideOfVerses_1 (chapterNode: Node)
  {
    var canonicalTitleNode: Node? = null
    var inVerse = false
    val problems: MutableSet<RefKey> = mutableSetOf()
    var chapterRefKey = Ref.rdOsis(chapterNode[m_FileProtocol.attrName_chapterSid()]!!).toRefKey()

    Dom.getAllNodesBelow(chapterNode).forEach {
      if (m_FileProtocol.isCanonicalTitleNode(it))
      {
        canonicalTitleNode = it
        return@forEach
      }


      if (m_FileProtocol.tagName_note() == Dom.getNodeName(it))
      {
        if (!inVerse && (null == canonicalTitleNode || !Dom.isAncestorOf(canonicalTitleNode!!, it))) // This assumes that notes inside canonical titles will work and therefore don't represent errors.  If they _don't_ work, remove the ancestor processing here and change SE_LastDitchTidier to delete notes from canonical titles.
        {
          //Dbg.d(chapterNode[m_FileProtocol.attrName_chapterSid()]!! + ": " + it.textContent)
          problems.add(chapterRefKey)
        }
      }

      else if (m_FileProtocol.tagName_verse() == Dom.getNodeName(it))
      {
        if (m_FileProtocol.attrName_verseEid() in it)
          inVerse = false
        else
        {
          inVerse = true
          chapterRefKey = RefCollection.rdOsis(chapterNode[m_FileProtocol.attrName_chapterSid()]!!).getFirstAsRefKey()
        }
      }
    }

    //problems.forEach { Logger.error(it, "Notes outside of verse at this location or nearby.")}
  }


  /****************************************************************************/
  private fun checkForSubversesAndMissingVerses (rootNode: Node)
  {
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { checkForSubversesAndMissingVerses_1(it) }
  }


  /****************************************************************************/
  private fun checkForSubversesAndMissingVerses_1 (chapterNode: Node)
  {
    /**************************************************************************/
    //Dbg.d(chapterNode.ownerDocument)
    //Dbg.dCont(Dom.toString(chapterNode), "7")



    /**************************************************************************/
    var currentId = "Dummy"
    var expectedVerse = 1
    var expectedSubverse = 1



    /**************************************************************************/
    Dom.findNodesByName(chapterNode, m_FileProtocol.tagName_verse(), false)
      .forEach {
        /**********************************************************************/
        /* At eid check alternation of sid and eid. */

        if (m_FileProtocol.attrName_verseEid() in it)
        {
          if (it[m_FileProtocol.attrName_verseEid()] != currentId)
            m_MismatchedEidsAndSids.add(RefCollection.rdOsis(it[m_FileProtocol.attrName_verseEid()]!!))
          currentId = "Dummy"
          return@forEach
        }



        /**********************************************************************/
        /* Check sid / eid interleaving. */

        val sid = it[m_FileProtocol.attrName_verseSid()]!!
        if ("Dummy" != currentId)
          m_MismatchedEidsAndSids.add(RefCollection.rdOsis(sid))
        currentId = sid



        /**********************************************************************/
        /* Check expected numbers. */

        RefCollection.rdOsis(sid).getAllAsRefs().forEach { ref ->
          /********************************************************************/
          /* Subverse. */

          if (ref.hasS())
          {
            m_Subverses.add(ref)

            val subverse = ref.getS()

            if (subverse != expectedSubverse && 1 != subverse) // Can either continue from most recent subverse, or restart from 1.
              m_MissingSubverses.add(ref)

            expectedSubverse = ref.getS() + 1
          } // ref.hasS()



         /********************************************************************/
         /* Verse checking -- do this even if we're actually dealing with a
            subverse.  The verse is ok if it matches expectations (which
            are always one more than the previous verse), or, if this is a
            subverse, if it matches the previous verse. */

          val verse = ref.getV()
          var badVerse = verse != expectedVerse
          if (badVerse && ref.hasS())
            badVerse = verse != expectedVerse - 1

          if (badVerse)
            m_MissingVerses.add(ref)

          expectedVerse = verse + 1
          if (!ref.hasS())
            expectedSubverse = 1
        } // forEach ref
    } // forEach verse tag
  }


  /****************************************************************************/
  private val m_MismatchedEidsAndSids: MutableList<RefCollection> = mutableListOf()
  private val m_MissingSubverses: MutableList<Ref> = mutableListOf()
  private val m_MissingVerses: MutableList<Ref> = mutableListOf()
  private val m_Subverses: MutableList<Ref> = mutableListOf()
}
