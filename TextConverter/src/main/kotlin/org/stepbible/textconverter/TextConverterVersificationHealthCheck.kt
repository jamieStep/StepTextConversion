package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructuresSupportedByOsis2modAll
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
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
 * chapter.  Note that this is done without reference to the particular
 * versification scheme at which we are aiming: the present class is concerned
 * purely with checking that all chapters do indeed come under the book node to
 * which purportedly they belong, that all verses come under the correct
 * chapter, that things are in the right order, that there are no holes in the
 * chapter, that things are in the right order, that there are no holes in the
 * numbering, etc.
 *
 * By default, all issues are reported as errors, and are reported via the
 * logging system.
 *
 * A configuration flag *stepValidationReportOutOfOrderAsError* determines
 * whether out-of-order issues are reported as errors or as warnings.  In
 * general I believe it is appropriate to report them as errors, but we have
 * come across at least one text -- deu_HFA -- where a few verses had
 * deliberately been included out of order in the raw text, and clearly we
 * would not wish these to be reported as errors, or processing will be
 * aborted.
 *
 * You can also override the default reporting mechanism by using
 * setErrorReporter and setErrorWarning to insert your own error and
 * warning handler.  This makes it possible to accumulate the information,
 * rather than have it reported.
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
  * By default, errors are reported via Logger.error.  If you want to do
  * something else instead (like accumulating them so you can work upon them),
  * you can supply your own error reporter.
  *
  * The *refAsString* argument to the reporter gives the reference at which the
  * issue has been detected, or is null if it cannot be localised.
  *
  * @param reporter The reporter.  Optional.  If omitted, reverts to using
  *   Logger.error.
  */

  fun setErrorReporter (reporter: (refAsString: String?, msg: String) -> Unit = ::logError)
  {
    m_ErrorReporter = reporter
  }


  /****************************************************************************/
  /**
  * By default, warnings are reported via Logger.warning.  If you want to do
  * something else instead (like accumulating them so you can work upon them),
  * you can supply your own error reporter.
  *
  * The *refAsString* argument to the reporter gives the reference at which the
  * issue has been detected, or is null if it cannot be localised.
  *
  * @param reporter The reporter.  Optional.  If omitted, reverts to using
  *   Logger.warning.
  */

  fun setWarningReporter (reporter: (refAsString: String?, msg: String) -> Unit = ::logWarning)
  {
    m_WarningReporter = reporter
  }


  /****************************************************************************/
  /**
   * Checks the content of all books, returning a list of errors.
   */

  fun checkAllBooks ()
  {
    StepFileUtils.getMatchingFilesFromFolder(StandardFileLocations.getEnhancedUsxFolderPath(), StandardFileLocations.getEnhancedUsxFilePattern(), false)
      .forEach { checkBook(Dom.getDocument(it.toString())) }
  }


  /****************************************************************************/
  /**
   * Carries out checks on a single document.
   *
   * @param document Document to be validated.
   * @return True if any changes have been applied.
   */

  fun checkBook (document: Document): Boolean
  {
    var res = false
    Logger.setPrefix("Ordering")
    val chapters = Dom.findNodesByName(document, "_X_chapter").filter{ Dom.hasAttribute(it,"sid") }
    checkChapterOrdering(chapters)
    checkAllVersesAreWithinChapters(document)
    chapters.forEach { res = checkAndOptionallyRemedyChapterContent(it) or res }
    Logger.setPrefix(null)
    return res
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

  private fun checkAndOptionallyRemedyChapterContent (chapter: Node): Boolean
  {
    m_SubverseTwosWhichDontNeedChecking = ReversificationData.getImplicitRenumbers()
    Logger.setPrefix("Validating structure")
    val res = checkAndOptionallyRemedyChapterContent1(chapter)
    Logger.setPrefix(null)
    return res
  }


  /****************************************************************************/
  /* Basic checks -- do sids and eids alternate, do they start at 1, do they
     have gaps. */

  private fun checkAndOptionallyRemedyChapterContent1 (chapter: Node): Boolean
  {
    checkVerseSidsAndEidsAreValidRefs(chapter)
    checkVerseSidsAndEidsAlternate(chapter)
    val res = checkAndOptionallyRemedyChapterContent2(chapter, !TextConverterProcessorReversification.runMe())
    checkVerseOrderingWithinChapter(chapter)
    return res
  }


  /****************************************************************************/
  /* Looks for the highest number verse in the chapter, and checks all numbers
     up to that point are present. */

  private fun checkAndOptionallyRemedyChapterContent2 (chapter: Node, okToInsertMissingVerses: Boolean): Boolean
  {
    /**************************************************************************/
    /* Collect all verse numbers and also determine the maximum verse number. */

    val verseCollection: MutableMap<Int, Int?> = HashMap(2000)
    var maxVerse = 0
    var res = false



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
      val refAsString = Dom.getAttribute(sid, "sid")
      val rc = RefCollection.rdUsx(refAsString!!)
      val refKeys = rc.getAllAsRefKeys()
      refKeys.forEach { addVerse(it) }
    }



    /**************************************************************************/
    val verses = Dom.findNodesByName(chapter, "verse", false)
    verses.filter { Dom.hasAttribute(it, "sid") }.forEach { collect(it) }



    /**************************************************************************/
    /* Look for duplicates ... */

    val duplicates: MutableList<Int> = mutableListOf()
    for (i in 1 .. maxVerse)
      if (null != verseCollection[i] && verseCollection[i]!! > 1)
        duplicates.add(i)



    /**************************************************************************/
    /* ... and for missing verses. */

    val chapterSid = Dom.getAttribute(chapter, "sid")!!
    val missings: MutableList<Int> = mutableListOf()
    for (i in 1 .. BibleStructuresSupportedByOsis2modAll.getStructureFor(ConfigData.get("stepVersificationScheme")!!).getLastVerseNo(chapterSid))
      if (null == verseCollection[i])
        missings.add(i)



    /**************************************************************************/
    if (duplicates.isNotEmpty())
    {
      val duplicateMsg = "Duplicate verse(s): ${duplicates.joinToString(", ")}"
      m_ErrorReporter(chapterSid, duplicateMsg)
    }



    /**************************************************************************/
    val reportableMissings = missings.filterNot { BibleAnatomy.isCommonlyMissingVerse("$chapterSid:$it") }
    if (reportableMissings.isNotEmpty())
    {
      val missingMsg = "Missing verse(s): ${reportableMissings.joinToString(", ")}"
      m_WarningReporter(chapterSid, missingMsg)
    }



    /**************************************************************************/
    if (okToInsertMissingVerses && missings.isNotEmpty())
    {
      res = true
      createVerses(chapter, missings)
    }



    /**************************************************************************/
    return res
  }


  /****************************************************************************/
  /* Reports issues to do with chapter ordering or positioning, but does not
     attempt to remedy matters. */

  private fun checkChapterOrdering (chapters: List<Node>)
  {
     val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(Dom.getAttribute(Dom.findNodeByName(chapters[0].ownerDocument, "_X_book")!!, "code")!!)
     var expectation = 1

     fun checkExpectation (chapterNode: Node)
     {
       val refAsString = Dom.getAttribute(chapterNode, "sid")!!
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
      val refAsString = Dom.getAttribute(sid, "sid")!!
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
        if (!ok) ok = null != m_SubverseTwosWhichDontNeedChecking && ref.toRefKey() in m_SubverseTwosWhichDontNeedChecking // And we're ok if this is a verse which doesn't need checking.
        if (!ok)
        {
          m_ErrorReporter(refAsString, "Subverse / verse ordering errors at and possibly also after verse $verse")
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
        currentSid = Dom.getAttribute(node, "sid")!!
        if ("sid" == expectation)
          expectation = "eid"
        else
        {
          m_ErrorReporter(Dom.getAttribute(node, "sid")!!, "sid / eid ordering error near here.")
          throw StepException("")
        }
      }

      else if (Dom.hasAttribute(node, "eid"))
      {
        if ("eid" == expectation)
        {
          if (!currentSid.equals(Dom.getAttribute(node, "eid")!!, ignoreCase = true))
            m_ErrorReporter(Dom.getAttribute(node, "eid")!!, "Incorrect eid value: " + Dom.getAttribute(node, "eid")!!)
            expectation = "sid"
        }
        else
        {
          m_ErrorReporter(Dom.getAttribute(node, "eid")!!, "sid / eid ordering error near here.")
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
        val lastVerseRefAsString = Dom.getAttribute(verses[verses.size - 1], "eid")!!
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
    val  chapterSid = Dom.getAttribute(chapter, "sid")!!
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
   /* Creates missing verses if required and permitted. */

   private fun createVerses (chapter: Node, versesRequired: List<Int>)
   {
     val chapterSid = Dom.getAttribute(chapter, "sid")
     var verseNumbersRequired = versesRequired.toMutableList()

     fun process (verseSid: Node): Boolean
     {
       val verseSidV = Ref.rdUsx(Dom.getAttribute(verseSid, "sid")!!).getV()

       while (true)
       {
         if (verseNumbersRequired.isEmpty()) return false
         if (verseSidV <= verseNumbersRequired[0]) return true
         val sid = chapterSid + ":" + verseNumbersRequired[0].toString()
         EmptyVerseHandler.createEmptyVerseForMissingVerse(verseSid, sid)
         verseNumbersRequired = verseNumbersRequired.subList(1, verseNumbersRequired.size)
       }
    }


     val verseSids = Dom.findNodesByAttributeName(chapter, "verse", "sid")
     run processSids@ { verseSids.forEach { if (!process(it)) return@processSids } }
   }


  /****************************************************************************/
  private fun getId (verse: Node): String
  {
    return (if (Dom.hasAttribute(verse, "sid")) Dom.getAttribute(verse, "sid") else Dom.getAttribute(verse, "eid"))!!
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
    val refKey = if (null == ref) 0 else Ref.rd(ref).toRefKey()
    Logger.error(refKey, message)
  }


  /****************************************************************************/
  private fun logWarning (ref:String?,  message:String)
  {
    val refKey = if (null == ref) 0 else Ref.rd(ref).toRefKey()
    Logger.warning(refKey, message)
  }


  /****************************************************************************/
  private var m_ErrorReporter: (refAsString: String?, msg: String) -> Unit = ::logError
  private var m_WarningReporter: (refAsString: String?, msg: String) -> Unit = ::logWarning
  private var m_MissingVerseReporter = m_ErrorReporter
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
      m_MissingVerseReporter = m_WarningReporter
      m_OutOfOrderReporter = m_WarningReporter
    }
  }
}