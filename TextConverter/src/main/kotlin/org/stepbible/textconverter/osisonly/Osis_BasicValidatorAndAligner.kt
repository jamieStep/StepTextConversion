package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefRange
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.protocolagnosticutils.PA_EmptyVerseHandler
import org.stepbible.textconverter.applicationspecificutils.Osis_FileProtocol
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructureOsis2ModScheme
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.w3c.dom.Node

/*******************************************************************************/
/**
 * Carries out OSIS validation (and simple corrections).
 *
 * The configuration parameter stepReversificationType must have been set before
 * this processing is invoked.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_BasicValidatorAndAligner: ObjectInterface
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

  fun structuralValidationAndCorrection (dataCollection: X_DataCollection)
  {
    Rpt.report(1, "Performing structural validation and correction (no separate per-book reporting for this step).")
    structuralValidationAndCorrection1(dataCollection)

    Rpt.report(1,"Checking for missing verses (no separate per-book reporting for this step).")
    validationForOrderingAndHoles1(dataCollection)
  }


  /****************************************************************************/
  private fun structuralValidationAndCorrection1 (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    val bibleStructure = dataCollection.getBibleStructure()



    /**************************************************************************/
    /* If we are targetting a specific Crosswire scheme, we need to delete any
       books that scheme can't handle.  We will be targetting a specific scheme
       if applying conversion-time reversification (when the scheme will be
       KJV(A)), or if we are not applying reversification (when the scheme
       should have been specified by the user). */

    var targetScheme: BibleStructureOsis2ModScheme? = null
    var targetSchemeName = ""
    val reversificationType = ConfigData["stepReversificationType"]!!.lowercase()
    if ("conversiontime" == reversificationType)
    {
      targetScheme = BibleStructure.makeOsis2modKjvxSchemeInstance(bibleStructure)
      targetSchemeName = "KJV / KJVA"
    }
    else if ("none" == reversificationType)
    {
      val selectedSchemeName = ConfigData["stepVersificationScheme"]
      if (null != selectedSchemeName)
      {
        targetScheme = BibleStructure.makeOsis2modSchemeInstance(selectedSchemeName)
        targetSchemeName = selectedSchemeName
      }
    }



    /**************************************************************************/
    /* If we have a specific scheme in mind, we can't have any books which are
       not in that scheme.  If we find such books, therefore, we drop them and
       report them. */

    if (null != targetScheme)
    {
      val deletedBooks: MutableList<String> = mutableListOf()

      dataCollection.getBookNumbers().forEach {bookNumber ->
        if (!targetScheme.bookExists(bookNumber))
        {
          val badRootNode = dataCollection.getRootNodes()[bookNumber]
          Dom.deleteNode(badRootNode)
          dataCollection.removeNodeFromRootNodeStructure(bookNumber)
          deletedBooks.add(dataCollection.getFileProtocol().getBookAbbreviation(badRootNode))
          return@forEach
        }
      }



      /************************************************************************/
      if (deletedBooks.isNotEmpty())
        ConfigData["stepDeletedBooks"] = deletedBooks.joinToString(", ")
    }



    /**************************************************************************/
    /* If we're not restructuring, it's ok to have missing verses at this point,
       but we must now fill them all in. */

    if (PA_EmptyVerseHandler(dataCollection.getFileProtocol()).createEmptyVersesForMissingVerses(dataCollection))
      dataCollection.reloadBibleStructureFromRootNodes(true)



    /**************************************************************************/
    /* If we're not reversifying, we need to be reasonably well aligned with
       one of the Crosswire versification schemes.

       At one time I regarded it as an error if the target scheme made no
       provision for a verse present in the supplied text.  However, on a
       non-Samification run we _have_ to use a Crosswire scheme even if
       it's not an ideal fit, so I've had to reduce most things here
       to a warning. */

    if ("runtime" != reversificationType)
    {
      dataCollection.getBookNumbers().forEach { bookNo ->
        val comparison = BibleStructure.compareWithGivenScheme(bookNo, bibleStructure, targetScheme!!)
        comparison.chaptersInTargetSchemeButNotInTextUnderConstruction.forEach { refKey -> Logger.warning(refKey, "Chapter in $targetSchemeName but not in supplied text.") }
        comparison.chaptersInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error  (refKey, "Chapter in supplied text but not in $targetSchemeName.") }
        comparison.versesInTextUnderConstructionButNotInTargetScheme  .forEach { refKey -> Logger.warning(refKey, "Verse in supplied text but not in $targetSchemeName.") }
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
    }


    /**************************************************************************/
    Logger.announceAll(true)
  }


  /****************************************************************************/
  private fun validationForOrderingAndHoles1 (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    /* There's a specific config parameter which says if out-of-order verses
       should be reported as an error.  Otherwise it's just a warning.

       FWIW, our own software (targeted at the 'STEP' audience) can cope; the
       Crosswire variant cannot: if presented with out-of-order verses, it
       simply reorders them, and if the translators have deliberately put them
       out of order (which occasionally they do) then that's a problem. */

    val outOfOrderVerses = dataCollection.getBibleStructure().getOutOfOrderVerses() // I think this will cover chapters too.
    if (outOfOrderVerses.isNotEmpty())
    {
      // Dbg.outputDom(dataCollection.getRootNode(24)!!.ownerDocument  )
      val reporter: (String) -> Unit = if (ConfigData.getAsBoolean("stepValidationReportOutOfOrderAsError", "y")) Logger::error else Logger::warning
      reporter("Locations where verses are out of order: " + outOfOrderVerses.joinToString(", "){ Ref.rd(it).toString() })
    }



    /**************************************************************************/
    /* I'm not sure how much out-of-order books matter.  Crosswire will
       definitely simply reorder them.  I don't know about our software.
       Everything should still _work_ either way -- the worst that should
       happen is that the text doesn't follow the required order.  I'm going
       to treat this as just a warning at present. */

    if (!dataCollection.getBibleStructure().standardBooksAreInOrder())
      Logger.warning("OT / NT books are not in order.")



    /**************************************************************************/
    /* Entire embedded chapters missing is definitely a problem -- I doubt
       either version of osis2mod will cope, and I have no processing to create
       dummy chapters (and even if I did, an entire empty chapter is probably
       iffy. */

    val missingEmbeddedChapters = dataCollection.getBibleStructure().getMissingEmbeddedChaptersForText()
    if (missingEmbeddedChapters.isNotEmpty())
      Logger.error("Locations where embedded chapters are missing: " + missingEmbeddedChapters.joinToString(", "){ Ref.rd(it).toString() })



    /**************************************************************************/
    /* Missing embedded verses would be a problem if things were left that way,
       because the osis2mod structure doesn't cope.  However, I have processing
       to fill in any holes -- either because reversification asks for it, or
       else as a backstop at the end of processing.  I think therefore that
       this need only be a backstop.

       Note that missing verses at the _ends_ of chapters are not a problem
       (and it may not always even be apparent if there _are_ such verses,
       because you don't always know what the last verse should be).  I don't
       bother detecting or reporting these, therefore. */

    val missingEmbeddedVerses = dataCollection.getBibleStructure().getMissingEmbeddedVersesForText()
    if (missingEmbeddedVerses.isNotEmpty())
      Logger.warning("Locations where embedded verses are missing: " + missingEmbeddedVerses.joinToString(", "){ Ref.rd(it).toString() })



    /**************************************************************************/
    /* Duplicates are a definite error. */

    val duplicateVerses = dataCollection.getBibleStructure().getDuplicateVersesForText()
    if (duplicateVerses.isNotEmpty())
      Logger.error("Locations where we have duplicate verses: " + duplicateVerses.joinToString(", "){ Ref.rd(it).toString() })
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
    val issuesThunk = IssuesThunk()
    Rpt.reportWithContinuation(level = 1, "Performing structural validation for ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable {
              val issues = Osis_BasicValidator_PerBook().processRootNode(rootNode)
              issuesThunk.merge(issues)
            }
          } // forEach
        } // run
      } // Parallel
    } // reportWithContinuation

    if (issuesThunk.m_ChaptersWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + issuesThunk.m_ChaptersWithBadIds.joinToString(", "))

    if (issuesThunk.m_VersesWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + issuesThunk.m_VersesWithBadIds.joinToString(", "))

    if (issuesThunk.m_ChaptersWithBadBookAncestor.isNotEmpty())
      Logger.error("Chapters which are not under a book, or are under the wrong book: " + issuesThunk.m_ChaptersWithBadBookAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWithBadChapterAncestor.isNotEmpty())
      Logger.error("Verses which are under the wrong chapter: " + issuesThunk.m_VersesWithBadChapterAncestor.toSet().joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWithNoChapterAncestor.isNotEmpty())
      Logger.error("Verses which are not under a chapter: " + issuesThunk.m_VersesWithNoChapterAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWhereSidAndEidDoNotAlternate.isNotEmpty())
      Logger.error("Locations where verse sids and eids do not alternate: " + issuesThunk.m_VersesWhereSidAndEidDoNotAlternate.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWhereSidAndEidDoNotMatch.isNotEmpty())
      Logger.error("Locations where verse sids and eids do not match: " + issuesThunk.m_VersesWhereSidAndEidDoNotMatch.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_ElidedSubverses.isNotEmpty())
      Logger.warning("Elided subverses: " + issuesThunk.m_ElidedSubverses.joinToString(", "){ RefRange.rdUsx(it).toString() })
  }
}




/*******************************************************************************/
private class Osis_BasicValidator_PerBook
{
  /****************************************************************************/
  fun processRootNode (bookNode: Node): IssuesThunk
  {
    Rpt.reportBookAsContinuation(Osis_FileProtocol.getBookAbbreviation(bookNode))
    structuralValidationForBook1(bookNode)
    return m_IssuesThunk
  }


  /****************************************************************************/
  private fun structuralValidationForBook1 (bookNode: Node)
  {
    val bookNo = Osis_FileProtocol.readRef(Osis_FileProtocol.getBookAbbreviation(bookNode)).getB()
    Dom.findNodesByName(bookNode, Osis_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      if (!Dom.hasAsAncestor(chapterNode, bookNode))
        m_IssuesThunk.m_ChaptersWithBadBookAncestor.add(Osis_FileProtocol.readRef(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!).toRefKey())
      else
      {
        val chapterRefKey = getRefKey(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!)
        if (0L == chapterRefKey)
          m_IssuesThunk.m_ChaptersWithBadIds.add(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!)
        else
        {
          if (Ref.getB(chapterRefKey) != bookNo)
            m_IssuesThunk.m_ChaptersWithBadBookAncestor.add(chapterRefKey)
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
        m_IssuesThunk.m_VersesWithNoChapterAncestor.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toRefKey())
      else
      {
        val id = if (Osis_FileProtocol.attrName_verseSid() in verseNode) Osis_FileProtocol.attrName_verseSid() else Osis_FileProtocol.attrName_verseEid()
        val verseRefKey = getRefKey(verseNode[id]!!)
        if (0L == verseRefKey)
          m_IssuesThunk.m_VersesWithBadIds.add(chapterNode[Osis_FileProtocol.attrName_verseSid()]!!)
        else
        {
          checkForElidedSubverses(verseNode)

          if (Ref.rd(verseRefKey).toRefKey_bc() != chapterRefKey)
            m_IssuesThunk.m_VersesWithBadChapterAncestor.add(verseRefKey)
        }
      }
    }

    checkVerseSidsAndEidsAlternate(verseNodes)
  }


  /****************************************************************************/
  private fun checkForElidedSubverses (node: Node)
  {
    val sid = node[Osis_FileProtocol.attrName_verseSid()] ?: return
    if ('-' !in sid) return
    val rc = Osis_FileProtocol.readRefCollection(sid)
    if (rc.getLowAsRef().hasS() || rc.getHighAsRef().hasS())
      m_IssuesThunk.m_ElidedSubverses.add(rc.toString())
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
          m_IssuesThunk.m_VersesWhereSidAndEidDoNotAlternate.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        expectedId = Osis_FileProtocol.readRefCollection(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toString()
      }

      else // eid
      {
        if (null == expectedId)
        {
          m_IssuesThunk.m_VersesWhereSidAndEidDoNotAlternate.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        if (expectedId != Osis_FileProtocol.readRefCollection(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toString())
          m_IssuesThunk.m_VersesWhereSidAndEidDoNotMatch.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toRefKey())

        expectedId = null
      } // eid
    }
  }


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


  private val m_IssuesThunk = IssuesThunk()
}





/******************************************************************************/
private class IssuesThunk
{
  val m_ChaptersWithBadBookAncestor: MutableList<RefKey> = mutableListOf()
  val m_ChaptersWithBadIds: MutableList<String> = mutableListOf()
  val m_ElidedSubverses: MutableList<String> = mutableListOf()
  val m_VersesWhereSidAndEidDoNotAlternate: MutableList<RefKey> = mutableListOf()
  val m_VersesWhereSidAndEidDoNotMatch: MutableList<RefKey> = mutableListOf()
  val m_VersesWithBadChapterAncestor: MutableList<RefKey> = mutableListOf()
  val m_VersesWithBadIds: MutableList<String> = mutableListOf()
  val m_VersesWithNoChapterAncestor: MutableList<RefKey> = mutableListOf()

  @Synchronized fun merge (otherIssues: IssuesThunk)
  {
    m_ChaptersWithBadBookAncestor.addAll(otherIssues.m_ChaptersWithBadBookAncestor)
    m_ChaptersWithBadIds.addAll(otherIssues.m_ChaptersWithBadIds)
    m_ElidedSubverses.addAll(otherIssues.m_ElidedSubverses)
    m_VersesWhereSidAndEidDoNotAlternate.addAll(otherIssues.m_VersesWhereSidAndEidDoNotAlternate)
    m_VersesWhereSidAndEidDoNotMatch.addAll(otherIssues.m_VersesWhereSidAndEidDoNotMatch)
    m_VersesWithBadChapterAncestor.addAll(otherIssues.m_VersesWithBadChapterAncestor)
    m_VersesWithBadIds.addAll(otherIssues.m_VersesWithBadIds)
    m_VersesWithNoChapterAncestor.addAll(otherIssues.m_VersesWithNoChapterAncestor)
  }
}