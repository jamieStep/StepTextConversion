package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.contains
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.get
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefRange
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.protocolagnosticutils.PA_EmptyVerseHandler
import org.stepbible.textconverter.applicationspecificutils.Osis_FileProtocol
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.w3c.dom.Node

/*******************************************************************************/
/**
 * Carries out OSIS validation (and simple corrections).
 *
 * The configuration parameters stepReversificationType and stepOsis2modType
 * must have been set before this processing is invoked.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_BasicValidator
{
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

  fun finalValidationAndCorrection (dataCollection: X_DataCollection)
  {
    Dbg.withReportProgressSub("Performing final validation and correction.") {
      finalValidationAndCorrection1(dataCollection)
    }

    Dbg.withReportProgressSub("Checking for missing verses.") {
      validationForOrderingAndHoles1(dataCollection)
    }
  }


  /****************************************************************************/
  private fun finalValidationAndCorrection1 (dataCollection: X_DataCollection)
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
        val comparison = BibleStructure.compareWithGivenScheme(it, bibleStructure, nrsvx)
        comparison.chaptersInTargetSchemeButNotInTextUnderConstruction.forEach { refKey -> Logger.error (refKey, "Chapter in NRSV(A) but not in supplied text.") }
        comparison.chaptersInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error (refKey, "Chapter in supplied text but not in NRSV(A).") }
        comparison.versesInTargetSchemeButNotInTextUnderConstruction  .forEach { refKey -> Logger.error (refKey, "Verse in NRSV(A) but not in supplied text.") }
        comparison.versesInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error (refKey, "Verse in supplied text but not in NRSV(A).") }
        comparison.versesInTextUnderConstructionOutOfOrder            .forEach { refKey -> Logger.warning(refKey,"Verse out of order.") }
      }

      Logger.announceAll(true)
      return
    }



    /**************************************************************************/
    /* If we're not restructuring, it's ok to have missing verses at this point,
       but we must now fill them all in. */

    if (PA_EmptyVerseHandler(dataCollection.getFileProtocol()).createEmptyVersesForMissingVerses(dataCollection))
      dataCollection.reloadBibleStructureFromRootNodes(true)



    /**************************************************************************/
    /* Unless we're using our own version of osis2mod, we need to be reasonably
       well aligned with whatever versification scheme has been selected.

       At one time I regarded it as an error if the target scheme made no
       provision for a verse present in the supplied text.  However, on a
       non-Samification run we _have_ to use a Crosswire scheme even if
       it's not an ideal fit, so I've had to reduce it to a warning. */

    if ("step" != ConfigData["stepOsis2modType"])
    {
      val schemeName = ConfigData["stepVersificationScheme"]!!
      val scheme = BibleStructure.makeOsis2modSchemeInstance(schemeName)
      dataCollection.getBookNumbers().forEach { bookNo ->
        val comparison = BibleStructure.compareWithGivenScheme(bookNo, bibleStructure, scheme)
        comparison.chaptersInTargetSchemeButNotInTextUnderConstruction.forEach { refKey -> Logger.warning(refKey, "Chapter in $schemeName but not in supplied text.") }
        comparison.chaptersInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error  (refKey, "Chapter in supplied text but not in $schemeName.") }
        comparison.versesInTextUnderConstructionButNotInTargetScheme  .forEach { refKey -> Logger.warning(refKey, "Verse in supplied text but not in $schemeName.") }
        comparison.versesInTextUnderConstructionOutOfOrder            .forEach { refKey -> Logger.warning (refKey, "Verse out of order.") }

        val missingVerses = comparison.versesInTargetSchemeButNotInTextUnderConstruction.toSet()
        val commonlyMissingVerses = BibleAnatomy.getCommonlyMissingVerses().toSet()
        val unexpectedMissings = missingVerses - commonlyMissingVerses
        val expectedMissings = missingVerses intersect commonlyMissingVerses

        val unexpectedMissingsAsString = if (unexpectedMissings.isEmpty()) "" else "The text lacks the following verse(s) which the target versification scheme expects: " + unexpectedMissings.sorted().joinToString(", "){ Ref.rd(it).toString("bcv") }
        val expectedMissingsAsString   = if (expectedMissings.isEmpty())   "" else "The text lacks the following verse(s) which the target versification scheme expects.  However, many texts lack these verses, so this is not of particular concern.: " + expectedMissings.sorted().joinToString(", "){ Ref.rd(it).toString() }
        val msg = (unexpectedMissingsAsString + expectedMissingsAsString).trim()
        if (msg.isNotEmpty())
           Logger.info(msg)
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
  /**
   *  Checks the basis structure -- are all chapters within books, all verses
   *  within chapters, etc.
   *
   *  @param dataCollection: Data to be processed.
   */

  fun structuralValidation (dataCollection: X_DataCollection)
  {
    Dbg.withProcessingBooks("Performing structural validation for ...") {
      dataCollection.getRootNodes().forEach { structuralValidationForBook(it) }
    }

    if (m_ChaptersWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + m_ChaptersWithBadIds.joinToString(", "))

    if (m_VersesWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + m_VersesWithBadIds.joinToString(", "))

    if (m_ChaptersWithBadBookAncestor.isNotEmpty())
      Logger.error("Chapters which are not under a book, or are under the wrong book: " + m_ChaptersWithBadBookAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (m_VersesWithBadChapterAncestor.isNotEmpty())
      Logger.error("Verses which are under the wrong chapter: " + m_VersesWithBadChapterAncestor.toSet().joinToString(", "){ Ref.rd(it).toString() })

    if (m_VersesWithNoChapterAncestor.isNotEmpty())
      Logger.error("Verses which are not under a chapter: " + m_VersesWithNoChapterAncestor.joinToString(", "){ Ref.rd(it).toString() })

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
    Dbg.withProcessingBook(Osis_FileProtocol.getBookAbbreviation(bookNode)) {
      structuralValidationForBook1(bookNode)
    }
  }


  /****************************************************************************/
  private fun structuralValidationForBook1 (bookNode: Node)
  {
    val bookNo = Osis_FileProtocol.readRef(Osis_FileProtocol.getBookAbbreviation(bookNode)).getB()
    Dom.findNodesByName(bookNode, Osis_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      if (!Dom.hasAsAncestor(chapterNode, bookNode))
        m_ChaptersWithBadBookAncestor.add(Osis_FileProtocol.readRef(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!).toRefKey())
      else
      {
        val chapterRefKey = getRefKey(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!)
        if (0L == chapterRefKey)
          m_ChaptersWithBadIds.add(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!)
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
    val chapterRefKey = Osis_FileProtocol.readRef(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!).toRefKey()
    val verseNodes = Dom.findNodesByName(chapterNode, Osis_FileProtocol.tagName_verse(), false)
    verseNodes.forEach { verseNode ->
      if (!Dom.hasAsAncestor(verseNode, chapterNode))
        m_VersesWithNoChapterAncestor.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toRefKey())
      else
      {
        val id = if (Osis_FileProtocol.attrName_verseSid() in verseNode) Osis_FileProtocol.attrName_verseSid() else Osis_FileProtocol.attrName_verseEid()
        val verseRefKey = getRefKey(verseNode[id]!!)
        if (0L == verseRefKey)
          m_VersesWithBadIds.add(chapterNode[Osis_FileProtocol.attrName_verseSid()]!!)
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
  private fun validationForOrderingAndHoles1 (dataCollection: X_DataCollection)
  {
    val softReporter: (String) -> Unit = if ("step" == ConfigData["stepOsis2modType"]!!) Logger::warning else Logger::error

    val outOfOrderVerses = dataCollection.getBibleStructure().getOutOfOrderVerses() // I think this will cover chapters too.
    if (outOfOrderVerses.isNotEmpty())
    {
      val reporter = if (ConfigData.getAsBoolean("stepValidationReportOutOfOrderAsError", "y")) softReporter else Logger::warning
      reporter("Locations where verses are out of order: " + outOfOrderVerses.joinToString(", "){ Ref.rd(it).toString() })
    }

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
    val sid = node[Osis_FileProtocol.attrName_verseSid()] ?: return
    if ('-' !in sid) return
    val rc = Osis_FileProtocol.readRefCollection(sid)
    if (rc.getLowAsRef().hasS() || rc.getHighAsRef().hasS())
      m_ElidedSubverses.add(rc.toString())
  }


  /****************************************************************************/
  private fun checkVerseSidsAndEidsAlternate (verseNodes: List<Node>)
  {
    var expectedId: String? = null
    verseNodes.forEach { verseNode ->
      if (Osis_FileProtocol.attrName_verseSid() in verseNode)
      {
        if (null != expectedId)
        {
          m_VersesWhereSidAndEidDoNotAlternate.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        expectedId = Osis_FileProtocol.readRefCollection(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toString()
      }

      else // eid
      {
        if (null == expectedId)
        {
          m_VersesWhereSidAndEidDoNotAlternate.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        if (expectedId != Osis_FileProtocol.readRefCollection(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toString())
          m_VersesWhereSidAndEidDoNotMatch.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toRefKey())

        expectedId = null
      } // eid
    }
  }


  /****************************************************************************/
  private val m_ChaptersWithBadBookAncestor: MutableList<RefKey> = mutableListOf()
  private val m_ChaptersWithBadIds: MutableList<String> = mutableListOf()
  private val m_ElidedSubverses: MutableList<String> = mutableListOf()
  private val m_VersesWhereSidAndEidDoNotAlternate: MutableList<RefKey> = mutableListOf()
  private val m_VersesWhereSidAndEidDoNotMatch: MutableList<RefKey> = mutableListOf()
  private val m_VersesWithBadChapterAncestor: MutableList<RefKey> = mutableListOf()
  private val m_VersesWithBadIds: MutableList<String> = mutableListOf()
  private val m_VersesWithNoChapterAncestor: MutableList<RefKey> = mutableListOf()





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
    return try
    {
      Osis_FileProtocol.readRefCollection(id).getFirstAsRefKey()
    }
    catch (_: Exception)
    {
      0
    }
  }
}