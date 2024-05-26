package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.ref.RefRange
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Carries out validation (and simple corrections).
 *
 * The configuration parameters stepReversificationType and stepOsis2modType
 * must have been set before this processing is invoked.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_BasicValidator (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun myPrerequisites () = listOf(ProcessRegistry.EnhancedVerseEndPositioning)
  override fun thingsIveDone () = listOf(ProcessRegistry.BasicValidation)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                     General checks and corrections                     **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks for some basic things which may be adrift (like the text
     containing subverses or lacking verses).  Depending upon the circumstances,
     some of these we may be able to remedy; others we may simply have to
     report, and abandon further processing. */

  fun finalValidationAndCorrection ()
  {
    finalValidationAndCorrection(m_DataCollection)
    validationForOrderingAndHoles(m_DataCollection)
  }


  /****************************************************************************/
  private fun finalValidationAndCorrection (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    val bibleStructure = dataCollection.getBibleStructure()



    /**************************************************************************/
    /* If we're using our own osis2mod, then subverses are ok.  If not, I don't
       think we can handle subverses. */

    if ("step" != ConfigData["stepOsis2modType"])
    {
      if (bibleStructure.hasSubverses())
      {
        Logger.error("Text contains subverses, but that would require us to use our own version of osis2mod: " + bibleStructure.getAllSubverses().joinToString(", "){ Ref.rd(it).toString() } )
        return
      }
    }



    /**************************************************************************/
    /* If we are applying conversion-time restructuring, we must end up with
       something which is entirely NRSV(A)-compliant.  It must also contain
       no subverses or missing verses, but I think the test for NRSVA-compliance
       should address that anyway. */

    if ("conversiontime" == ConfigData["stepReversificationType"]!!.lowercase())
    {
      val nrsvx = BibleStructure.makeOsis2modNrsvxSchemeInstance(bibleStructure)
      dataCollection.getBookNumbers().forEach {
        BibleStructure.compareWithGivenScheme(it, bibleStructure, nrsvx).chaptersInTargetSchemeButNotInTextUnderConstruction.forEach { refKey -> Logger.error(refKey, "Chapter in NRSV(A) but not in supplied text.") }
        BibleStructure.compareWithGivenScheme(it, bibleStructure, nrsvx).chaptersInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error(refKey, "Chapter in in supplied text but not in NRSV(A).") }
        BibleStructure.compareWithGivenScheme(it, bibleStructure, nrsvx).versesInTargetSchemeButNotInTextUnderConstruction.forEach   { refKey -> Logger.error(refKey, "Verse in NRSV(A) but not in supplied text.") }
        BibleStructure.compareWithGivenScheme(it, bibleStructure, nrsvx).chaptersInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error(refKey, "Verse in supplied text but not in NRSV(A).") }
      }

      Logger.announceAll(true)
      return
    }



    /**************************************************************************/
    /* If we're not restructuring, it's ok to have missing verses at this point,
       but we must now fill them all in. */

    if (EmptyVerseHandler(dataCollection).createEmptyVersesForMissingVerses(dataCollection))
      dataCollection.reloadBibleStructureFromRootNodes(false)



    /**************************************************************************/
    /* Unless we're using our own version of osis2mod, we need to be reasonably
       well aligned with whatever versification scheme has been selected. */

    if ("step" != ConfigData["stepOsis2modType"])
    {
      val schemeName = ConfigData["stepVersificationScheme"]!!
      val scheme = BibleStructure.makeOsis2modSchemeInstance(schemeName)
      dataCollection.getBookNumbers().forEach { bookNo ->
        BibleStructure.compareWithGivenScheme(bookNo, bibleStructure, scheme).chaptersInTargetSchemeButNotInTextUnderConstruction.forEach { refKey -> Logger.warning(refKey, "Chapter in $schemeName but not in supplied text.") }
        BibleStructure.compareWithGivenScheme(bookNo, bibleStructure, scheme).chaptersInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error  (refKey, "Chapter in supplied text but not in $schemeName.") }
        BibleStructure.compareWithGivenScheme(bookNo, bibleStructure, scheme).versesInTextUnderConstructionButNotInTargetScheme  .forEach { refKey -> Logger.error  (refKey, "Verse in supplied text but not in $schemeName.") }

        val missingVerses = BibleStructure.compareWithGivenScheme(bookNo, bibleStructure, scheme).versesInTargetSchemeButNotInTextUnderConstruction.toSet()
        val commonlyMissingVerses = BibleAnatomy.getCommonlyMissingVerses().toSet()
        val reportableMissings = missingVerses - commonlyMissingVerses
        val nonReportableMissings = commonlyMissingVerses - reportableMissings

        val missingsAsString = if (missingVerses.isEmpty()) "" else missingVerses.sorted().joinToString(", "){ Ref.rd(it).toString("bcv") }
        val nonReportableMissingsAsString = if (nonReportableMissings.isEmpty()) "" else ("  (The following verse(s) are absent in many Bibles, and therefore are not of concern: " + nonReportableMissings.sorted().joinToString(", "){ Ref.getV(it).toString() } + ".)")

        if (missingVerses.isNotEmpty())
        {
          val missingMsg = "Text lacks verse(s) which target versification scheme expects: $missingsAsString$nonReportableMissingsAsString"
          Logger.info(missingMsg)
        }
      }

      Logger.announceAll(true)
      return
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Structural checks                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks the basis structure -- are all chapters within books, all verses
     within chapters, etc. */

  fun structuralValidation ()
  {
    m_DataCollection.getRootNodes().forEach { structuralValidationForBook(it) }

    if (m_ChaptersWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + m_ChaptersWithBadIds.joinToString(", "))

    if (m_VersesWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + m_VersesWithBadIds.joinToString(", "))

    if (m_ChaptersWithBadBookAncestor.isNotEmpty())
      Logger.error("Chapters which are not under a book, or are under the wrong book: " + m_ChaptersWithBadBookAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (m_VersesWithBadChapterAncestor.isNotEmpty())
      Logger.error("Verses which are not under a chapter, or are under the wrong chapter: " + m_VersesWithBadChapterAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (m_VersesWithBadChapterAncestor.isNotEmpty())
      Logger.error("Verses which are not under a chapter, or are under the wrong chapter: " + m_VersesWithBadChapterAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (m_VersesWhereSidAndEidDoNotAlternate.isNotEmpty())
      Logger.error("Locations where verse sids and eids do not alternate: " + m_VersesWhereSidAndEidDoNotAlternate.joinToString(", "){ Ref.rd(it).toString() })

    if (m_VersesWhereSidAndEidDoNotMatch.isNotEmpty())
      Logger.error("Locations where verse sids and eids do not match: " + m_VersesWhereSidAndEidDoNotMatch.joinToString(", "){ Ref.rd(it).toString() })

    if (m_ElidedSubverses.isNotEmpty())
      Logger.warning("Elided subverses: " + m_ElidedSubverses.joinToString(", "){ RefRange.rdUsx(it).toString() })
  }


  /****************************************************************************/
  private fun structuralValidationForBook (bookNode: Node)
  {
    val bookNo = m_FileProtocol.readRef(m_FileProtocol.getBookAbbreviation(bookNode)).getB()
    Dom.findNodesByName(bookNode, m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      if (!Dom.hasAsAncestor(chapterNode, bookNode))
        m_ChaptersWithBadBookAncestor.add(m_FileProtocol.readRef(chapterNode[m_FileProtocol.attrName_chapterSid()]!!).toRefKey())
      else
      {
        val chapterRefKey = getRefKey(chapterNode[m_FileProtocol.attrName_chapterSid()]!!)
        if (0L == chapterRefKey)
          m_ChaptersWithBadIds.add(chapterNode[m_FileProtocol.attrName_chapterSid()]!!)
        else
        {
          if (Ref.getB(chapterRefKey) != bookNo)
            m_ChaptersWithBadBookAncestor.add(chapterRefKey)
          structuralValidationForChapter(chapterNode)
        }
      }
    }
  }


  /****************************************************************************/
  private fun structuralValidationForChapter (chapterNode: Node)
  {
    val chapterRefKey = m_FileProtocol.readRef(chapterNode[m_FileProtocol.attrName_chapterSid()]!!).toRefKey()
    val verseNodes = Dom.findNodesByName(chapterNode, m_FileProtocol.tagName_verse(), false)
    verseNodes.forEach { verseNode ->
      if (!Dom.hasAsAncestor(verseNode, chapterNode))
        m_VersesWithBadChapterAncestor.add(m_FileProtocol.readRef(verseNode[m_FileProtocol.attrName_verseSid()]!!).toRefKey())
      else
      {
        val id = if (m_FileProtocol.attrName_verseSid() in verseNode) m_FileProtocol.attrName_verseSid() else m_FileProtocol.attrName_verseEid()
        val verseRefKey = getRefKey(verseNode[id]!!)
        if (0L == verseRefKey)
          m_VersesWithBadIds.add(chapterNode[m_FileProtocol.attrName_verseSid()]!!)
        else
        {
          checkForElidedSubverses(verseNode)

          if (Ref.rd(verseRefKey).toRefKey_bc() != chapterRefKey)
            m_VersesWithBadChapterAncestor.add(verseRefKey)
        }
      }
    }

    checkVerseSidsAndEidsAlternate(verseNodes)
  }


  /****************************************************************************/
  private fun validationForOrderingAndHoles (dataCollection: X_DataCollection)
  {
    val softReporter: (String) -> Unit = if ("step" == ConfigData["stepOsis2modType"]!!) Logger::warning else Logger::error

    val outOfOrderVerses = dataCollection.getBibleStructure().getOutOfOrderVerses() // I think this will cover chapters too.
    if (outOfOrderVerses.isNotEmpty())
      softReporter("Locations where verses are out of order: " + outOfOrderVerses.joinToString(", "){ Ref.rd(it).toString() })

    if (!dataCollection.getBibleStructure().standardBooksAreInOrder())
      softReporter("OT / NT books are not in order.")

    val missingEmbeddedChapters = dataCollection.getBibleStructure().getMissingEmbeddedChaptersForText()
    if (missingEmbeddedChapters.isNotEmpty())
      Logger.error("Locations where embedded chapters are missing: " + missingEmbeddedChapters.joinToString(", "){ Ref.rd(it).toString() })

    val missingEmbeddedVerses = dataCollection.getBibleStructure().getMissingEmbeddedVersesForText()
    if (missingEmbeddedVerses.isNotEmpty())
      Logger.error("Locations where embedded verses are missing: " + missingEmbeddedVerses.joinToString(", "){ Ref.rd(it).toString() })

    val duplicateVerses = dataCollection.getBibleStructure().getDuplicateVersesForText()
    if (duplicateVerses.isNotEmpty())
      Logger.error("Locations where we have duplicate verses: " + duplicateVerses.joinToString(", "){ Ref.rd(it).toString() })
  }


  /****************************************************************************/
  private fun checkForElidedSubverses (node: Node)
  {
    val sid = node[m_FileProtocol.attrName_verseSid()] ?: return
    if ('-' !in sid) return
    val rc = m_FileProtocol.readRefCollection(sid)
    if (rc.getLowAsRef().hasS() || rc.getHighAsRef().hasS())
      m_ElidedSubverses.add(rc.toString())
  }


  /****************************************************************************/
  private fun checkVerseSidsAndEidsAlternate (verseNodes: List<Node>)
  {
    var expectedId: String? = null
    verseNodes.forEach { verseNode ->
      if (m_FileProtocol.attrName_verseSid() in verseNode)
      {
        if (null != expectedId)
        {
          m_VersesWhereSidAndEidDoNotAlternate.add(m_FileProtocol.readRef(verseNode[m_FileProtocol.attrName_verseSid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        expectedId = m_FileProtocol.readRefCollection(verseNode[m_FileProtocol.attrName_verseSid()]!!).toString()
      }

      else // eid
      {
        if (null == expectedId)
        {
          m_VersesWhereSidAndEidDoNotAlternate.add(m_FileProtocol.readRef(verseNode[m_FileProtocol.attrName_verseEid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        if (expectedId != m_FileProtocol.readRefCollection(verseNode[m_FileProtocol.attrName_verseEid()]!!).toString())
          m_VersesWhereSidAndEidDoNotMatch.add(m_FileProtocol.readRef(verseNode[m_FileProtocol.attrName_verseEid()]!!).toRefKey())

        expectedId = null
      } // eid
    }
  }


  /****************************************************************************/
  private val m_ChaptersWithBadIds: MutableList<String> = mutableListOf()
  private val m_VersesWithBadIds: MutableList<String> = mutableListOf()
  private val m_ElidedSubverses: MutableList<String> = mutableListOf()
  private val m_ChaptersWithBadBookAncestor: MutableList<RefKey> = mutableListOf()
  private val m_VersesWithBadChapterAncestor: MutableList<RefKey> = mutableListOf()
  private val m_VersesWhereSidAndEidDoNotAlternate: MutableList<RefKey> = mutableListOf()
  private val m_VersesWhereSidAndEidDoNotMatch: MutableList<RefKey> = mutableListOf()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Attempts to read an id (sid / eid) and returns either the resulting refkey
     (or the first refkey if this is a range), or 0 as an error indicator. */

  private fun getRefKey (id: String): RefKey
  {
    try
    {
      return m_FileProtocol.readRefCollection(id).getFirstAsRefKey()
    }
    catch (_: Exception)
    {
      return 0
    }
  }
}
