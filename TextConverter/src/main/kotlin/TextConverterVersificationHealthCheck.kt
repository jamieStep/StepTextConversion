package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.*


/******************************************************************************/
/**
 * Checks for the validity of the chapter / verse structure of a document or a
 * chapter.  There are two aspects to this.  We look at the validity of the
 * structure in isolation -- things like are chapters and verses in ascending,
 * are all verses within chapters etc; and, where we are targetting a known
 * versification scheme, we check to see whether all expected verses are
 * present.
 *
 * CAUTION: Do not confuse this with EnhancedUsxValidator.  The latter assumes
 * any relevant structural issues have been addressed, and is concerned mainly
 * with attempting to confirm that the canonical text of the enhanced USX is
 * what we should expect, based upon the raw text and any reversification
 * processing.
 *
 * By default, missing verses are reported as warnings, and all other issues
 * as reported as errors, and are reported via the logging system.
 *
 * A configuration flag *stepValidationReportOutOfOrderAsError* determines
 * whether out-of-order issues are reported as errors or as warnings.  In
 * general I believe it is appropriate to report them as errors, but we have
 * come across at least one text -- deu_HFA -- where a few verses had
 * deliberately been included out of order in the raw text, and clearly we
 * would not wish these to be reported as errors, or processing will be
 * aborted.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterVersificationHealthCheck
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Package                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Checks the content of all books, returning a list of errors.
   */

  fun checkAllBooks ()
  {
    StepFileUtils.getMatchingFilesFromFolder(StandardFileLocations.getInternalUsxBFolderPath(), ".*\\.usx".toRegex())
      .forEach { checkBook(Dom.getDocument(it.toString())) }
  }


  /****************************************************************************/
  /**
   * Carries out checks on a single document.
   *
   * @param document Document to be validated.
   */

  fun checkBook (document: Document)
  {
    Logger.setPrefix("Structural health check")

    if ("step" != ConfigData["stepOsis2modType"]!!)
      checkForMissingAndExcessVerses(document) // We can do this only with the Crosswire osis2mod, because with the STEP version we don't know what the expectations are.

    val chapters = Dom.findNodesByName(document, "_X_chapter").filter{ Dom.hasAttribute(it,"sid") }
    checkChapterOrdering(chapters)
    checkAllVersesAreWithinChapters(document)
    chapters.forEach { checkChapterContent(it) }
    Logger.setPrefix(null)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Most methods below simply report issues.  Any which may remedy issues have
     'checkAndRemedy' in their names. */

  /****************************************************************************/
  /* Checks that verses are within chapters, but does not attempt to remedy any
     issues. */

  private fun checkAllVersesAreWithinChapters (document: Document)
  {
    Dom.findNodesByName(document, "verse").forEach {
      if (!Dom.hasAncestorNamed(it, "_X_chapter"))
        m_ErrorReporter(getId(it), "Not within chapter.")
    }
  }


  /****************************************************************************/
  /* Performs checks on a single chapter. */

  private fun checkChapterContent (chapter: Node)
  {
    m_SubverseTwosWhichDontNeedChecking = ReversificationData.getImplicitRenumbers()
    Logger.setPrefix("Validating structure")
    checkVerseSidsAndEidsAreValidRefs(chapter)
    checkVerseSidsAndEidsAlternate(chapter)
    checkVerseOrderingWithinChapter(chapter)
    checkForDuplicatesAndHoles(chapter)
    Logger.setPrefix(null)
  }

  /****************************************************************************/
  /* Reports issues to do with chapter ordering or positioning, but does not
     attempt to remedy matters. */

  private fun checkChapterOrdering (chapters: List<Node>)
  {
     val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(Dom.findNodeByName(chapters[0].ownerDocument, "_X_book")!!["code"]!!)
     var expectation = 1

     fun checkExpectation (chapterNode: Node)
     {
       val refAsString = chapterNode["sid"]!!
       try
       {
         val ref = Ref.rdUsx(refAsString)
         val thisBook = ref.getB()
         val  chapter = ref.getC()
         if (thisBook != bookNo)
         {
            m_ErrorReporter(refAsString, "Chapter in wrong book.")
            throw StepException("")
         }

         if (chapter != expectation)
         {
            m_ErrorReporter(refAsString, "Chapter ordering issue.")
            throw StepException("")
         }

         ++expectation
       }
      catch (e: Exception)
      {
        if (e.toString().isNotEmpty()) m_ErrorReporter(null, "Invalid sid: $refAsString.")
      }
    }

    chapters.forEach{ checkExpectation(it) }
  }


  /****************************************************************************/
  /* Looks for the highest number verse in the chapter, and checks all numbers
     up to that point are present. */

  private fun checkForDuplicatesAndHoles (chapter: Node)
  {
    /**************************************************************************/
    //Dbg.d(chapter["sid"]!!, "Mat 17")



    /**************************************************************************/
    /* Collect all verse numbers and also determine the maximum verse number. */

    val verseCollection: MutableMap<Int, Int?> = HashMap(2000)
    var maxVerse = 0



    /**************************************************************************/
    /* For each verse, check if this is the maximum verse number.  Also, count
       the number of occurrences for the given verse. */

    fun addVerse (refKey: RefKey)
    {
      val verse = Ref.getV(refKey)
      if (verse > maxVerse) maxVerse = verse
      if (null == verseCollection[verse])
        verseCollection[verse] = 1
      else if (!Ref.hasS(refKey))
        verseCollection[verse] = 1 + verseCollection[verse]!!
    }


    /**************************************************************************/
    /* Each verse tag may refer to a single verse or to a range (the latter
       occurring when things are elided).  We need to deal with all of the
       verses implied by the sid. */

    fun collect (sid: Node)
    {
      val refAsString = sid["sid"]
      val rc = RefCollection.rdUsx(refAsString!!)
      val refKeys = rc.getAllAsRefKeys()
      refKeys.forEach { addVerse(it) }
    }



    /**************************************************************************/
    val verses = Dom.findNodesByName(chapter, "verse", false)
    verses.filter { "sid" in it }.forEach { collect(it) }



    /**************************************************************************/
    /* Look for duplicates ... */

    val duplicates: MutableList<Int> = mutableListOf()
    for (i in 1 .. maxVerse)
      if (null != verseCollection[i] && verseCollection[i]!! > 1)
        duplicates.add(i)



    /**************************************************************************/
    /* ... and for missing verses. */

    var missings: MutableList<Int> = mutableListOf()
    val chapterSid = chapter["sid"]!!

    if ("step" == ConfigData["stepOsis2modType"]!!)
    {
      val chapterRefKey = Ref.rdUsx(chapterSid).toRefKey_bc()
      missings = BibleStructure.UsxUnderConstructionInstance().getMissingEmbeddedVersesForChapter(Ref.getB(chapterRefKey), Ref.getC(chapterRefKey)) .map { Ref.getV(it) } as MutableList<Int>
    }
    else
    {
      for (i in 1 .. BibleStructure.Osis2modSchemeInstance(ConfigData["stepVersificationScheme"]!!, true).getLastVerseNo(chapterSid))
        if (null == verseCollection[i])
          missings.add(i)
    }



    /**************************************************************************/
    if (duplicates.isNotEmpty())
    {
      val duplicateMsg = "Duplicate verse(s): ${duplicates.joinToString(", ")}"
      m_ErrorReporter(chapterSid, duplicateMsg)
    }



//    /**************************************************************************/
//    val reportableMissings = missings.filterNot { BibleAnatomy.isCommonlyMissingVerse("$chapterSid:$it") }
//    if (reportableMissings.isNotEmpty())
//    {
//      val missingMsg = "Missing verse(s): ${reportableMissings.joinToString(", ")}"
//      m_WarningReporter(chapterSid, missingMsg)
//    }



    /**************************************************************************/
    val chapterSidAsRefKey = Ref.rdUsx(chapterSid).toRefKey_bc()
    val commonlyMissingVersesForThisChapterAsRefKeys = BibleAnatomy.getCommonlyMissingVerses().filter { Ref.rd(it).toRefKey_bc() == chapterSidAsRefKey }.toSet()
    val missingsAsRefKeys = missings.map { Ref.setV(chapterSidAsRefKey, it) }.toSet()
    val reportableMissingsAsRefKeys = missingsAsRefKeys - commonlyMissingVersesForThisChapterAsRefKeys
    val nonReportableMissingsAsRefKeys = commonlyMissingVersesForThisChapterAsRefKeys - reportableMissingsAsRefKeys
    val missingsAsString = if (missingsAsRefKeys.isEmpty()) "" else missingsAsRefKeys.sorted().joinToString(", "){ Ref.rd(it).toString("bcv") }
    val nonReportableMissingsAsString = if (nonReportableMissingsAsRefKeys.isEmpty()) "" else ("  (The following verse(s) are absent in many Bibles, and therefore are not of concern: " + nonReportableMissingsAsRefKeys.sorted().joinToString(", "){ Ref.getV(it).toString() } + ".)")
    if (missingsAsRefKeys.isNotEmpty())
    {
      val missingMsg = "Text lacks verse(s) which target versification scheme expects: $missingsAsString$nonReportableMissingsAsString"
      Logger.info(Ref.rdUsx(chapterSid).toRefKey(), missingMsg)
    }
  }


  /****************************************************************************/
  private fun checkForMissingAndExcessVerses (document: Document)
  {
    /**************************************************************************/
    val osis2modSchemeDetails = BibleStructure.Osis2modSchemeInstance(ConfigData["stepVersificationScheme"]!!, true)
    val bookNumber = BibleBookNamesUsx.abbreviatedNameToNumber(Dom.findNodeByName(document,"_X_book")!!["code"]!!)
    BibleStructure.UsxUnderConstructionInstance().populateFromDom(document, wantWordCount = false, collection = "enhanced/healthCheck", )
    val diffs = BibleStructure.compareWithGivenScheme(bookNumber, BibleStructure.UsxUnderConstructionInstance(), osis2modSchemeDetails)



    /**************************************************************************/
    if (diffs.chaptersInTargetSchemeButNotInTextUnderConstruction.isNotEmpty())
      m_MissingElementReporter(null, "Text lacks chapter(s) which target versification scheme expects: ${diffs.chaptersInTargetSchemeButNotInTextUnderConstruction.joinToString(", ") { Ref.rd(it).toString() } }.")

    if ("runtime" != ConfigData["stepForceReversificationType"]!!.lowercase() && diffs.chaptersInTextUnderConstructionButNotInTargetScheme.isNotEmpty())
      m_ExcessElementReporter(null, "Text contains chapter(s) which target versification scheme does not expect: ${diffs.chaptersInTextUnderConstructionButNotInTargetScheme.joinToString(", ") { Ref.rd(it).toString() } }.")

    //if (diffs.versesInTargetSchemeButNotInTextUnderConstruction.isNotEmpty()) // Reported later as a result of later checks.
    //  m_MissingElementReporter(null, "Text lacks verse(s) which target versification scheme expects: ${diffs.versesInTargetSchemeButNotInTextUnderConstruction.joinToString(", ") { Ref.rd(it).toString() } }.")

    if ("runtime" != ConfigData["stepForceReversificationType"]!!.lowercase() && diffs.versesInTextUnderConstructionButNotInTargetScheme.isNotEmpty())
      m_ExcessElementReporter(null, "Text contains verse(s) which target versification scheme does not expect: ${diffs.versesInTextUnderConstructionButNotInTargetScheme.joinToString(", ") { Ref.rd(it).toString() } }.")
  }


  /****************************************************************************/
  private fun checkVerseOrderingWithinChapter (chapter:Node)
  {
    /**************************************************************************/
    //Dbg.outputDom(chapter.ownerDocument, "a")



    /**************************************************************************/
    var prevVerse= 0
    var prevSubverse= 0



    /**************************************************************************/
    fun check (sid: Node)
    {
      val refAsString = sid["sid"]!!
      val ref = Ref.rdUsx(refAsString)
      val verse = ref.getV()
      val subverse = ref.getS()

      if (subverse == RefBase.C_DummyElement) // If there is no subverse, we must have a verse one higher than before.
      {
        if (verse != prevVerse + 1 && !nonReportableOrderingIssue(refAsString))
        {
          m_OutOfOrderReporter(refAsString, "Verse ordering errors at or near verse $verse")
          throw StepException("")
        }

        prevVerse = verse
        prevSubverse = 0
      }
      else
      {
        var ok = verse == prevVerse && subverse == prevSubverse + 1 // We're ok if we're still in the same verse and have moved on by 1
        if (!ok) ok = subverse == 1 && verse == prevVerse + 1 // We're also ok if this is subverse 1 and we've just moved to a new verse.
        if (!ok) ok = ref.toRefKey() in m_SubverseTwosWhichDontNeedChecking // And we're ok if this is a verse which doesn't need checking.
        if (!ok)
        {
          m_OutOfOrderReporter(refAsString, "Subverse / verse ordering errors at and possibly also after verse $verse")
          throw StepException("")
        }

        prevVerse = verse
        prevSubverse = subverse
      } // else
    } // fun check



    /**************************************************************************/
    try
    {
      val verses: List<Node> = Dom.findNodesByName(chapter, "verse", false)
      verses.subList(0, verses.size - 2).filter {Dom.hasAttribute(it, "sid")} .forEach { check(it) }
    }
    catch (_: Exception)
    {
    }
  }


  /****************************************************************************/
  private fun checkVerseSidsAndEidsAlternate (chapter:Node)
  {
    /**************************************************************************/
    var currentSid = ""
    var expectation = "sid"



    /**************************************************************************/
    fun checkOrdering (node: Node)
    {
      if (Dom.hasAttribute(node, "sid"))
      {
        currentSid = node["sid"]!!
        if ("sid" == expectation)
          expectation = "eid"
        else
        {
          m_ErrorReporter(node["sid"]!!, "sid / eid ordering error near here.")
          throw StepException("")
        }
      }

      else if (Dom.hasAttribute(node, "eid"))
      {
        if ("eid" == expectation)
        {
          if (!currentSid.equals(node["eid"]!!, ignoreCase = true))
            m_ErrorReporter(node["eid"]!!, "Incorrect eid value: " + node["eid"]!!)
            expectation = "sid"
        }
        else
        {
          m_ErrorReporter(node["eid"]!!, "sid / eid ordering error near here.")
          throw StepException("")
        }
      }

      else  // Not sid or eid.
      {
        m_ErrorReporter(null, "sid / eid ordering error near " + Dom.toString(node))
        throw StepException("")
      }
    } // fun checkOrdering



    /**************************************************************************/
    try
    {
      val verses: List<Node> = Dom.findNodesByName(chapter, "verse", false)
      verses.forEach { checkOrdering(it) }

      if ("sid" != expectation)
      {
        val lastVerseRefAsString = verses[verses.size - 1]["eid"]!!
        val chapterRefAsRefKey = Ref.rdUsx(lastVerseRefAsString).toRefKey_bc()
        val chapterRef = Ref.rd(chapterRefAsRefKey).toString()
        m_ErrorReporter(chapterRef, "sid / eid ordering error at end of this chapter.")
      }
    }
    catch (_: StepException)
    {
    }
  }


  /****************************************************************************/
  /* Checks that all verses have sids or eids which are valid USX references
     and which appear in the correct chapter. */

  private fun checkVerseSidsAndEidsAreValidRefs (chapter: Node)
  {
    /**************************************************************************/
    val  chapterSid = chapter["sid"]!!
    val  chapterRef = Ref.rdUsx(chapterSid)
    val  bookNo = chapterRef.getB()
    val  chapterNo = chapterRef.getC()



    /**************************************************************************/
    fun checkRefForValidity (node: Node)
    {
      val id = getId(node)
      try
      {
        val rc = RefCollection.rdUsx(id)
        if (rc.getLowAsRef().getB() != bookNo)m_ErrorReporter(id, "Reference in wrong book.")
        if (rc.getLowAsRef().getC() != chapterNo)m_ErrorReporter(id, "Reference in wrong chapter.")
        if (rc.getHighAsRef().getB() != bookNo)m_ErrorReporter(id, "Reference in wrong book.")
        if (rc.getHighAsRef().getC() != chapterNo)m_ErrorReporter(id, "Reference in wrong chapter.")
      }
      catch (_: Exception)
      {
        m_ErrorReporter(null, "Invalid sid or eid: $id.")
      }
    } // fun checkRefForValidity



    /**************************************************************************/
    val verses = Dom.findNodesByName(chapter, "verse", false)
    verses.forEach { checkRefForValidity(it) }
}


  /****************************************************************************/
  private fun getId (verse: Node): String
  {
    return (if (Dom.hasAttribute(verse, "sid")) verse["sid"] else verse["eid"])!!
  }


   /****************************************************************************/
   /* Certain ordering issues are common and acceptable, and should therefore not
      be reported. */

  private fun nonReportableOrderingIssue (sid:String): Boolean
  {
    var refKey = Ref.rdUsx(sid).toRefKey_bcv()
    refKey = Ref.setV(refKey, Ref.getV(refKey) - 1) // An ordering issue usually indicates that the preceding verse is missing.
    return BibleAnatomy.isCommonlyMissingVerse(refKey)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                          Logging / reporting                           **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun logError (ref:String?,  message:String)
  {
    val refKey = if (null == ref) -1 else Ref.rd(ref).toRefKey()
    Logger.error(refKey, message)
  }


  /****************************************************************************/
  private fun logWarning (ref:String?,  message:String)
  {
    val refKey = if (null == ref) -1 else Ref.rd(ref).toRefKey()
    Logger.warning(refKey, message)
  }


  /****************************************************************************/
  private var m_ErrorReporter: (refAsString: String?, msg: String) -> Unit = ::logError
  private var m_WarningReporter: (refAsString: String?, msg: String) -> Unit = ::logWarning
  private var m_ExcessElementReporter = m_ErrorReporter
  private var m_MissingElementReporter = m_WarningReporter
  private var m_OutOfOrderReporter = m_ErrorReporter


  /****************************************************************************/
  /* Where we are applying reversification, reversification sometimes creates
     a subverse b without having a row which creates the subverse a.  Where this
     is the case, the reversification data implicitly assumes that the verse
     itself fulfils the role of subverse a.  In such cases we will hit
     subverse b without having seen subverse explicitly identified as such, but
     we don't want to see this as an ordering error.  The variable below
     records the refKeys of all the subverse b's where we don't want to report
     the lack of subverse a as an error.
  */

  private lateinit var m_SubverseTwosWhichDontNeedChecking: Set<RefKey>


  /****************************************************************************/
  init
  {
    /*************************************************************************/
    /* If we're prepared to accept mis-ordered texts, we need to report
       mis-ordering as a warning rather than the default (which is an error).
       And since missing verses will themselves also give rise to
       mis-ordering, we need to report these as warnings too. */

    if (!ConfigData.getAsBoolean("stepValidationReportOutOfOrderAsError", "y"))
    {
      m_MissingElementReporter = m_WarningReporter
      m_OutOfOrderReporter = m_WarningReporter
    }
  }
}




