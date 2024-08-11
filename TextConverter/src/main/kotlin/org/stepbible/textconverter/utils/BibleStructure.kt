package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.*
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.abs


/******************************************************************************/
/**
 * Base class for details of chapter / verse structure of Bibles.
 *
 * This is used for two purposes -- to hold the structure details of an OSIS
 * file, or to hold the details of one of Crosswire osis2mod's built-in
 * versification schemes.
 *
 * There is a derived singleton which can be used to hold OSIS details.
 * At present I imagine you will only use this to hold details of the *input*
 * OSIS file, but the instance can be repopulated if necessary, so you could
 * also use to hold details of the *output* file if, for instance, you wish
 * to validate the output.
 * 
 * As regards osis2mod schemes, there is a singleton which you can use to
 * represent the NRSV(A) scheme, since that is needed quite commonly.
 * Other than that, you can create separate instances for each scheme of
 * interest.
 *
 * The class holds details of which books / chapters / verses / subverses
 * exist (no subverses for osis2mod schemes).  And for an OSIS file, it also
 * holds a word-count for each verse, information which is needed by the
 * reversification processing.
 * 
 * It also contains details of 'unusual' circumstances, like verses having
 * been supplied out of order or OT+NT books not being in Protestant Bible
 * order; of points of interest (like the locations of elided verses), and
 * of errors (like duplicate chapters).  I assume that something elsewhere
 * will check for errors, report them and abort the processing, since the
 * remaining information may well be invalid in the presence of errors.
 *
 *
 *
 *
 *
 * ## Method structure
 *
 * Many methods here come in five flavours, one using a Ref to identify an
 * element of interest, one using a RefKey, one the individual bcvs entries
 * which make up an element, one the ref as a USX or OSIS string, and one the
 * four elements as an IntArray.  All are organised such that all five call
 * a common method.  This means that derived classes need override only this
 * one method, should they need to impose their own processing.
 *
 * The names of the arguments to these methods indicate how much of the
 * argument *must* be defined.  Thus, for example, a bookRefKey represents a
 * RefKey in which at least the book portion has been filled in.  In general
 * there is no harm in filling in more than the portion actually required;
 * any excess is usually simply ignored.  The form which takes the b, c, v and
 * s as separate arguments always accepts all four, even where, say, only the
 * book is required.  Unused arguments are defaulted, however, so you only
 * *need* fill in the portion required.
 *
 * Having said this, you need to be a little more careful with verses and
 * subverses.  There are separate methods to permit you to check the
 * existence of a specific verse as such, without subverses; of a specific
 * subverse; or of a verse as either the verse or a collection of subverses.
 * And depending upon which of these you use, the existence or non-existence
 * of a value for the subverse may be significant.
 *
 * There is a similar issue over word counts.  If you ask for the word count
 * of a verse which does not exist as subverses, you'll get the count for the
 * verse.  If you ask for the count of a subverse, you'll get the count for
 * that subverse.  But if you ask for the count of a verse which has subverses,
 * you'll get the aggregate count across all subverses.
 *
 *
 *
 *
 * ## Assumptions
 *
 * I make the assumption that elisions run from one *verse* to another (ie that
 * they do not involve subverses), and that they fall entirely within a single
 * chapter.  (This limitation means I can always know how many elements make up
 * the elision, because it is simply the difference in the two verse numbers.)
 *
 * I cater for the possibility that the text as a whole may be incomplete (may
 * lack certain books); that a given book may be incomplete (may lack certain
 * chapters); or that a given chapter may be incomplete (may lack certain
 * verses).  I do *not* cater for the possibility that a given *verse* may be
 * incomplete (may lack certain subverses).  In other words, I assume that
 * subverses will be numbered a, b, etc with no gaps.  The first subverse may
 * start at the beginning of the verse or some way into it.
 *
 * Chapter incompleteness reflects only that there are holes in the verse
 * numbering scheme (eg it runs 1, 2, 5, 6), not that there are verses missing
 * at the *end* of the chapter, because in general I don't know how many verses
 * there *should* be in a chapter.
 *
 * Ditto book incompleteness, mutatis mutandis.
 *
 * With osis2mod schemes, I make the assumption that the text will always be
 * complete (ie no missing chapters or missing verses), and that elements
 * (books, chapters or verses) will be correctly ordered.
 *
 *
 *
 *
 * ## Elisions and subverses
 *
 * Elisions are always treated as though expanded out into their individual
 * verses.  In other words, if we have, say, an elision Gen.1:1-Gen.1:3, the
 * processing will behave as though each of Gen.1:1, Gen.1:2 and Gen.1:3 were
 * present.  Existence tests will pass on all three of these verses, and if you
 * ask for a list of references from Gen.1, all three verses will feature in
 * that list.  A request for the word count of an element which appears in an
 * elision always returns the special (negative) value C_ElementInElision.
 *
 * If subverses are present, a request for a list of all verses will include
 * the subverses as well.
 *
 * Note that in the full conversion process, elisions tend to be expanded out
 * into their individual verses early on in the processing.  Unfortunately,
 * this doesn't necessarily occur prior to the point where the present
 * processing is called upon to read the text, though, so I need to be able
 * to cope with non-expanded elisions.
 * 
 * 
 * 
 * 
 * ## When to obtain derived instances
 * 
 * osis2mod details can be obtained at any point.
 * 
 * OSIS details should be obtained *after* the OSIS has been converted to
 * canonical form (eg after div:chapter has been converted to <chapter>,
 * but *before* any restructuring has been applied (such as expanding out
 * elided verses).
 *
 * @author ARA "Jamie" Jamieson
*/

open class BibleStructure (fileProtocol: X_FileProtocol?)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Populate                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  companion object
  {
    /**************************************************************************/
    /** Dummy word count when dealing with verses in an elision (for which no
    *   meaningful word count can be obtained).
    */

    const val C_ElementInElision   = -1



    /**************************************************************************/
    /** Dummy indicator returned when asking for something which is not
    *   available.
    */

    const val C_Unavailable   = -2



    /**************************************************************************/
    /**
    * Returns an instance for a given osis2mod scheme.  (The name supplied need
    * not be in canonical form -- it is converted to canonical form within the
    * method.)
    *
    * If you intend to use the details once-off (for example when evaluating
    * the extent to which different schemes fit the available data), give
    * 'retain' as false.  That way the scheme won't be retained in memory.
    *
    * If you intend to use it quite a bit (for example because we are doing a
    * plain vanilla run with Crosswire's osis2mod, and this is the scheme which
    * we are telling osis2mod to use), you can set the retain flag, and in that
    * case the call acts as a singleton.
    *
    * @param schemeName
    * @return Instance.
    */

    fun makeOsis2modSchemeInstance (schemeName: String): BibleStructureOsis2ModScheme
    {
      val canonicalSchemeName = VersificationSchemesSupportedByOsis2mod.canonicaliseSchemeName(schemeName)
      var res = m_RetainedOsis2modSchemes[canonicalSchemeName]
      if (null == res)
      {
        res = BibleStructureOsis2ModScheme(canonicalSchemeName)
        m_RetainedOsis2modSchemes[canonicalSchemeName] = res
      }

      return res
    }

    private val m_RetainedOsis2modSchemes: MutableMap<String, BibleStructureOsis2ModScheme> = mutableMapOf()


    /****************************************************************************/
    /**
    * Returns details for the osis2mod scheme which represents either NRSV or
    * NRSVA, depending upon whether or not we are working with a text which
    * contains DC books.
    *
    * @param bibleStructureBeingWorkedOn
    * @return Details of osis2mod scheme.
    */

    fun makeOsis2modNrsvxSchemeInstance (bibleStructureBeingWorkedOn: BibleStructure) = makeOsis2modSchemeInstance("NRSV" + (if (bibleStructureBeingWorkedOn.hasAnyBooksDc()) "A" else ""))


    /****************************************************************************/
    /** Returned when you compare the text under construction with another
     *  scheme.  Hopefully the purposes of the fields are obvious.  All are
     *  returned as sorted lists of RefKeys. */

    data class ComparisonWithOtherScheme (val chaptersInTextUnderConstructionButNotInTargetScheme: List<RefKey>,
                                          val chaptersInTargetSchemeButNotInTextUnderConstruction: List<RefKey>,
                                          val chaptersInBoth: List<RefKey>,
                                          val versesInTextUnderConstructionButNotInTargetScheme: List<RefKey>,
                                          val versesInTargetSchemeButNotInTextUnderConstruction: List<RefKey>,
                                          val versesInBoth: List<RefKey>,
                                          val versesInTextUnderConstructionOutOfOrder: List<RefKey>)


    /****************************************************************************/
    /**
    * Compares the text of a single book in two different schemes.
    *
    * @param bookNumber What it says on the tin.
    * @param schemeA The scheme against which to compare.
    * @param schemeB The scheme against which to compare.
    * @return ComparisonWithOtherScheme instance giving details of common verses
     *        etc.
    */

    fun compareWithGivenScheme (bookNumber: Int, schemeA: BibleStructure, schemeB: BibleStructure): ComparisonWithOtherScheme
    {
      val versesInTextUnderConstruction = schemeA.getAllRefKeysForBook(bookNumber).toSet()
      val versesInOtherScheme = try { schemeB.getAllRefKeysForBook(bookNumber).toSet() } catch (_: Exception) { setOf() }
      val versesInTextUnderConstructionButNotInOtherScheme = versesInTextUnderConstruction subtract versesInOtherScheme
      val versesInOtherSchemeButNotInTextUnderConstruction = versesInOtherScheme subtract versesInTextUnderConstruction
      val versesInBoth = versesInTextUnderConstruction intersect versesInOtherScheme
      val chaptersInTextUnderConstruction = versesInTextUnderConstruction.map { Ref.clearV(Ref.clearS(it)) }.toSet()
      val chaptersInOtherScheme = versesInOtherScheme.map { Ref.clearV(Ref.clearS(it)) }.toSet()
      val chaptersInTextUnderConstructionButNotInOtherScheme = chaptersInTextUnderConstruction subtract chaptersInOtherScheme
      val chaptersInOtherSchemeButNotInTextUnderConstruction = chaptersInOtherScheme subtract chaptersInTextUnderConstruction
      val chaptersInBoth = chaptersInTextUnderConstructionButNotInOtherScheme intersect chaptersInOtherScheme
      val versesOutOfOrder = schemeA.getOutOfOrderVerses()
      return ComparisonWithOtherScheme(chaptersInTextUnderConstructionButNotInOtherScheme.sorted(), chaptersInOtherSchemeButNotInTextUnderConstruction.sorted(), chaptersInBoth.sorted(),
                                       versesInTextUnderConstructionButNotInOtherScheme.sorted(), versesInOtherSchemeButNotInTextUnderConstruction.sorted(), versesInBoth.sorted(),
                                       versesOutOfOrder)
    } // compareWithGivenScheme
  } // companion object





  /****************************************************************************/
  /**
  * Adds the data from a given file to the current data structures.  The method
  * is marked 'open' mainly so that inheriting classes (and in particular,
  * BibleStructureOsis2ModScheme) can ensure that it doesn't get called.
  *
  * @param prompt Output to screen as part of progress indicator.
  * @param rootNode
  * @param wantCanonicalTextSize True if we need to accumulate the text size.
  * @param filePath: Optional: used for debugging and progress reporting only.
  * @param bookName USX abbreviation.
  */

  open fun addFromBookRootNode (prompt: String, rootNode: Node, wantCanonicalTextSize: Boolean, filePath: String? = null, bookName: String? = null)
  {
    m_Populated = true
    if (null != bookName) Dbg.reportProgress("  $prompt: Determining Bible structure for ${bookName.uppercase()}")
    m_CollectingCanonicalTextSize = wantCanonicalTextSize
    if (null != bookName && null != filePath) m_BookAbbreviationToFilePathMappings[bookName.lowercase()] = filePath
    addFromRootNode(rootNode, wantCanonicalTextSize)
  }


  /****************************************************************************/
  /**
  * Adds the data from a given file to the current data structures.  The method
  * is marked 'open' mainly so that inheriting classes (and in particular,
  * BibleStructureOsis2ModScheme) can ensure that it doesn't get called.
  *
  * @param prompt Output to screen as part of progress indicator.
  * @param doc
  * @param wantCanonicalTextSize True if we need to accumulate the word count.
  * @param filePath: Optional: used for debugging and progress reporting only.
  * @param bookName USX abbreviation.
  */

  open fun addFromDoc (prompt: String, doc: Document, wantCanonicalTextSize: Boolean, filePath: String? = null, bookName: String? = null)
  {
    m_Populated = true
    if (null != bookName) Dbg.reportProgress("  $prompt: Determining Bible structure for ${bookName.uppercase()}")
    m_CollectingCanonicalTextSize = wantCanonicalTextSize
    if (null != bookName && null != filePath) m_BookAbbreviationToFilePathMappings[bookName.lowercase()] = filePath
    addFromDoc(doc, wantCanonicalTextSize)
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether or not the instance has been populated.
  *
  * @return True if populated.
  */

  fun isPopulated () = m_Populated


  /****************************************************************************/
  /**
  * Clears the data structures and adds the data from a given file.
  *
  * @param prompt Output to screen as part of progress indicator.
  * @param doc
  * @param wantCanonicalTextSize True if we need to accumulate the text size.
  * @param filePath: Optional: used for debugging and progress reporting only.
  * @param bookName USX abbreviation.
  */

  fun populateFromDom (prompt: String, doc: Document, wantCanonicalTextSize: Boolean, filePath: String? = null, bookName: String? = null)
  {
    clear()
    addFromDoc(prompt = prompt, doc, wantCanonicalTextSize, bookName = bookName)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Extract information                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun hasCanonicalTextSize () = m_CollectingCanonicalTextSize

  open fun getAllBookNumbers   () = m_Text.m_Content.m_ContentMap.keys
  open fun getAllBookNumbersOt () = m_Text.m_Content.m_ContentMap.keys.filter { BibleAnatomy.isOt(it) }
  open fun getAllBookNumbersNt () = m_Text.m_Content.m_ContentMap.keys.filter { BibleAnatomy.isNt(it) }
  open fun getAllBookNumbersDc () = m_Text.m_Content.m_ContentMap.keys.filter { BibleAnatomy.isDc(it) }

  open fun getAllBookAbbreviations   () = getAllBookNumbers()  .map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  open fun getAllBookAbbreviationsOt () = getAllBookNumbersOt().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  open fun getAllBookAbbreviationsNt () = getAllBookNumbersNt().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  open fun getAllBookAbbreviationsDc () = getAllBookNumbersDc().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }



  open fun getAllBookAbbreviationsUsx  () = m_Text.m_Content.m_ContentMap.keys.map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  open fun getAllBookAbbreviationsOsis () = m_Text.m_Content.m_ContentMap.keys.map { BibleBookNamesOsis.numberToAbbreviatedName(it) }



  open fun hasAllBooksOt () = BibleAnatomy.getNumberOfBooksInOt() == getAllBookNumbersOt().size
  open fun hasAllBooksNt () = BibleAnatomy.getNumberOfBooksInNt() == getAllBookNumbersNt().size



  open fun hasAnyBooksOt () = null != m_Text.m_Content.m_ContentMap.keys.firstOrNull { BibleAnatomy.isOt(it) }
  open fun hasAnyBooksNt () = null != m_Text.m_Content.m_ContentMap.keys.firstOrNull { BibleAnatomy.isNt(it) }
  open fun hasAnyBooksDc () = null != m_Text.m_Content.m_ContentMap.keys.firstOrNull { BibleAnatomy.isDc(it) }



  fun bookExists (bookRef: Ref)                               = commonBookExists(makeElts(bookRef))
  fun bookExists (bookRefKey: RefKey)                         = commonBookExists(makeElts(bookRefKey))
  fun bookExists (b: Int, c: Int = 0, v: Int = 0, s: Int = 0) = commonBookExists(makeElts(b, 0, 0, 0))
  fun bookExists (bookRefAsString: String)                    = commonBookExists(makeElts(bookRefAsString))
  fun bookExists (elts: IntArray)                             = commonBookExists(elts)



  fun chapterExists (chapterRef: Ref)                        = commonChapterExists(makeElts(chapterRef))
  fun chapterExists (chapterRefKey: RefKey)                  = commonChapterExists(makeElts(chapterRefKey))
  fun chapterExists (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonChapterExists(makeElts(b, c, 0, 0))
  fun chapterExists (chapterRefAsString: String)             = commonChapterExists(makeElts(chapterRefAsString))
  fun chapterExists (elts: IntArray)                         = commonChapterExists(elts)



  fun verseExistsWithoutSubverses (verseOrSubverseRef: Ref)            = commonVerseExistsWithoutSubverses(makeElts(verseOrSubverseRef))
  fun verseExistsWithoutSubverses (verseOrSubverseRefKey: RefKey)      = commonVerseExistsWithoutSubverses(makeElts(verseOrSubverseRefKey))
  fun verseExistsWithoutSubverses (b: Int, c: Int, v: Int, s: Int = 0) = commonVerseExistsWithoutSubverses(makeElts(b, c, v, s))
  fun verseExistsWithoutSubverses (verseOrSubverseRefAsString: String) = commonVerseExistsWithoutSubverses(makeElts(verseOrSubverseRefAsString))
  fun verseExistsWithoutSubverses (elts: IntArray)                     = commonVerseExistsWithoutSubverses(elts)


  fun verseExistsWithOrWithoutSubverses (verseOrSubverseRef: Ref)            = commonVerseExistsWithOrWithoutSubverses(makeElts(verseOrSubverseRef))
  fun verseExistsWithOrWithoutSubverses (verseOrSubverseRefKey: RefKey)      = commonVerseExistsWithOrWithoutSubverses(makeElts(verseOrSubverseRefKey))
  fun verseExistsWithOrWithoutSubverses (b: Int, c: Int, v: Int, s: Int = 0) = commonVerseExistsWithOrWithoutSubverses(makeElts(b, c, v, s))
  fun verseExistsWithOrWithoutSubverses (verseOrSubverseRefAsString: String) = commonVerseExistsWithOrWithoutSubverses(makeElts(verseOrSubverseRefAsString))
  fun verseExistsWithOrWithoutSubverses (elts: IntArray)                     = commonVerseExistsWithOrWithoutSubverses(elts)



  fun verseOrSubverseExistsAsSpecified (verseOrSubverseRef: Ref)            = commonVerseOrSubverseExistsAsSpecified(makeElts(verseOrSubverseRef))
  fun verseOrSubverseExistsAsSpecified (verseOrSubverseRefKey: RefKey)      = commonVerseOrSubverseExistsAsSpecified(makeElts(verseOrSubverseRefKey))
  fun verseOrSubverseExistsAsSpecified (b: Int, c: Int, v: Int, s: Int = 0) = commonVerseOrSubverseExistsAsSpecified(makeElts(b, c, v, s))
  fun verseOrSubverseExistsAsSpecified (verseOrSubverseRefAsString: String) = commonVerseOrSubverseExistsAsSpecified(makeElts(verseOrSubverseRefAsString))
  fun verseOrSubverseExistsAsSpecified (elts: IntArray)                     = commonVerseOrSubverseExistsAsSpecified(elts)



  fun thingExists (chapterRef: Ref)                        = commonThingExists(makeElts(chapterRef))
  fun thingExists (chapterRefKey: RefKey)                  = commonThingExists(makeElts(chapterRefKey))
  fun thingExists (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonThingExists(makeElts(b, c, 0, 0))
  fun thingExists (chapterRefAsString: String)             = commonThingExists(makeElts(chapterRefAsString))
  fun thingExists (elts: IntArray)                         = commonThingExists(elts)



  fun getLastChapterNo (bookRef: Ref)                               = commonGetLastChapterNo(makeElts(bookRef))
  fun getLastChapterNo (bookRefKey: RefKey)                         = commonGetLastChapterNo(makeElts(bookRefKey))
  fun getLastChapterNo (b: Int, c: Int = 0, v: Int = 0, s: Int = 0) = commonGetLastChapterNo(makeElts(b, 0, 0, 0))
  fun getLastChapterNo (bookRefAsString: String)                    = commonGetLastChapterNo(makeElts(bookRefAsString))
  fun getLastChapterNo (elts: IntArray)                             = commonGetLastChapterNo(elts)



  fun getLastVerseNo (chapterRef: Ref)                        = commonGetLastVerseNo(makeElts(chapterRef))
  fun getLastVerseNo (chapterRefKey: RefKey)                  = commonGetLastVerseNo(makeElts(chapterRefKey))
  fun getLastVerseNo (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonGetLastVerseNo(makeElts(b, c, 0, 0))
  fun getLastVerseNo (chapterRefAsString: String)             = commonGetLastVerseNo(makeElts(chapterRefAsString))
  fun getLastVerseNo (elts: IntArray)                         = commonGetLastVerseNo(elts)



  fun getNodeListForCanonicalTitle (chapterRef: Ref)                        = commonGetNodeListForCanonicalTitle(makeElts(chapterRef.getB(), chapterRef.getC(), 0, 0))
  fun getNodeListForCanonicalTitle (chapterRefKey: RefKey)                  = commonGetNodeListForCanonicalTitle(makeElts(Ref.getB(chapterRefKey), Ref.getC(chapterRefKey), 0, 0))
  fun getNodeListForCanonicalTitle (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonGetNodeListForCanonicalTitle(makeElts(b, c, 0, 0))
  fun getNodeListForCanonicalTitle (chapterRefAsString: String)             = getNodeListForCanonicalTitle(m_FileProtocol!!.readRef(chapterRefAsString).toRefKey())
  fun getNodeListForCanonicalTitle (elts: IntArray)                         = commonGetNodeListForCanonicalTitle(makeElts(elts[0], elts[1], 0, 0))



  fun getCanonicalTextSize (verseOrSubverseRef: Ref)            = commonGetCanonicalTextSize(makeElts(verseOrSubverseRef))
  fun getCanonicalTextSize (verseOrSubverseRefKey: RefKey)      = commonGetCanonicalTextSize(makeElts(verseOrSubverseRefKey))
  fun getCanonicalTextSize (b: Int, c: Int, v: Int, s: Int = 0) = commonGetCanonicalTextSize(makeElts(b, c, v, s))
  fun getCanonicalTextSize (verseOrSubverseRefAsString: String) = commonGetCanonicalTextSize(makeElts(verseOrSubverseRefAsString))
  fun getCanonicalTextSize (elts: IntArray)                     = commonGetCanonicalTextSize(elts)



  fun getCanonicalTextSizeForCanonicalTitle (chapterRef: Ref)                        = commonGetCanonicalTextSizeForCanonicalTitle(makeElts(chapterRef.getB(), chapterRef.getC(), 0, 0))
  fun getCanonicalTextSizeForCanonicalTitle (chapterRefKey: RefKey)                  = commonGetCanonicalTextSizeForCanonicalTitle(makeElts(Ref.getB(chapterRefKey), Ref.getC(chapterRefKey), 0, 0))
  fun getCanonicalTextSizeForCanonicalTitle (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonGetCanonicalTextSizeForCanonicalTitle(makeElts(b, c, 0, 0))
  fun getCanonicalTextSizeForCanonicalTitle (chapterRefAsString: String)             = getCanonicalTextSizeForCanonicalTitle(m_FileProtocol!!.readRef(chapterRefAsString).toRefKey())
  fun getCanonicalTextSizeForCanonicalTitle (elts: IntArray)                         = commonGetCanonicalTextSizeForCanonicalTitle(makeElts(elts[0], elts[1], 0, 0))



  fun getAllRefKeys () = m_Text.m_Content.m_ContentMap.map { (bookNo, bd) -> getAllAsRefKeys(bookNo, bd) }.flatten()

  fun getAllRefKeysForBook (bookRef: Ref)                               = commonGetAllRefKeysForBook(makeElts(bookRef))
  fun getAllRefKeysForBook (bookRefKey: RefKey)                         = commonGetAllRefKeysForBook(makeElts(bookRefKey))
  fun getAllRefKeysForBook (b: Int, c: Int = 0, v: Int = 0, s: Int = 0) = commonGetAllRefKeysForBook(makeElts(b, 0, 0, 0))
  fun getAllRefKeysForBook (bookRefAsString: String)                    = commonGetAllRefKeysForBook(makeElts(bookRefAsString))
  fun getAllRefKeysForBook (elts: IntArray)                             = commonGetAllRefKeysForBook(elts)



  fun getAllRefKeysForChapter (chapterRef: Ref)                        = commonGetAllRefKeysForChapter(makeElts(chapterRef))
  fun getAllRefKeysForChapter (chapterRefKey: RefKey)                  = commonGetAllRefKeysForChapter(makeElts(chapterRefKey))
  fun getAllRefKeysForChapter (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonGetAllRefKeysForChapter(makeElts(b, c, 0, 0))
  fun getAllRefKeysForChapter (chapterRefAsString: String)             = commonGetAllRefKeysForChapter(makeElts(chapterRefAsString))
  fun getAllRefKeysForChapter (elts: IntArray)                         = commonGetAllRefKeysForChapter(elts)



  fun hasCanonicalTitle (chapterRef: Ref)                        = commonHasCanonicalTitle(makeElts(chapterRef.getB(), chapterRef.getC(), 0, 0))
  fun hasCanonicalTitle (chapterRefKey: RefKey)                  = commonHasCanonicalTitle(makeElts(Ref.getB(chapterRefKey), Ref.getC(chapterRefKey), 0, 0))
  fun hasCanonicalTitle (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonHasCanonicalTitle(makeElts(b, c, 0, 0))
  fun hasCanonicalTitle (chapterRefAsString: String)             = hasCanonicalTitle(m_FileProtocol!!.readRef(chapterRefAsString).toRefKey())
  fun hasCanonicalTitle (elts: IntArray)                         = commonHasCanonicalTitle(makeElts(elts[0], elts[1], 0, 0))



  fun hasSubverses () = m_Text.m_SubverseDetails.isNotEmpty()
  fun getAllSubverses () = m_Text.m_SubverseDetails.keys




  /****************************************************************************/
  /* Errors, issues and oddities. */

  open fun getDuplicateVersesForText () = m_Text.m_DuplicateVerses



  fun getMissingEmbeddedChaptersForText ()= aggregateOverBooksInText(::getMissingEmbeddedChaptersForBook)

  fun getMissingEmbeddedChaptersForBook (bookRef: Ref)                               = commonGetMissingEmbeddedChaptersForBook(makeElts(bookRef))
  fun getMissingEmbeddedChaptersForBook (bookRefKey: RefKey)                         = commonGetMissingEmbeddedChaptersForBook(makeElts(bookRefKey))
  fun getMissingEmbeddedChaptersForBook (b: Int, c: Int = 0, v: Int = 0, s: Int = 0) = commonGetMissingEmbeddedChaptersForBook(makeElts(b, 0, 0, 0))
  fun getMissingEmbeddedChaptersForBook (bookRefAsString: String)                    = commonGetMissingEmbeddedChaptersForBook(makeElts(bookRefAsString))
  fun getMissingEmbeddedChaptersForBook (elts: IntArray)                             = commonGetMissingEmbeddedChaptersForBook(elts)



  fun getMissingEmbeddedVersesForText () = aggregateOverBooksInText(::getMissingEmbeddedVersesForBook)

  fun getMissingEmbeddedVersesForBook (bookRef: Ref)                               = commonGetMissingEmbeddedVersesForBook(makeElts(bookRef))
  fun getMissingEmbeddedVersesForBook (bookRefKey: RefKey)                         = commonGetMissingEmbeddedVersesForBook(makeElts(bookRefKey))
  fun getMissingEmbeddedVersesForBook (b: Int, c: Int = 0, v: Int = 0, s: Int = 0) = commonGetMissingEmbeddedVersesForBook(makeElts(b, c, 0, 0))
  fun getMissingEmbeddedVersesForBook (bookRefAsString: String)                    = commonGetMissingEmbeddedVersesForBook(makeElts(bookRefAsString))
  fun getMissingEmbeddedVersesForBook (elts: IntArray)                             = commonGetMissingEmbeddedVersesForBook(elts)


  fun getMissingEmbeddedVersesForChapter (chapterRef: Ref)                        = commonGetMissingEmbeddedVersesForChapter(makeElts(chapterRef))
  fun getMissingEmbeddedVersesForChapter (chapterRefKey: RefKey)                  = commonGetMissingEmbeddedVersesForChapter(makeElts(chapterRefKey))
  fun getMissingEmbeddedVersesForChapter (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonGetMissingEmbeddedVersesForChapter(makeElts(b, c, 0, 0))
  fun getMissingEmbeddedVersesForChapter (chapterRefAsString: String)             = commonGetMissingEmbeddedVersesForChapter(makeElts(chapterRefAsString))
  fun getMissingEmbeddedVersesForChapter (elts: IntArray)                         = commonGetMissingEmbeddedVersesForChapter(elts)



  open fun otBooksAreInOrder () = null == MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllBookNumbersOt())
  open fun ntBooksAreInOrder () = null == MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllBookNumbersNt())
  open fun dcBooksAreInOrder () = null == MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllBookNumbersDc())

  fun standardBooksAreInOrder (): Boolean
  {
    val standardBooks = getAllBookNumbersOt() + getAllBookNumbersNt()
    return null == MiscellaneousUtils.checkInStrictlyAscendingOrder(standardBooks)
  }



  fun chaptersAreInOrder (bookRef: Ref)                               = commonChaptersAreInOrder(makeElts(bookRef))
  fun chaptersAreInOrder (bookRefKey: RefKey)                         = commonChaptersAreInOrder(makeElts(bookRefKey))
  fun chaptersAreInOrder (b: Int, c: Int = 0, v: Int = 0, s: Int = 0) = commonChaptersAreInOrder(makeElts(b, 0, 0, 0))
  fun chaptersAreInOrder (bookRefAsString: String)                    = commonChaptersAreInOrder(makeElts(bookRefAsString))
  fun chaptersAreInOrder (elts: IntArray)                             = commonChaptersAreInOrder(elts)



  fun getOutOfOrderVerses () : List<RefKey>
  {
    var ot = getAllBookNumbersOt().mapNotNull { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }
    var nt = getAllBookNumbersNt().mapNotNull { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }
    var dc = getAllBookNumbersDc().mapNotNull { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }
    return (ot.toSet() union nt.toSet() union dc.toSet()).toList().sorted()
  }



  fun versesAreInOrder () : Boolean
  {
    var v = getAllBookNumbersOt().mapNotNull { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }
    if (v.isNotEmpty()) return false

    v = getAllBookNumbersNt().mapNotNull { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }
    if (v.isNotEmpty()) return false

    v = getAllBookNumbersDc().mapNotNull { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }
    if (v.isNotEmpty()) return false

    return true
  }


  fun versesAreInOrder (chapterRef: Ref)                        = commonVersesAreInOrder(makeElts(chapterRef))
  fun versesAreInOrder (chapterRefKey: RefKey)                  = commonVersesAreInOrder(makeElts(chapterRefKey))
  fun versesAreInOrder (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonVersesAreInOrder(makeElts(b, c, 0, 0))
  fun versesAreInOrder (chapterRefAsString: String)             = commonVersesAreInOrder(makeElts(chapterRefAsString))
  fun versesAreInOrder (elts: IntArray)                         = commonVersesAreInOrder(elts)



  fun allChaptersArePresent (bookRef: Ref)                              = commonAllChaptersArePresent(makeElts(bookRef))
  fun allChaptersArePresent (bookRefKey: RefKey)                        = commonAllChaptersArePresent(makeElts(bookRefKey))
  fun allChaptersArePresent (b: Int, c: Int = 0, v: Int =0, s: Int = 0) = commonAllChaptersArePresent(makeElts(b, 0, 0, 0))
  fun allChaptersArePresent (bookRefAsString: String)                   = commonAllChaptersArePresent(makeElts(bookRefAsString))
  fun allChaptersArePresent (elts: IntArray)                            = commonAllChaptersArePresent(elts)



  fun allVersesArePresent (chapterRef: Ref)                        = commonAllVersesArePresent(makeElts(chapterRef))
  fun allVersesArePresent (chapterRefKey: RefKey)                  = commonAllVersesArePresent(makeElts(chapterRefKey))
  fun allVersesArePresent (b: Int, c: Int, v: Int = 0, s: Int = 0) = commonAllVersesArePresent(makeElts(b, c, 0, 0))
  fun allVersesArePresent (chapterRefAsString: String)             = commonAllVersesArePresent(makeElts(chapterRefAsString))
  fun allVersesArePresent (elts: IntArray)                         = commonAllVersesArePresent(elts)





  /****************************************************************************/
  /**
  * Returns the verse nodes which were marked as elisions.
  */

  fun getElisionVerseNodes () = m_Text.m_ElisionDetails.map { it.value }


  /****************************************************************************/
  /**
   * Given a low and a high reference, returns a list of all the references
   * between low and high (inclusive).  The two must be in the same book, but
   * need not be in the same chapter.  Any subverse indicators in the reference
   * keys are ignored.
   *
   * This needs a little more explanation.  This method is a relative new kid on
   * the block, but builds upon earlier work which was aimed at providing
   * anatomical information about NRSVA.
   *
   * I anticipate that the method will be called mainly when handling
   * vernacular text, where the problem is that we don't necessarily know ahead
   * of time where verse boundaries will fall -- nor, indeed, what verses the
   * text may contain, since it will perhaps not follow NRSVA.
   *
   * It will, I think, be called under two circumstances -- first, when
   * processing elided verses with a view to expanding them out so we can find
   * out what actual verses are present; and later, once we have established
   * this information, in order to expand out eg ranges in cross-references.
   *
   * I believe that in the former case, we can rely upon ranges not crossing
   * chapter boundaries, because if they did there would be no place in the USX
   * where chapter tags can be inserted.  This is useful, because it makes it
   * easier to rely upon NRSVA information, which at this point will be the
   * only information available to us ...
   *
   * If both start and end are recognised as NRSVA verses, we can simply return
   * a collection running from one to the other (something which is facilitated
   * by the fact that at this point, getAllReferencesForChapter will
   * automatically look at the NRSVA data.
   *
   * If neither start nor end are recognised as NRSVA verses, I can make the
   * assumption that they are both in that portion of the versification scheme
   * outside of NRSVA, and can return a collection running from the one to the
   * other.
   *
   * I don't think it's possible to have the situation at this point where the
   * low reference is outside of NRSVA and the high reference is inside it, or
   * certainly not without crossing a chapter boundary, so this is a situation
   * for which I do not cater.
   *
   * It is possible to have the high reference outside of NRSVA, in which case
   * I return a collection which runs from lowRef to the end of the NRSVA verses
   * for the containing chapter, and then appends everything up to and including
   * the high ref.
   *
   * Later, once this initial anatomical investigation had been completed, we
   * will know what verses we actually have.  At this point,
   * getAllReferencesForChapter will look at the actual anatomy (ie it will no
   * RefKeyer look at NRSVA), and so ranges can be expanded correctly.
   *
   * @param theRefKeyLow What it says on the tin.
   * @param theRefKeyHigh What it says on the tin.
   *
   * @return Full list of all references for given book of the NRSVA.
   */

  open fun getRefKeysInRange (theRefKeyLow: RefKey, theRefKeyHigh: RefKey): List<RefKey>
  {
    /**************************************************************************/
    val refKeyLow  = Ref.clearS(theRefKeyLow)
    val refKeyHigh = Ref.clearS(theRefKeyHigh)

    val bookNo = Ref.getB(refKeyLow)
    val chapterNoLow = Ref.getC(refKeyLow)
    val chapterNoHigh = Ref.getC(refKeyHigh)
    var res: MutableList<RefKey> = ArrayList()



    /**************************************************************************/
    /* Strictly no need to factor this out as a special case, but it will
       commonly be the case that low and high are the same, and if they are,
       we just return that one ref, regardless of whether the verse is in
       the collection for NRSVA or not. */

    if (refKeyLow == refKeyHigh)
    {
      res.add(refKeyLow)
      return res
    }



    /**************************************************************************/
    /* In the normal course of events we'd expect this to work, but if we're
       being called in respect of vernacular text, it's always possible that
       the vernacular may have a chapter which NRSVA does not.  In that case,
       we assume that the caller knows what they're doing, so we don't give up
       here -- and later processing will simply give back a range based on what
       they've asked for. */

    for (chapterNo in chapterNoLow .. chapterNoHigh)
      try { res.addAll(getAllRefKeysForChapter(bookNo, chapterNo)); } catch (_: Exception) {}



    /**************************************************************************/
    /* If res is empty, we must be dealing with a chapter unknown to NRSVA, so
       we just return a range from low to high.  I've also come across a
       situation where both low and high are in the same chapter, but neither of
       them features in the list of references, and this can be treated the
       same way. */

    val ixLow  = res.indexOf(refKeyLow)
    val ixHigh = res.indexOf(refKeyHigh)

    if (res.isEmpty() || ((-1 == ixLow || -1 == ixHigh) && chapterNoLow == chapterNoHigh))
    {
      res.clear()
      var verse = Ref.getV(refKeyLow)
      var n = refKeyLow
      while (n <= refKeyHigh)
      {
        res.add(n)
        n = Ref.setV(n, ++verse)
      }

      return res
    }



    /**************************************************************************/
    /* Check if the low value exists in the list of references.  If not, find
       the first reference in the list which comes _after_ it, and then amend
       the list so we have the low reference itself followed by those things
       which come after it. */

    if (-1 == ixLow)
      throw StepException("Bad case in getReferencesInRange: $refKeyLow : $refKeyHigh") // I think in fact we should never hit this situation.



    /**************************************************************************/
    /* If the high reference is in the list, we remove from the list everything
       after the high reference.  Otherwise, we just assume that we want the
       entire list plus everything up to the high reference. */

    if (ixHigh > -1)
      res = res.subList(0, ixHigh + 1)
    else
    {
      var n = res[res.size - 1]
      var verse = Ref.getV(n)
      while (true)
      {
        n = Ref.setV(n, ++verse)
        if (n > refKeyHigh) break
        res.add(n)
      }
    }



    /**************************************************************************/
    /* Chuck away stuff before the low ref. */

    res = res.subList(ixLow, res.size)
    return res
  }


  /****************************************************************************/
  /**
   * If both reference keys are to the same verse, and both have subverses,
   * returns the absolute difference between the two (as an integer number of
   * subverses).  Otherwise returns -1.  Note that I do mean 'difference' here
   * -- it's the difference, not the number of subverses.  If you have
   * subverses 3 and 4, you'll get 1, not 2.
   *
   * @param refKey1 Long-form for low subverse.
   * @param refKey2 Long-form for high subverse.
   * @return Difference.
   */

  open fun getSubverseDifference (refKey1: RefKey, refKey2: RefKey): Int
  {
    val r1 = Ref.rd(refKey1)
    val r2 = Ref.rd(refKey2)
    val cf1 = r1.toRefKey_bcv()
    val cf2 = r2.toRefKey_bcv()
    val x1 = r1.getS()
    val x2 = r2.getS()

    if (cf1 != cf2) return -1
    if ( RefBase.C_DummyElement == x1 || RefBase.C_DummyElement == x2 ) return -1

    return abs(x1 - x2)
  }


  /****************************************************************************/
  /**
   * Compares two verse references.
   * <p>
   * <ul>
   *   <li>If they are to different books, returns -1.</li>
   *
   *   <li>Otherwise returns the verse difference refKey2 - refKey1.  This
   *       caters both for verse in the same chapter and for verses in
   *       different chapters.</li>
   * </ul>
   *
   * @param theRefKey1 Refkey for low verse.
   * @param theRefKey2 Refkey for high verse.
   * @return Verse difference.
   */

  open fun getVerseDifference (theRefKey1: RefKey, theRefKey2: RefKey): Int
  {
    /**************************************************************************/
    var refKey1 = theRefKey1
    var refKey2 = theRefKey2
    val bookNo = Ref.getB(refKey1)
    if (bookNo != Ref.getB(refKey2)) return -1 // Different books.



    /**************************************************************************/
    var chapter1 = Ref.getC(refKey1)
    var chapter2 = Ref.getC(refKey2)
    if (chapter1 == chapter2) return Ref.getV(refKey2) - Ref.getV(refKey1) // Same chapter.  Return difference between chapter numbers.



    /**************************************************************************/
    /* Swap so that we are running from low to high, but remember the fact we've
       done so. */

    var multiplier = 1
    if (refKey2 < refKey1)
    {
      val x = refKey1; refKey1 = refKey2; refKey2 = x
      val xx = chapter1; chapter1 = chapter2; chapter2 = xx
      multiplier = -1
    }



    /**************************************************************************/
    var n = getLastVerseNo(bookNo, chapter1)
    n -= Ref.getV(refKey1)
    for (i in chapter1 + 1..< chapter2)
      n += getLastVerseNo(bookNo, i)
    n += Ref.getV(refKey2)

    return multiplier * n
  }


  /****************************************************************************/
  /**
  * Determines whether two references are adjacent.  The processing is as
  * follows:
  *
  * - If either reference is to a subverse, they are adjacent if the
  *   refKeys are consecutive.
  *
  * - Otherwise (if neither if a subverse), they are adjacent if they are
  *   consecutive verses of the same chapter of the same book.
  *
  * - Otherwise, they are adjacent if the lower reference is for the last
  *   verse of a chapter and the higher reference is for the first verse
  *   of a chapter.
  */

  open fun isAdjacent (lowBcvs: RefKey, highBcvs: RefKey): Boolean
  {
    val lowBcv = Ref.clearS(lowBcvs)
    val highBcv   = Ref.clearS(highBcvs)

    if (Ref.hasS(lowBcvs) || Ref.hasS(highBcvs))
      return lowBcvs == highBcvs - 1

    if (lowBcv == highBcv - Ref.rd(0, 0, 1, 0).toRefKey())
      return true

    if (1 != Ref.getV(highBcv))
      return false

    if (Ref.getC(lowBcvs) != Ref.getC(highBcvs) - 1)
      return false

    return getLastVerseNo(lowBcvs) == Ref.getV(lowBcvs)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Protected                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* protected because this method needs to be accessed when dealing with
     osis2mod schemes. */

  protected fun addVerse (refKey: RefKey): VerseDescriptor
  {
    /**************************************************************************/
//    Dbg.d(Ref.rd(refKey).toString())
//    Dbg.d(Ref.rd(refKey).toString(), "Psa 44:1")



    /**************************************************************************/
    /* Find or create the book entry within the text. */

    var refNo = Ref.getB(refKey)
    var b = m_Text.m_Content.m_ContentMap[refNo]
    if (null == b)
    {
      b = BookDescriptor()
      m_Text.m_Content.addEntry(refNo, b)
    }



    /**************************************************************************/
    /* Find or create the chapter entry within the book. */

    refNo = Ref.getC(refKey)
    var c = b.m_Content.m_ContentMap[refNo]
    if (null == c)
    {
      c = ChapterDescriptor()
      b.m_Content.addEntry(refNo, c)
    }



    /**************************************************************************/
    /* Find or create the verse entry within the chapter.  By contrast with
       the previous steps, this time it's worrying if the thing already
       exists. */

    refNo = Ref.getV(refKey) * C_Multiplier + Ref.getS(refKey)
    var v = c.m_Content.m_ContentMap[refNo]
    if (null == v)
    {
      v = VerseDescriptor()
      c.m_Content.addEntry(refNo, v)
    }
    else
      m_Text.m_DuplicateVerses.add(refKey)



    /**************************************************************************/
    return v
  }


  /****************************************************************************/
  protected open fun commonAllChaptersArePresent (elts: IntArray): Boolean
  {
    return getBookDescriptor(elts)!!.m_Content.getMissingEntries().isEmpty()
  }


  /****************************************************************************/
  protected open fun commonAllVersesArePresent (elts: IntArray): Boolean
  {
    return getChapterDescriptor(elts)!!.m_Content.getMissingEntries().isEmpty()
  }


  /****************************************************************************/
  protected open fun commonBookExists (elts: IntArray): Boolean
  {
    return null != getBookDescriptor(elts)
  }


  /****************************************************************************/
  protected open fun commonChapterExists (elts: IntArray): Boolean
  {
    return null != getChapterDescriptor(elts)
  }


  /****************************************************************************/
  protected open fun commonChaptersAreInOrder (elts: IntArray): Boolean
  {
    return null == MiscellaneousUtils.checkInStrictlyAscendingOrder(getBookDescriptor(elts)!!.m_Content.m_ContentMap.keys.toList())
  }


  /****************************************************************************/
  protected open fun commonGetAllRefKeysForBook (elts: IntArray): List<RefKey>
  {
    return getAllAsRefKeys(elts[0], getBookDescriptor(elts)!!)
  }


  /****************************************************************************/
  protected open fun commonGetAllRefKeysForChapter (elts: IntArray): List<RefKey>
  {
    val cd = getChapterDescriptor(elts)!!
    return getAllAsRefKeys(elts[0], elts[1], cd)
  }


  /****************************************************************************/
  protected open fun commonGetLastChapterNo (elts: IntArray): Int
  {
    return getBookDescriptor(elts)!!.m_Content.m_Limits.m_HighIx
  }


  /****************************************************************************/
  protected open fun commonGetLastVerseNo (elts: IntArray): Int
  {
    val cd = getChapterDescriptor(elts)
    return if (null == cd) C_Unavailable else Ref.getV(cd!!.m_Content.m_Limits.m_HighIx.toLong())
  }


  /****************************************************************************/
  protected open fun commonGetMissingEmbeddedChaptersForBook (elts: IntArray): List<RefKey>
  {
    val numbers = getBookDescriptor(elts)!!.m_Content.getMissingEntries()
    val refKey = Ref.rd(elts).toRefKey()
    return numbers.map { Ref.setC(refKey, it) }
  }


  /****************************************************************************/
  protected open fun commonGetMissingEmbeddedVersesForBook (elts: IntArray): List<RefKey>
  {
    return aggregateOverChaptersInBook(elts, ::getMissingEmbeddedVersesForChapter)
  }


  /****************************************************************************/
  protected open fun commonGetMissingEmbeddedVersesForChapter (elts: IntArray): List<RefKey>
  {
    val cd = getChapterDescriptor(elts)!!
    val verses = cd.m_Content.m_ContentMap.keys.map { Ref.rd(it.toLong()).getV() } .toMutableSet()

    val baseRef = Ref.rd(elts).toRefKey_bcv()
    var prevVerse = 0
    var res: MutableList<RefKey> = mutableListOf()

    verses.forEach {
      for (i in prevVerse + 1 ..< it)
        if (i !in verses)
          res.add(Ref.setV(baseRef, i))
      prevVerse = it
    }

    return res
  }


  /****************************************************************************/
  protected open fun commonGetCanonicalTextSize (elts: IntArray): Int
  {
    /**************************************************************************/
    if (!m_CollectingCanonicalTextSize)
      throw StepException("Canonical text size for verse requested, but never asked to accumulate this information.")



    /**************************************************************************/
    /* If we've been asked about a subverse, return the count for that subverse
       alone.  I assume subverses can never be part of an elision, so I don't
       worry about that here. */

    if (RefBase.C_DummyElement != elts[3]) // If asked for a subverse, return precisely that.
      return getVerseDescriptor(elts)!!.m_CanonicalTextSize



    /**************************************************************************/
    /* If we've been asked about a verse which is part of an elision, return a
       dummy value. */

    if (makeRef_bcv(elts) in m_Text.m_ElisionDetails)
      return C_ElementInElision



    /**************************************************************************/
    /* If we've been asked about a verse which is not made up of subverses,
       we will want only the verse itself.  Otherwise we'll want to accumulate
       the count across all subverses. */

    var lowSubverse = 0
    var highSubverse = 0
    val subverseDetails = m_Text.m_SubverseDetails[makeRef_bcv(elts)]
    if (null != subverseDetails)
    {
      lowSubverse = subverseDetails.m_LowIx
      highSubverse = subverseDetails.m_HighIx
    }

    var count = 0
    val tempElts = elts.clone()
    for (subverse in lowSubverse .. highSubverse)
    {
      tempElts[3] = subverse
      count += getVerseDescriptor(tempElts)!!.m_CanonicalTextSize
    }

    return count
  }


  /****************************************************************************/
  protected open fun commonGetNodeListForCanonicalTitle (elts: IntArray) = m_Text.m_CanonicalTitleDetails[Ref.rd(elts[0], elts[1], 0).toRefKey()]?.nodes


  /****************************************************************************/
  protected open fun commonGetCanonicalTextSizeForCanonicalTitle (elts: IntArray) = m_Text.m_CanonicalTitleDetails[Ref.rd(elts[0], elts[1], 0).toRefKey()]?.canonicalTextSize


  /****************************************************************************/
  protected open fun commonHasCanonicalTitle (elts: IntArray) = Ref.rd(elts[0], elts[1], 0).toRefKey() in m_Text.m_CanonicalTitleDetails


  /****************************************************************************/
  protected open fun commonThingExists (elts: IntArray): Boolean
  {
    if (RefBase.C_DummyElement == elts[1]) return commonBookExists(elts)
    if (RefBase.C_DummyElement == elts[2]) return commonChapterExists(elts)
    if (RefBase.C_DummyElement == elts[3]) return commonVerseExistsWithOrWithoutSubverses(elts)
    return commonVerseOrSubverseExistsAsSpecified(elts)
  }


  /****************************************************************************/
  protected open fun commonVerseExistsWithOrWithoutSubverses (elts: IntArray): Boolean
  {
    val eltsWithoutSubverse = elts.clone(); elts[3] = 0
    return null != getVerseDescriptor(eltsWithoutSubverse) || null != getVerseDescriptor(elts)
  }


  /****************************************************************************/
  protected open fun commonVerseExistsWithoutSubverses (elts: IntArray): Boolean
  {
    val eltsWithoutSubverse = elts.clone(); elts[3] = 0
    if (null == getVerseDescriptor(eltsWithoutSubverse)) return false
    return makeRef_bcv(eltsWithoutSubverse) !in m_Text.m_SubverseDetails
  }


  /****************************************************************************/
  protected open fun commonVerseOrSubverseExistsAsSpecified (elts: IntArray): Boolean
  {
    return null != getVerseDescriptor(elts)
  }


  /****************************************************************************/
  protected open fun commonVersesAreInOrder (elts: IntArray): Boolean
  {
    return null == MiscellaneousUtils.checkInStrictlyAscendingOrder(getChapterDescriptor(elts)!!.m_Content.m_ContentMap.keys.toList())
  }


  /****************************************************************************/
  /* This determines the relevance of any given node to the 'load' function
     in BibleStructure.  That function is interested in verse starts and ends.
     Previously it was also interested in canonical titles, but that was based
     on a misunderstanding on my part as to what actually _was_ a canonical
     title from the perspective of reversification, and I now have to handle
     canonical titles separately. */

  protected open fun getRelevanceOfNode (node: Node): NodeRelevance
  {
    /**************************************************************************/
    if (Dom.isTextNode(node))
      return NodeRelevance(NodeRelevanceType.Text, "", false)



    /**************************************************************************/
    /* Not a verse. */

    if (m_FileProtocol!!.tagName_verse() != Dom.getNodeName(node))
      return NodeRelevance(NodeRelevanceType.Boring, "", false)



    /**************************************************************************/
    /* It's a verse ... */

    val id = node[m_FileProtocol!!.attrName_verseSid()] ?: node[m_FileProtocol!!.attrName_verseEid()]!!
    if (RefBase.C_DummyElement == m_FileProtocol!!.readRefCollection(id).getLowAsRef().getV()) // Ignore dummy verses which I insert at the ends of chapters to simplify processing.
      return NodeRelevance(NodeRelevanceType.Boring, "", false)

    return if (m_FileProtocol.attrName_verseSid() in node)
      NodeRelevance(NodeRelevanceType.VerseStart, id, false)
    else
      NodeRelevance(NodeRelevanceType.VerseEnd, id, false)
  }


  /****************************************************************************/
  /**
  * Indicates whether a given node is non-canonical.  Need cater only for
  * node which can legitimately appear within a verse or a canonical title.
  *
  * @param node
  * @return True if node is non-canonical
  */

  private fun isNonCanonical (node: Node): Boolean = m_FileProtocol!!.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node)


  /****************************************************************************/
  /**
  * Loads the data structures from a given document, accumulating a word count
  * where requested and possible.  This outline structure should almost
  * certainly be fine for any XML-based representation, but you may want to
  * override it for other representations.
  *
  * @param doc Document from which data is taken.  (This data is added to any
  *   data already stored -- it does not *replace* it.)
  *
  * @param wantCanonicalTextSize What it says on the tin.
  */

  protected open fun addFromDoc (doc: Document, wantCanonicalTextSize: Boolean)
  {
    doc.getAllNodesBelow()
      .filter { m_FileProtocol!!.isBookNode(it) }
      .forEach { addFromRootNode(it, wantCanonicalTextSize) }
  }


  /****************************************************************************/
  /**
  * Loads the data structures from a given document, accumulating a word count
  * where requested and possible.  This outline structure should almost
  * certainly be fine for any XML-based representation, but you may want to
  * override it for other representations.
  *
  * @param rootNode Book node from which data is taken.  (This data is added
  *   to any data already stored -- it does not *replace* it.)
  *
  * @param wantCanonicalTextSize What it says on the tin.
  */

  protected open fun addFromRootNode (rootNode: Node, wantCanonicalTextSize: Boolean)
  {
    /**************************************************************************/
    var canonicalTitleCanonicalTextSize = 0
    var inCanonicalTitle = false
    var inVerse = false
    var isElision = false
    var verseCanonicalTextSize = 0



    /**************************************************************************/
    /* I aim to make this class usable under various circumstances.  In
       particular I want to be able to use it even if the text lacks eids.
       However, if it _does_ lack them, I insert them (in a valid but non-
       optimal position, because that's quite easy) to simplify later
       processing.  I then delete them again when I've finished. */

    val neededToInsertVerseEids = rootNode.findNodesByAttributeName(m_FileProtocol!!.tagName_verse(), m_FileProtocol!!.attrName_verseEid()).isEmpty()
    if (neededToInsertVerseEids)
      rootNode.findNodesByName(m_FileProtocol!!.tagName_chapter()).forEach(::insertVerseEids)



    /**************************************************************************/
    fun textIsOfInterest (textNode: Node): Boolean
    {
      if (Dom.isWhitespace(textNode))
        return false

      var parent = textNode
      while (true)
     {
        parent = parent.parentNode ?: break
        if (m_FileProtocol.tagName_chapter() == Dom.getNodeName(parent))
          return true
        else if (m_FileProtocol.tagName_note() == Dom.getNodeName(parent))
          return false
        else if (isNonCanonical(parent))
          return false
      }

      return true
    }



    /**************************************************************************/
    /* Decides whether text needs to be added to the word count. */

    fun processText (node: Node)
    {
      if (!textIsOfInterest(node))
        return

      //Dbg.d(getCanonicalTextSizeForVerse(node.textContent).toString() + ": " + node.textContent)

      if (inCanonicalTitle)
        canonicalTitleCanonicalTextSize += getCanonicalTextSizeForVerse(node.textContent)

      else if (inVerse && !isElision)
        verseCanonicalTextSize += getCanonicalTextSizeForVerse(node.textContent)
    }



    /**************************************************************************/
    //Dbg.d(rootNode.ownerDocument)
    rootNode.getAllNodesBelow().forEach {
      //Dbg.dCont(Dom.toString(it), "Num.10.34")
      //Dbg.d(Dom.toString(it))
      var processNode = true
      while (processNode)
      {
        val relevance = getRelevanceOfNode(it)
        when (relevance.nodeType)
        {
          NodeRelevanceType.VerseStart ->
          {
//            Dbg.dCont(Dom.toString(it), "<verse osisID='Phil.1.16', sID='Phil.1.16'>")
//            Dbg.dCont(Dom.toString(it), "<verse osisID='Phil.1.17', sID='Phil.1.17'>")
            handleVerseSid(it, relevance.idAsString)
            isElision = "-" in relevance.idAsString || NodeMarker.hasElisionType(it); verseCanonicalTextSize = 0; inVerse = true
          }


          NodeRelevanceType.VerseEnd ->
          {
            handleVerseEid(relevance.idAsString, if (isElision) C_ElementInElision else verseCanonicalTextSize)
            isElision = false; inVerse = false
          }


          NodeRelevanceType.Text ->
          {
            if (m_CollectingCanonicalTextSize)
              processText(it)
          }


          NodeRelevanceType.Boring ->
          {
          }
        } // when

        processNode = relevance.andProcessThisNode
      } // while (processNode)
    } // forEach



    /**************************************************************************/
    /* On Psalms only, I need to see if we have canonical titles. */

    if (BibleAnatomy.C_BookNumberForPsa == m_FileProtocol.bookNameToNumber(m_FileProtocol!!.getBookAbbreviation(rootNode)))
      rootNode.findNodesByName(m_FileProtocol.tagName_chapter()).forEach(::processCanonicalTitleForChapter)



    /**************************************************************************/
    /* If I inserted eids here, it was only as a temporary measure, and they
       need to go again. */

    if (neededToInsertVerseEids)
      rootNode.findNodesByAttributeName(m_FileProtocol!!.tagName_verse(), m_FileProtocol!!.attrName_verseEid()).forEach(Dom::deleteNode)
  } // load


  /****************************************************************************/
  /* Looks for canonical title details (I anticipate this being called only on
     Psalms).

     A psalm is regarded as having a canonical title ...

     - Not if it has a para:d or title:psalm heading: not all texts will do so.

     - Not if according to NRSVA we would _expect_ this psalm to have a
       canonical title.

     - But if it has any canonical text prior to the first verse sid.


     The method takes all nodes from the start of the first such canonical
     text node to the end of the last one and retains a list of pointers to
     all of them.  (I'm very much hoping that they will all be direct children
     of the chapter, and not be contained in anything else -- if they _are_
     contained in anything else, I'm not too sure what the implications may
     be.)

     It retains a list of these nodes, along with a combined count of the
     number of words in all of the canonical text nodes. */

  private fun processCanonicalTitleForChapter (chapterNode: Node)
  {
    /**************************************************************************/
//    Dbg.dCont(Dom.toString(chapterNode), "Ps.44")
//    Dbg.d(Dom.toString(chapterNode))



    /**************************************************************************/
    var nodesOfInterest: MutableList<Node> = mutableListOf()
    var canonicalTextSize = 0
    val allNodes = chapterNode.getAllNodesBelow()



    /**************************************************************************/
    /* Get a list of canonical nodes prior to the first verse node. */

    for (n in allNodes)
    {
      if (m_FileProtocol!!.tagName_verse() == Dom.getNodeName(n)) // Stop when we hit the first verse.
        break

      if (m_FileProtocol.isCanonicalNode(n) && "#text" == n.nodeName)
        nodesOfInterest.add(n)
    } // for



    /**************************************************************************/
    /* Get word count.  I _believe_ I'm correct in saying here that we will
       have all relevant text nodes in the list, and none which are
       irrelevant, so the isCanonicalNode test below is probably redundant.
       I believe I'll get the correct word count even if we have a node which
       spans the first verse node and has text before it.  Mind you, if we
       have that situation, I'm not too sure what's going to happen with
       the processing more generally. */

    nodesOfInterest
      .filter { Dom.isTextNode(it) && m_FileProtocol!!.isCanonicalNode(it) }
      .forEach { canonicalTextSize += getCanonicalTextSizeForVerse(it.textContent) }



    /**************************************************************************/
    /* Reduce to top level nodes only. */

    nodesOfInterest = nodesOfInterest.filter { m_FileProtocol!!.tagName_chapter() == Dom.getNodeName(it.parentNode) } .toMutableList()



    /**************************************************************************/
    if (canonicalTextSize > 0 && nodesOfInterest.isNotEmpty())
      m_Text.m_CanonicalTitleDetails[m_FileProtocol!!.readRef(chapterNode[m_FileProtocol.attrName_chapterSid()]!!).toRefKey_bc()] =
        CanonicalTitleDetails(canonicalTextSize, nodesOfInterest.filter { m_FileProtocol!!.tagName_chapter() == Dom.getNodeName(it.parentNode) })
  }


  /****************************************************************************/
  /**
  * Assesses whether a node is of relevance to the processing here.
  * Relevance is determined purely in terms of whether the node impacts the
  * job of gathering word counts.
  */

  protected enum class NodeRelevanceType { VerseStart, VerseEnd, Text, Boring }
  protected data class NodeRelevance (val nodeType: NodeRelevanceType, val idAsString: String, val andProcessThisNode: Boolean)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This takes a string containing canonical content (possibly a number of
     words, along with spaces, punctuation, etc, and returns either a count
     of the actual word characters or the number of words.  I've hived this off
     to a separate method because we keep changing our minds as to whether we
     want words or characters.

     .length and .codePointCount both seem to give back the same number here.
     Don't be tempted to use .characterCount -- it _is_ tempting, because it
     claims to exclude spaces and punctuation from the count, but in fact it
     works only with Latin characters.

     I have wondered about applying a regex here myself to as to exclude spaces
     and punctuation, but it would slow the processing and I think there's
     probably no need: the count is used only when selecting reversification
     data, where a rough idea of the size is good enough.

     Talking of which, I have no idea whether this actually does give back
     anything meaningful -- the numbers I get here don't seem to correspond
     to the number of hits of the right-arrow key needed to step through the
     text in, say, Word.  However, we use the numbers only to compare the
     lengths of verses, and if the lengths are wrong, that doesn't matter too
     much so long as they're reasonably consistently wrong. */

  private val C_WordCharsRegex = "\\p{L}}".toRegex()
  private fun getCanonicalTextSizeForVerse (s: String) = s.length // s.replace(C_WordCharsRegex, "").trim().length // s.trim().codePointCount(0, s.length - 1)



  /****************************************************************************/
  /* In order to make the parsing work, it is convenient to have eids even if
     the text doesn't presently have any.  This method inserts a temporary
     verse end just before each verse start.  This is good enough so long as
     our main interest is determining which verses exist and which don't.  For
     counting words it's _probably_ also good enough.  But for avoiding cross-
     boundary markup, it won't be enough, so I rely on other processing to
     position verse ends more suitably at the point that cross-boundary markup
     becomes of interest. */

  private fun insertVerseEids (chapterNode: Node)
  {
    val dummyNode = chapterNode.ownerDocument.createNode("<${m_FileProtocol!!.tagName_verse()}/>")
    chapterNode.appendChild(dummyNode)
    val verseNodes = chapterNode.findNodesByName(m_FileProtocol.tagName_verse())
    for (i in 1..< verseNodes.size)
    {
      val newNode = chapterNode.ownerDocument.createNode("<${m_FileProtocol.tagName_verse()}/>")
      newNode[m_FileProtocol.attrName_verseEid()] = verseNodes[i - 1][m_FileProtocol.attrName_verseSid()]!!
      Dom.insertNodeBefore(verseNodes[i], newNode)
    }

    Dom.deleteNode(dummyNode)
  }


  /****************************************************************************/
  private val C_Multiplier = RefBase.C_Multiplier.toInt()
  private val m_BookAbbreviationToFilePathMappings: MutableMap<String, String> = mutableMapOf()
  private var m_CollectingCanonicalTextSize = false
  private var m_Populated = false // Used to make sure we don't use the facilities without first populating things.
  private var m_Text = TextDescriptor() // The root of the structure.





  /****************************************************************************/
  private class Limits
  {
    fun tryNewValue (value: Int)
    {
      if (value < m_LowIx ) m_LowIx  = value
      if (value > m_HighIx) m_HighIx = value
    }

    var m_LowIx = 99999
    var m_HighIx = -1
  }


  /****************************************************************************/
  private class ContentHolder<CONTENT_TYPE>
  {
    /**************************************************************************/
    fun addEntry (refNo: Int, entry: CONTENT_TYPE)
    {
      m_ContentMap[refNo] = entry
      m_Limits.tryNewValue(refNo)
    }


    /**************************************************************************/
    fun getMissingEntries (): List<Int>
    {
      val divisor = if (m_Limits.m_LowIx > RefBase.C_Multiplier.toInt()) RefBase.C_Multiplier.toInt() else 1
      val checkAgainst =  IntRange(m_Limits.m_LowIx / divisor, m_Limits.m_HighIx / divisor).toMutableSet()

      if (1 == divisor)
        checkAgainst.removeAll(m_ContentMap.keys)
      else
        checkAgainst.removeAll(m_ContentMap.keys.map { it / divisor }.toSet())

      return checkAgainst.toList()
    }


    /**************************************************************************/
    val m_ContentMap = LinkedHashMap<Int, CONTENT_TYPE>()
    val m_Limits = Limits()
  }


  /****************************************************************************/
  private data class CanonicalTitleDetails (val canonicalTextSize: Int, val nodes: List<Node>)


  /****************************************************************************/
  private class TextDescriptor
  {
    val m_Content = ContentHolder<BookDescriptor>() // The text is made up of books.
    val m_CanonicalTitleDetails: MutableMap<RefKey, CanonicalTitleDetails> = mutableMapOf()
    val m_DuplicateVerses: MutableList<RefKey> = mutableListOf() // Just a list of refkeys.
    val m_ElisionDetails = LinkedHashMap<RefKey, Node>() // Maps the refKey of any verse in an elision to the pair comprising the first and last refKey.
    val m_SubverseDetails: MutableMap<RefKey, Limits> = mutableMapOf() // Maps the refKey of the master verse for a subverse to the low and high subverse numbers making it up.
  }


  /****************************************************************************/
  private class BookDescriptor
  {
    val m_Content = ContentHolder<ChapterDescriptor>() // A book is made up of chapters.
  }


  /****************************************************************************/
  private class ChapterDescriptor
  {
    val m_Content = ContentHolder<VerseDescriptor>() // A chapter is made up of verses.
  }


  /****************************************************************************/
  /* Really want this as private rather than protected, but it appears in the
     signature of addVerse (which I _do_ need as protected), and that forces
     the issue. */

  protected class VerseDescriptor
  {
    var m_CanonicalTextSize: Int = 0
  }


  /****************************************************************************/
  /**
  * Adds the data from a given file to the current data structures.
  *
  * @param prompt Output to screen as part of progress indicator.
  * @param filePath
  * @param wantCanonicalTextSize True if we need to accumulate the text size.
  * @param bookName USX abbreviation.
 */

  private fun addFromFile (prompt: String, filePath: String, wantCanonicalTextSize: Boolean, bookName: String? = null)
  {
    addFromDoc(prompt = prompt, Dom.getDocument(filePath, false), wantCanonicalTextSize, filePath = filePath, bookName = bookName)
  }


  /****************************************************************************/
  private fun aggregateOverBooksInText (fn: (elts:IntArray) -> List<RefKey>): List<RefKey>
  {
    return m_Text.m_Content.m_ContentMap.map { (bookNo, bd) ->
      fn(intArrayOf(bookNo, 0, 0, 0)) } .flatten()
  }


  /****************************************************************************/
  private fun aggregateOverChaptersInBook (elts: IntArray, fn: (elts:IntArray) -> List<RefKey>): List<RefKey>
  {
    val bd = getBookDescriptor(elts)
    return bd!!.m_Content.m_ContentMap.map { (chapterNo, bd) ->
      fn(intArrayOf(elts[0], chapterNo, 0, 0)) } .flatten()
  }


  /****************************************************************************/
  fun clear ()
  {
    m_Text = TextDescriptor()
    m_Populated = false
  }


  /****************************************************************************/
  private fun getAllAsRefKeys (bookNo: Int, bd: BookDescriptor): List<RefKey>
  {
    return bd.m_Content.m_ContentMap.map { (chapterNo, cd) -> getAllAsRefKeys(bookNo, chapterNo, cd) }.flatten()
  }


  /****************************************************************************/
  private fun getAllAsRefKeys (bookNo: Int, chapterNo: Int, cd: ChapterDescriptor): List<RefKey>
  {
    val baseRefKey = Ref.rd(bookNo, chapterNo, RefBase.C_DummyElement, RefBase.C_DummyElement).toRefKey()
    return cd.m_Content.m_ContentMap.keys.map { baseRefKey + it }.toList()
  }


  /****************************************************************************/
  private fun getBookDescriptor    (elts: IntArray): BookDescriptor?    { return m_Text.m_Content.m_ContentMap[elts[0]] }
  private fun getChapterDescriptor (elts: IntArray): ChapterDescriptor? { val bd = getBookDescriptor(elts)    ?: return null; return bd.m_Content.m_ContentMap[elts[1]] }
  private fun getVerseDescriptor   (elts: IntArray): VerseDescriptor?   { val cd = getChapterDescriptor(elts) ?: return null; return cd.m_Content.m_ContentMap[makeRef_vs(elts).toInt()] }


  /****************************************************************************/
  /* This needs to cater for both single verses and elisions.  For a single
     verse, it will store the actual word count.  For an elision, each verse
     will be given the dummy value C_ElementInElision. */

  private fun handleVerseEid (id: String, canonicalTextSize: Int)
  {
//    if (Dbg.d(id, "Zech.4.10!a"))
//      Dbg.d(OsisTempDataCollection.getDocument())

    val refKeys = m_FileProtocol!!.readRefCollection(id).getAllAsRefs()
    val isElision = refKeys.size > 1

    refKeys.forEach {
      //if (Dbg.d(it.toString(), "Act 24:7"))
      //{
      //  debugDisplayStructure()
      //}

      val v = getVerseDescriptor(it.getCopyOfElements())!!
      v.m_CanonicalTextSize = if (isElision) C_ElementInElision else canonicalTextSize
    }
  }


  /****************************************************************************/
  private fun handleVerseSid (node: Node, id: String): VerseDescriptor
  {
    /**************************************************************************/
    //Dbg.d(node)
    val idAsRef = m_FileProtocol!!.readRefCollection(id)
    val refKeys = idAsRef.getAllAsRefKeys()
    var res: VerseDescriptor? = null



    /**************************************************************************/
    /* If this is an elision, create an elision entry for each verse. */

    if (refKeys.size > 1 || NodeMarker.hasElisionType(node))
      refKeys.forEach { m_Text.m_ElisionDetails[it] = node }



    /**************************************************************************/
    /* Create a verse entry for each verse. */

    refKeys.forEach { res = addVerse(it) }



    /**************************************************************************/
    /* If this is a subverse, create a subverse details entry, or add to the
       existing one.  (I assume that we never have subverse elisions, so I
       will have only one refKey to look at.) */

    val subverseNo = Ref.getS(refKeys[0])
    if (RefBase.C_DummyElement != subverseNo)
    {
      if (refKeys.size > 1) throw StepException("Can't handle subverse range: $idAsRef")
      val verseRefKey = Ref.clearS(refKeys[0])
      var thunk = m_Text.m_SubverseDetails[verseRefKey]
      if (null == thunk)
      {
        thunk = Limits()
        m_Text.m_SubverseDetails[verseRefKey] = thunk
        if (verseExistsWithoutSubverses(verseRefKey)) thunk.m_LowIx = 0
      }

      if (subverseNo < thunk.m_LowIx) thunk.m_LowIx = subverseNo
    }



    /**************************************************************************/
    return res!!
  }


  /****************************************************************************/
  // $$$ Need to consider subverses and canonical titles.

  fun debugDisplayStructure ()
  {
    /**************************************************************************/
    var doneSomething = false



    /**************************************************************************/
    m_Text.m_Content.m_ContentMap.keys.forEach { book ->
      val bookName = BibleBookNamesUsx.numberToAbbreviatedName(book)
      m_Text.m_Content.m_ContentMap[book]!!.m_Content.m_ContentMap.keys.forEach {chapter ->
        var verseDetails = ""
        m_Text.m_Content.m_ContentMap[book]!!.m_Content.m_ContentMap[chapter]!!.m_Content.m_ContentMap.keys.forEach { verse ->
          val refKey = Ref.rd(book, chapter, (verse / RefBase.C_Multiplier).toInt(), (verse % RefBase.C_Multiplier).toInt()).toRefKey()
          val elisionMarker = if (refKey in m_Text.m_ElisionDetails.keys) "*" else ""
          val descriptor = m_Text.m_Content.m_ContentMap[book]!!.m_Content.m_ContentMap[chapter]!!.m_Content.m_ContentMap[verse]
          val wc = if (elisionMarker.isNotEmpty() || 0 == descriptor!!.m_CanonicalTextSize) "" else "(${descriptor!!.m_CanonicalTextSize})"
          verseDetails += "${Ref.rd(verse + 0L).toString("vs")}$elisionMarker$wc, "
        }

        verseDetails = verseDetails.substring(0, verseDetails.length - 2)
        val text = if ('*' in verseDetails || '(' in verseDetails)
          "$bookName $chapter:$verseDetails"
        else
        {
          val rc = RefCollection.rdUsx("$bookName $chapter:$verseDetails")
          rc.simplify()
          val fullDetails = rc.toString()
          val prefix = fullDetails.split(':')[0] + ":"
          prefix + fullDetails.replace(prefix, "") // Remove repeats of bookname etc.  We have to do this manually here, because the processing assumes we always want full refs for USX (which normally we do).
        }

        if (!doneSomething)
        {
          Dbg.d("")
          Dbg.d("One row per chapter, in condensed form unless any verses are elided, or we have verse word counts.")
          Dbg.d("Elided verses are marked with an asterisk.  Word counts are in parentheses.")
          Dbg.d("")
        }

        doneSomething = true
        Dbg.d(text)
      }
    }



    /**************************************************************************/
    if (!doneSomething)
      Dbg.d("*** No data found ***")
  }


  /****************************************************************************/
  private fun makeElts (ref: Ref): IntArray = ref.getCopyOfElements()
  private fun makeElts (refKey: RefKey) = Ref.rd(refKey).getCopyOfElements()
  private fun makeElts (b:Int, c: Int = 0, v: Int = 0, s: Int = 0) = listOf(b, c, v, s).toIntArray()
  private fun makeElts (refAsString: String) = m_FileProtocol!!.readRef(refAsString).getCopyOfElements()
  private fun makeRef_bcv  (elts: IntArray) = Ref.rd(elts).toRefKey_bcv()
  private fun makeRef_vs   (elts: IntArray) = Ref.rd(elts).toRefKey_vs()
  protected val m_FileProtocol = fileProtocol
} // BibleStructure





/******************************************************************************/
/**
* osis2mod scheme for any selected scheme.
*/

open class BibleStructureOsis2ModScheme (scheme: String): BibleStructure(null)
{
  /****************************************************************************/
  private val m_Scheme: String


  /****************************************************************************/
  override fun addFromDoc (prompt: String, doc: Document, wantCanonicalTextSize: Boolean, filePath: String?, bookName: String?) { throw StepException("Can't populate osis2mod scheme from Document.") }
  override fun commonGetCanonicalTextSize (elts: IntArray): Int { throw StepException("Can't ask for word count on an osis2mod scheme, because the schemes are abstract and have no text.") }
  override fun commonGetCanonicalTextSizeForCanonicalTitle (elts: IntArray): Int { throw StepException("Can't ask for word count on an osis2mod scheme, because the schemes are abstract and have no text.") }
  override fun getRelevanceOfNode (node: Node): NodeRelevance { throw StepException("getRelevanceOfNode should not be being called on an osis2mod scheme.") }
  override fun addFromDoc (doc: Document, wantCanonicalTextSize: Boolean) { throw StepException("load should not be being called on an osis2mod scheme.") }


  /****************************************************************************/
  /* A line in the data looks like    Calvin/Rut/22, 23, 18, 22   */

  private fun parseData ()
  {
    /**************************************************************************/
    fun processLine (line: String, retain: Boolean = false)
    {
      val (schemeName, bookAbbreviation, verseCountDetails) = line.split('/')
      val bookNo = BibleBookNamesUsx.nameToNumber(bookAbbreviation.trim())
      val verseCounts = verseCountDetails.replace("\\s+".toRegex(), "").split(',')
      for (chapterIx in verseCounts.indices)
      {
        val count = Integer.parseInt(verseCounts[chapterIx])
        for (verseIx in 1 .. count)
          addVerse(Ref.rd(bookNo, chapterIx + 1, verseIx).toRefKey())
      }
    }



    /**************************************************************************/
    val selector = "$m_Scheme/"
    FileLocations.getInputStream(FileLocations.getOsis2modVersificationDetailsFilePath())!!.bufferedReader().use { it.readText() } .lines()
      .map { it.trim() }
      .filter { it.startsWith(selector) } // Limit to the lines for this text.
      .forEach { processLine(it) }
  }


  /****************************************************************************/
  init {
    m_Scheme = VersificationSchemesSupportedByOsis2mod.canonicaliseSchemeName(scheme)
    parseData()
  }
}





/******************************************************************************/
/**
* BibleStructure derived from IMP format.
*/

open class BibleStructureImp (filePath: String): BibleStructure(null)
{
  /****************************************************************************/
  override fun addFromDoc (prompt: String, doc: Document, wantCanonicalTextSize: Boolean, filePath: String?, bookName: String?) { throw StepException("Can't populate osis2mod scheme from Document.") }
  override fun commonGetCanonicalTextSize (elts: IntArray): Int { throw StepException("Not yet set up to provide word counts on an IMP file.") }
  override fun commonGetCanonicalTextSizeForCanonicalTitle (elts: IntArray): Int { throw StepException("Not yet set up to provide word counts on an IMP file.") }
  override fun getRelevanceOfNode (node: Node): NodeRelevance { throw StepException("getRelevanceOfNode should not be being called on an IMP file.") }
  override fun addFromDoc (doc: Document, wantCanonicalTextSize: Boolean) { throw StepException("load should not be being called on an IMP file.") }


  /****************************************************************************/
  /* We are interested only in verse lines, which look like eg Genesis 1:1. */

  private fun processLine (theLine: String)
  {
    var line = theLine.trim()
    if (!line.startsWith("$$$")) return
    if ("[" in line) return

    line = line.substring(3)
    var bits = line.split(" ")
    val bookNo = BibleBookNamesUsx.nameToNumber(bits[0].trim())
    bits = bits[1].trim().split(":")
    val chapterNo = bits[0].trim().toInt()
    val verseNo = bits[1].trim().toInt()
    addVerse(Ref.rd(bookNo, chapterNo, verseNo).toRefKey())
  }


  /****************************************************************************/
  init {
    File(filePath).bufferedReader().lines().forEach { processLine(it) }
  }
}
