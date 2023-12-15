package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.ref.RefRange
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.ArrayList
import kotlin.math.abs


/******************************************************************************/
/**
 * Base class for details of chapter / verse structure of Bibles.
 *
 * Via derived classes, you can use this to hold the structure of a populated
 * USX or OSIS text; or you can use it to hold the structure of one of the
 * standard osis2mod schemes.
 *
 * With the USX and OSIS variants, you can also ask the processing to determine
 * the count of the number of words in the canonical content of each verse or
 * subverse.  Word counts are not available for osis2mod schemes, because those
 * schemes are determined a priori without reference to any text.
 *
 * In order to populate the USX and OSIS flavours, we have to parse the
 * relevant text files, to which end I assume that the milestone versions
 * of the verse tags has been used.  (Strictly it might be possible to process
 * the enclosing-tag version instead, so long as word counts are not
 * required, and possibly even if they are -- I kind of make provision for it
 * below, but I have to admit I'm not entirely sure how easy it will be.)
 *
 *
 *
 *
 *
 * ## Obtaining instances
 *
 * You may, if you wish, obtain instances of the various classes directly here,
 * simply by instantiating them for yourself.  However, experience suggests that
 * there are particular things which you are almost certain to want as
 * singletons.  Accordingly there are method below to provide for this --
 * look at the various methods with 'Instance' in their names in the companion
 * objects.
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
 * numbering scheme (eg it runs 1, 2, 5, 6), not that there are verses
 * missing at the *end* of the chapter, because in general I don't know how many
 * verses there *should* be in a chapter.
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
 * @author ARA "Jamie" Jamieson
*/

abstract class BibleStructure
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
     * Acts as a singleton for an OSIS structure.
     *
     * @return Instance.
     */

    fun OsisInstance (): BibleStructureOsis
    {
      if (null == m_InstanceOsis)
        m_InstanceOsis = BibleStructureOsis()
      return m_InstanceOsis!!
    }

    private var m_InstanceOsis: BibleStructureOsis? = null


    /**************************************************************************/
    /**
     * Acts as a singleton for a USX structure.  The intention here is that this
     * might be used to retain information about the raw USX (separate from
     * information about the USX under construction), but whether or how you
     * use it is up to you.
     *
     * @return Instance.
     */

    fun UsxRawInstance (): BibleStructureUsx
    {
      if (null == m_InstanceUsxRaw)
        m_InstanceUsxRaw = BibleStructureUsx()
        return m_InstanceUsxRaw!!
    }

    private var m_InstanceUsxRaw: BibleStructureUsx?  = null


    /**************************************************************************/
    /**
     * Acts as a singleton a USX structure.  The intention here is that this
     * might be used to retain information about the USX under construction
     * (separate from information about the raw USX), but whether or how you
     * use it is up to you.
     *
     * @return Instance.
     */

    fun UsxUnderConstructionInstance (): BibleStructureUsx
    {
      if (null == m_InstanceUsxUnderConstruction)
        m_InstanceUsxUnderConstruction = BibleStructureUsx()
      return m_InstanceUsxUnderConstruction!!
    }

    private var m_InstanceUsxUnderConstruction: BibleStructureUsx?  = null


    /**************************************************************************/
    /**
     * Creates and or returns an instance of BibleStructureOsis2ModScheme which
     * contains either NRSV or NRSVA data, according to whether or not the text
     * under construction contains the DC.  (Cannot be called until the
     * BibleStructure details for the text under construction have been
     * established.)
     *
     * @return Instance.
     */

    fun NrsvxInstance (): BibleStructureOsis2ModScheme
    {
      if (null == m_BibleStructureNrsvxInstance)
        m_BibleStructureNrsvxInstance = Osis2modSchemeInstance(if (m_InstanceUsxUnderConstruction!!.hasAnyBooksDc()) "nrsva" else "nrsv", true)
      return m_BibleStructureNrsvxInstance!!
    }

    private var m_BibleStructureNrsvxInstance : BibleStructureOsis2ModScheme? = null


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
    * @param retain True if you want to retain the data rather than rebuild it if
    *               it is called for again.
    * @return Instance.
    */

    fun Osis2modSchemeInstance (schemeName: String, retain: Boolean): BibleStructureOsis2ModScheme
    {
      val canonicalSchemeName = BibleStructuresSupportedByOsis2mod.canonicaliseSchemeName(schemeName)
      var res = m_RetainedOsis2modSchemes[canonicalSchemeName]
      if (null == res)
      {
        res = BibleStructureOsis2ModScheme(canonicalSchemeName)
        if (retain) m_RetainedOsis2modSchemes[canonicalSchemeName] = res
      }

      return res
    }

    private val m_RetainedOsis2modSchemes: MutableMap<String, BibleStructureOsis2ModScheme> = mutableMapOf()


    /****************************************************************************/
    /** Returned when you compare the text under construction with another
     *  scheme.  Hopefully the purposes of the fields are obvious.  All are
     *  returned as sorted lists of RefKeys. */

    data class ComparisonWithOtherScheme (val chaptersInTextUnderConstructionButNotInTargetScheme: List<RefKey>,
                                          val chaptersInTargetSchemeButNotInTextUnderConstruction: List<RefKey>,
                                          val chaptersInBoth: List<RefKey>,
                                          val versesInTextUnderConstructionButNotInTargetScheme: List<RefKey>,
                                          val versesInTargetSchemeButNotInTextUnderConstruction: List<RefKey>,
                                          val versesInBoth: List<RefKey>)


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
      val versesInOtherScheme = schemeB.getAllRefKeysForBook(bookNumber).toSet()
      val versesInTextUnderConstructionButNotInOtherScheme = versesInTextUnderConstruction subtract versesInOtherScheme
      val versesInOtherSchemeButNotInTextUnderConstruction = versesInOtherScheme subtract versesInTextUnderConstruction
      val versesInBoth = versesInTextUnderConstruction intersect versesInOtherScheme

      val chaptersInTextUnderConstruction = versesInTextUnderConstruction.map { Ref.clearV(Ref.clearS(it)) }.toSet()
      val chaptersInOtherScheme = versesInOtherScheme.map { Ref.clearV(Ref.clearS(it)) }.toSet()
      val chaptersInTextUnderConstructionButNotInOtherScheme = chaptersInTextUnderConstruction subtract chaptersInOtherScheme
      val chaptersInOtherSchemeButNotInTextUnderConstruction = chaptersInOtherScheme subtract chaptersInTextUnderConstruction
      val chaptersInBoth = chaptersInTextUnderConstructionButNotInOtherScheme intersect chaptersInOtherScheme
      return ComparisonWithOtherScheme(chaptersInTextUnderConstructionButNotInOtherScheme.sorted(), chaptersInOtherSchemeButNotInTextUnderConstruction.sorted(), chaptersInBoth.sorted(),
                                       versesInTextUnderConstructionButNotInOtherScheme.sorted(), versesInOtherSchemeButNotInTextUnderConstruction.sorted(), versesInBoth.sorted())
    } // compareWithGivenScheme
  } // companion object





  /****************************************************************************/
  /**
  * Adds the data from a given file to the current data structures.  The method
  * is marked 'open' mainly so that inheriting classes (and in particular,
  * BibleStructureOsis2ModScheme) can ensure that it doesn't get called.
  *
  * @param doc
  * @param wantWordCount True if we need to accumulate the word count.
  * @param filePath: Optional: used for debugging and progress reporting only.
  * @param bookName USX abbreviation.
  */

  open fun addFromDom (doc: Document, wantWordCount: Boolean, filePath: String? = null, bookName: String? = null)
  {
    if (null != bookName) Dbg.reportProgress("  Determining Bible structure for $bookName")
    m_CollectingWordCounts = wantWordCount
    if (null != bookName && null != filePath) m_BookAbbreviationToFilePathMappings[bookName.lowercase()] = filePath
    preprocess(doc)
    load(doc, wantWordCount)
    postprocess(doc)
  }


  /****************************************************************************/
  /**
  * Adds the data from a given file to the current data structures.
  *
  * @param filePath
  * @param wantWordCount True if we need to accumulate the word count.
  * @param bookName USX abbreviation.
 */

  fun addFromFile (filePath: String, wantWordCount: Boolean, bookName: String? = null)
  {
    addFromDom(Dom.getDocument(filePath, false), wantWordCount, filePath, bookName)
  }


  /****************************************************************************/
  /**
  * USE WITH CAUTION: Indicates that the instance has been populated with
  * _something_.  It does not follow that it is necessarily up to date, nor
  * that it has been populated with anything more than a single book.
  *
  * @return True if already populated.
  */

  fun alreadyPopulated (): Boolean
  {
    return m_Text.m_Content.m_ContentMap.isNotEmpty()
  }


  /****************************************************************************/
  /**
  * Returns the path to the file which contains a given book, or null if not
  * found.
  *
  * @param bookAbbreviation Abbreviated name of book of interest.
  * @return File path.
  */

  fun getFilePathForBook (bookAbbreviation: String): String?
  {
    return m_BookAbbreviationToFilePathMappings[bookAbbreviation.lowercase()]
  }


  /****************************************************************************/
  /**
  * Populates the data structures by running over the items identified by the
  * mapper.
  *
  * @param mapper
  * @param wantWordCount
  */

  fun populateFromBookAndFileMapper (mapper: BibleBookAndFileMapper, wantWordCount: Boolean = false)
  {
    clear()
    mapper.iterateOverSelectedFiles{ bookName: String, filePath: String -> addFromFile(filePath, wantWordCount, bookName) }
  }


  /****************************************************************************/
  /**
  * Clears the data structures and adds the data from a given file.
  *
  * @param doc
  * @param wantWordCount True if we need to accumulate the word count.
  * @param filePath: Optional: used for debugging and progress reporting only.
  * @param bookName USX abbreviation.
  */

  fun populateFromDom (doc: Document, wantWordCount: Boolean, filePath: String? = null, bookName: String? = null)
  {
    clear()
    addFromDom(doc, wantWordCount, bookName)
  }


  /****************************************************************************/
  /**
  * Clears the data structures and adds the data from a given file.
  *
  * @param filePath
  * @param wantWordCount True if we need to accumulate the word count.
  * @param bookName USX abbreviation.
  */

  fun populateFromFile (filePath: String, wantWordCount: Boolean, bookName: String? = null)
  {
    clear()
    addFromFile(filePath, wantWordCount, bookName)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Extract information                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  open fun getAllBookNumbersOt (): List<Int> { return m_Text.m_Content.m_ContentMap.keys.filter { BibleAnatomy.isOt(it) } }
  open fun getAllBookNumbersNt (): List<Int> { return m_Text.m_Content.m_ContentMap.keys.filter { BibleAnatomy.isNt(it) } }
  open fun getAllBookNumbersDc (): List<Int> { return m_Text.m_Content.m_ContentMap.keys.filter { BibleAnatomy.isDc(it) } }



  open fun hasAllBooksOt (): Boolean { return BibleAnatomy.getNumberOfBooksInOt() == getAllBookNumbersOt().size }
  open fun hasAllBooksNt (): Boolean { return BibleAnatomy.getNumberOfBooksInOt() == getAllBookNumbersNt().size }
  open fun hasAllBooksDc (): Boolean { return BibleAnatomy.getNumberOfBooksInOt() == getAllBookNumbersDc().size }



  open fun hasAnyBooksOt (): Boolean { return null != m_Text.m_Content.m_ContentMap.keys.firstOrNull { BibleAnatomy.isOt(it) } }
  open fun hasAnyBooksNt (): Boolean { return null != m_Text.m_Content.m_ContentMap.keys.firstOrNull { BibleAnatomy.isNt(it) } }
  open fun hasAnyBooksDc (): Boolean { return null != m_Text.m_Content.m_ContentMap.keys.firstOrNull { BibleAnatomy.isDc(it) } }



  fun bookExists (bookRef: Ref)                              : Boolean { return commonBookExists(makeElts(bookRef)) }
  fun bookExists (bookRefKey: RefKey)                        : Boolean { return commonBookExists(makeElts(bookRefKey)) }
  fun bookExists (b: Int, c: Int = 0, v: Int = 0, s: Int = 0): Boolean { return commonBookExists(makeElts(b, 0, 0, 0)) }
  fun bookExists (bookRefAsString: String)                   : Boolean { return commonBookExists(makeElts(bookRefAsString)) }
  fun bookExists (elts: IntArray)                            : Boolean { return commonBookExists(elts) }



  fun chapterExists (chapterRef: Ref)                       : Boolean { return commonChapterExists(makeElts(chapterRef)) }
  fun chapterExists (chapterRefKey: RefKey)                 : Boolean { return commonChapterExists(makeElts(chapterRefKey)) }
  fun chapterExists (b: Int, c: Int, v: Int = 0, s: Int = 0): Boolean { return commonChapterExists(makeElts(b, c, 0, 0)) }
  fun chapterExists (chapterRefAsString: String)            : Boolean { return commonChapterExists(makeElts(chapterRefAsString)) }
  fun chapterExists (elts: IntArray)                        : Boolean { return commonChapterExists(elts) }



  fun verseExistsWithoutSubverses (verseOrSubverseRef: Ref)           : Boolean { return commonVerseExistsWithoutSubverses(makeElts(verseOrSubverseRef)) }
  fun verseExistsWithoutSubverses (verseOrSubverseRefKey: RefKey)     : Boolean { return commonVerseExistsWithoutSubverses(makeElts(verseOrSubverseRefKey)) }
  fun verseExistsWithoutSubverses (b: Int, c: Int, v: Int, s: Int = 0): Boolean { return commonVerseExistsWithoutSubverses(makeElts(b, c, v, s)) }
  fun verseExistsWithoutSubverses (verseOrSubverseRefAsString: String): Boolean { return commonVerseExistsWithoutSubverses(makeElts(verseOrSubverseRefAsString)) }
  fun verseExistsWithoutSubverses (elts: IntArray)                    : Boolean { return commonVerseExistsWithoutSubverses(elts) }


  fun verseExistsWithOrWithoutSubverses (verseOrSubverseRef: Ref)           : Boolean { return commonVerseExistsWithOrWithoutSubverses(makeElts(verseOrSubverseRef)) }
  fun verseExistsWithOrWithoutSubverses (verseOrSubverseRefKey: RefKey)     : Boolean { return commonVerseExistsWithOrWithoutSubverses(makeElts(verseOrSubverseRefKey)) }
  fun verseExistsWithOrWithoutSubverses (b: Int, c: Int, v: Int, s: Int = 0): Boolean { return commonVerseExistsWithOrWithoutSubverses(makeElts(b, c, v, s)) }
  fun verseExistsWithOrWithoutSubverses (verseOrSubverseRefAsString: String): Boolean { return commonVerseExistsWithOrWithoutSubverses(makeElts(verseOrSubverseRefAsString)) }
  fun verseExistsWithOrWithoutSubverses (elts: IntArray)                    : Boolean { return commonVerseExistsWithOrWithoutSubverses(elts) }



  fun verseOrSubverseExistsAsSpecified (verseOrSubverseRef: Ref)           : Boolean { return commonVerseOrSubverseExistsAsSpecified(makeElts(verseOrSubverseRef)) }
  fun verseOrSubverseExistsAsSpecified (verseOrSubverseRefKey: RefKey)     : Boolean { return commonVerseOrSubverseExistsAsSpecified(makeElts(verseOrSubverseRefKey)) }
  fun verseOrSubverseExistsAsSpecified (b: Int, c: Int, v: Int, s: Int = 0): Boolean { return commonVerseOrSubverseExistsAsSpecified(makeElts(b, c, v, s)) }
  fun verseOrSubverseExistsAsSpecified (verseOrSubverseRefAsString: String): Boolean { return commonVerseOrSubverseExistsAsSpecified(makeElts(verseOrSubverseRefAsString)) }
  fun verseOrSubverseExistsAsSpecified (elts: IntArray)                    : Boolean { return commonVerseOrSubverseExistsAsSpecified(elts) }



  fun thingExists (chapterRef: Ref)                       : Boolean { return commonThingExists(makeElts(chapterRef)) }
  fun thingExists (chapterRefKey: RefKey)                 : Boolean { return commonThingExists(makeElts(chapterRefKey)) }
  fun thingExists (b: Int, c: Int, v: Int = 0, s: Int = 0): Boolean { return commonThingExists(makeElts(b, c, 0, 0)) }
  fun thingExists (chapterRefAsString: String)            : Boolean { return commonThingExists(makeElts(chapterRefAsString)) }
  fun thingExists (elts: IntArray)                        : Boolean { return commonThingExists(elts) }



  fun getLastChapterNo (bookRef: Ref)                              : Int { return commonGetLastChapterNo(makeElts(bookRef)) }
  fun getLastChapterNo (bookRefKey: RefKey)                        : Int { return commonGetLastChapterNo(makeElts(bookRefKey)) }
  fun getLastChapterNo (b: Int, c: Int = 0, v: Int = 0, s: Int = 0): Int { return commonGetLastChapterNo(makeElts(b, 0, 0, 0)) }
  fun getLastChapterNo (bookRefAsString: String)                   : Int { return commonGetLastChapterNo(makeElts(bookRefAsString)) }
  fun getLastChapterNo (elts: IntArray)                            : Int { return commonGetLastChapterNo(elts) }



  fun getLastVerseNo (chapterRef: Ref)                       : Int { return commonGetLastVerseNo(makeElts(chapterRef)) }
  fun getLastVerseNo (chapterRefKey: RefKey)                 : Int { return commonGetLastVerseNo(makeElts(chapterRefKey)) }
  fun getLastVerseNo (b: Int, c: Int, v: Int = 0, s: Int = 0): Int { return commonGetLastVerseNo(makeElts(b, c, 0, 0)) }
  fun getLastVerseNo (chapterRefAsString: String)            : Int { return commonGetLastVerseNo(makeElts(chapterRefAsString)) }
  fun getLastVerseNo (elts: IntArray)                        : Int { return commonGetLastVerseNo(elts) }



  fun getWordCount (verseOrSubverseRef: Ref)           : Int { return commonGetWordCount(makeElts(verseOrSubverseRef)) }
  fun getWordCount (verseOrSubverseRefKey: RefKey)     : Int { return commonGetWordCount(makeElts(verseOrSubverseRefKey)) }
  fun getWordCount (b: Int, c: Int, v: Int, s: Int = 0): Int { return commonGetWordCount(makeElts(b, c, v, s)) }
  fun getWordCount (verseOrSubverseRefAsString: String): Int { return commonGetWordCount(makeElts(verseOrSubverseRefAsString)) }
  fun getWordCount (elts: IntArray)                    : Int { return commonGetWordCount(elts) }



  fun getWordCountForCanonicalTitle (verseOrSubverseRef: Ref)           : Int { return commonGetWordCountForCanonicalTitle(makeElts(verseOrSubverseRef)) }
  fun getWordCountForCanonicalTitle (verseOrSubverseRefKey: RefKey)     : Int { return commonGetWordCountForCanonicalTitle(makeElts(verseOrSubverseRefKey)) }
  fun getWordCountForCanonicalTitle (b: Int, c: Int, v: Int, s: Int = 0): Int { return commonGetWordCountForCanonicalTitle(makeElts(b, c, v, s)) }
  fun getWordCountForCanonicalTitle (verseOrSubverseRefAsString: String): Int { return commonGetWordCountForCanonicalTitle(makeElts(verseOrSubverseRefAsString)) }
  fun getWordCountForCanonicalTitle (elts: IntArray)                    : Int { return commonGetWordCountForCanonicalTitle(elts) }



  fun getAllRefKeys (): List<RefKey> { return m_Text.m_Content.m_ContentMap.map { (bookNo, bd) -> getAllAsRefKeys(bookNo, bd) }.flatten() }

  fun getAllRefKeysForBook (bookRef: Ref)                              : List<RefKey> { return commonGetAllRefKeysForBook(makeElts(bookRef)) }
  fun getAllRefKeysForBook (bookRefKey: RefKey)                        : List<RefKey> { return commonGetAllRefKeysForBook(makeElts(bookRefKey)) }
  fun getAllRefKeysForBook (b: Int, c: Int = 0, v: Int = 0, s: Int = 0): List<RefKey> { return commonGetAllRefKeysForBook(makeElts(b, 0, 0, 0)) }
  fun getAllRefKeysForBook (bookRefAsString: String)                   : List<RefKey> { return commonGetAllRefKeysForBook(makeElts(bookRefAsString)) }
  fun getAllRefKeysForBook (elts: IntArray)                            : List<RefKey> { return commonGetAllRefKeysForBook(elts) }



  fun getAllRefKeysForChapter (chapterRef: Ref)                       : List<RefKey> { return commonGetAllRefKeysForChapter(makeElts(chapterRef)) }
  fun getAllRefKeysForChapter (chapterRefKey: RefKey)                 : List<RefKey> { return commonGetAllRefKeysForChapter(makeElts(chapterRefKey)) }
  fun getAllRefKeysForChapter (b: Int, c: Int, v: Int = 0, s: Int = 0): List<RefKey> { return commonGetAllRefKeysForChapter(makeElts(b, c, 0, 0)) }
  fun getAllRefKeysForChapter (chapterRefAsString: String)            : List<RefKey> { return commonGetAllRefKeysForChapter(makeElts(chapterRefAsString)) }
  fun getAllRefKeysForChapter (elts: IntArray)                        : List<RefKey> { return commonGetAllRefKeysForChapter(elts) }



  fun hasCanonicalTitle (chapterRef: Ref)                       : Boolean { return commonHasCanonicalTitle(makeElts(chapterRef)) }
  fun hasCanonicalTitle (chapterRefKey: RefKey)                 : Boolean { return commonHasCanonicalTitle(makeElts(chapterRefKey)) }
  fun hasCanonicalTitle (b: Int, c: Int, v: Int = 0, s: Int = 0): Boolean { return commonHasCanonicalTitle(makeElts(b, c, 0, 0)) }
  fun hasCanonicalTitle (chapterRefAsString: String)            : Boolean { return commonHasCanonicalTitle(makeElts(chapterRefAsString)) }
  fun hasCanonicalTitle (elts: IntArray)                        : Boolean { return commonHasCanonicalTitle(elts) }




  /****************************************************************************/
  /* Errors, issues and oddities. */

  open fun getDuplicateVersesForText (): List<RefKey> { return m_Text.m_DuplicateVerses }



  fun getMissingEmbeddedChaptersForText (): List<RefKey> { return aggregateOverBooksInText(::getMissingEmbeddedChaptersForBook) }

  fun getMissingEmbeddedChaptersForBook (bookRef: Ref)                              : List<RefKey> { return commonGetMissingEmbeddedChaptersForBook(makeElts(bookRef)) }
  fun getMissingEmbeddedChaptersForBook (bookRefKey: RefKey)                        : List<RefKey> { return commonGetMissingEmbeddedChaptersForBook(makeElts(bookRefKey)) }
  fun getMissingEmbeddedChaptersForBook (b: Int, c: Int = 0, v: Int = 0, s: Int = 0): List<RefKey> { return commonGetMissingEmbeddedChaptersForBook(makeElts(b, 0, 0, 0))}
  fun getMissingEmbeddedChaptersForBook (bookRefAsString: String)                   : List<RefKey> { return commonGetMissingEmbeddedChaptersForBook(makeElts(bookRefAsString)) }
  fun getMissingEmbeddedChaptersForBook (elts: IntArray)                            : List<RefKey> { return commonGetMissingEmbeddedChaptersForBook(elts) }



  fun getMissingEmbeddedVersesForText (): List<RefKey> { return aggregateOverBooksInText(::getMissingEmbeddedVersesForBook) }

  fun getMissingEmbeddedVersesForBook (bookRef: Ref)                              : List<RefKey> { return commonGetMissingEmbeddedVersesForBook(makeElts(bookRef)) }
  fun getMissingEmbeddedVersesForBook (bookRefKey: RefKey)                        : List<RefKey> { return commonGetMissingEmbeddedVersesForBook(makeElts(bookRefKey)) }
  fun getMissingEmbeddedVersesForBook (b: Int, c: Int = 0, v: Int = 0, s: Int = 0): List<RefKey> { return commonGetMissingEmbeddedVersesForBook(makeElts(b, c, 0, 0))}
  fun getMissingEmbeddedVersesForBook (bookRefAsString: String)                   : List<RefKey> { return commonGetMissingEmbeddedVersesForBook(makeElts(bookRefAsString)) }
  fun getMissingEmbeddedVersesForBook (elts: IntArray)                            : List<RefKey> { return commonGetMissingEmbeddedVersesForBook(elts) }


  fun getMissingEmbeddedVersesForChapter (chapterRef: Ref)                       : List<RefKey> { return commonGetMissingEmbeddedVersesForChapter(makeElts(chapterRef)) }
  fun getMissingEmbeddedVersesForChapter (chapterRefKey: RefKey)                 : List<RefKey> { return commonGetMissingEmbeddedVersesForChapter(makeElts(chapterRefKey)) }
  fun getMissingEmbeddedVersesForChapter (b: Int, c: Int, v: Int = 0, s: Int = 0): List<RefKey> { return commonGetMissingEmbeddedVersesForChapter(makeElts(b, c, 0, 0))}
  fun getMissingEmbeddedVersesForChapter (chapterRefAsString: String)            : List<RefKey> { return commonGetMissingEmbeddedVersesForChapter(makeElts(chapterRefAsString)) }
  fun getMissingEmbeddedVersesForChapter (elts: IntArray)                        : List<RefKey> { return commonGetMissingEmbeddedVersesForChapter(elts) }



  open fun otBooksAreInOrder (): Boolean { return MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllBookNumbersOt()) }
  open fun ntBooksAreInOrder (): Boolean { return MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllBookNumbersNt()) }
  open fun dcBooksAreInOrder (): Boolean { return MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllBookNumbersDc()) }



  fun chaptersAreInOrder (bookRef: Ref)                              : Boolean { return commonChaptersAreInOrder(makeElts(bookRef)) }
  fun chaptersAreInOrder (bookRefKey: RefKey)                        : Boolean { return commonChaptersAreInOrder(makeElts(bookRefKey)) }
  fun chaptersAreInOrder (b: Int, c: Int = 0, v: Int = 0, s: Int = 0): Boolean { return commonChaptersAreInOrder(makeElts(b, 0, 0, 0)) }
  fun chaptersAreInOrder (bookRefAsString: String)                   : Boolean { return commonChaptersAreInOrder(makeElts(bookRefAsString)) }
  fun chaptersAreInOrder (elts: IntArray)                            : Boolean { return commonChaptersAreInOrder(elts) }



  fun versesAreInOrder () : Boolean
  {
    var v = getAllBookNumbersOt().map { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }. firstOrNull { !it }
    if (null != v) return false

    v = getAllBookNumbersNt().map { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }. firstOrNull { !it }
    if (null != v) return false

    v = getAllBookNumbersDc().map { MiscellaneousUtils.checkInStrictlyAscendingOrder(getAllRefKeysForBook(it)) }. firstOrNull { !it }
    if (null != v) return false

    return true
  }


  fun versesAreInOrder (chapterRef: Ref)                       : Boolean { return commonVersesAreInOrder(makeElts(chapterRef)) }
  fun versesAreInOrder (chapterRefKey: RefKey)                 : Boolean { return commonVersesAreInOrder(makeElts(chapterRefKey)) }
  fun versesAreInOrder (b: Int, c: Int, v: Int = 0, s: Int = 0): Boolean { return commonVersesAreInOrder(makeElts(b, c, 0, 0)) }
  fun versesAreInOrder (chapterRefAsString: String)            : Boolean { return commonVersesAreInOrder(makeElts(chapterRefAsString)) }
  fun versesAreInOrder (elts: IntArray)                        : Boolean { return commonVersesAreInOrder(elts) }



  fun allChaptersArePresent (bookRef: Ref)                             : Boolean { return commonAllChaptersArePresent(makeElts(bookRef)) }
  fun allChaptersArePresent (bookRefKey: RefKey)                       : Boolean { return commonAllChaptersArePresent(makeElts(bookRefKey)) }
  fun allChaptersArePresent (b: Int, c: Int = 0, v: Int =0, s: Int = 0): Boolean { return commonAllChaptersArePresent(makeElts(b, 0, 0, 0)) }
  fun allChaptersArePresent (bookRefAsString: String)                  : Boolean { return commonAllChaptersArePresent(makeElts(bookRefAsString)) }
  fun allChaptersArePresent (elts: IntArray)                           : Boolean { return commonAllChaptersArePresent(elts) }




  fun allVersesArePresent (chapterRef: Ref)                       : Boolean { return commonAllVersesArePresent(makeElts(chapterRef)) }
  fun allVersesArePresent (chapterRefKey: RefKey)                 : Boolean { return commonAllVersesArePresent(makeElts(chapterRefKey)) }
  fun allVersesArePresent (b: Int, c: Int, v: Int = 0, s: Int = 0): Boolean { return commonAllVersesArePresent(makeElts(b, c, 0, 0)) }
  fun allVersesArePresent (chapterRefAsString: String)            : Boolean { return commonAllVersesArePresent(makeElts(chapterRefAsString)) }
  fun allVersesArePresent (elts: IntArray)                        : Boolean { return commonAllVersesArePresent(elts) }



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

    if (res.isEmpty() || (-1 == ixLow && -1 == ixHigh && chapterNoLow == chapterNoHigh))
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
    return MiscellaneousUtils.checkInStrictlyAscendingOrder(getBookDescriptor(elts)!!.m_Content.m_ContentMap.keys.toList())
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
    val verses = cd.m_Content.m_ContentMap.keys.map { Ref.rd(it.toLong()).getV() }

    val baseRef = Ref.rd(elts).toRefKey_bcv()
    var prevVerse = 0
    var res: MutableList<RefKey> = mutableListOf()

    verses.forEach {
      for (i in prevVerse + 1 ..< it)
        res.add(Ref.setV(baseRef, i))
      prevVerse = it
    }

    return res
  }


  /****************************************************************************/
  protected open fun commonGetWordCount (elts: IntArray): Int
  {
    /**************************************************************************/
    if (!m_CollectingWordCounts)
      throw StepException("Word count for verse requested, but never asked to accumulate word counts.")



    /**************************************************************************/
    /* If we've been asked about a subverse, return the count for that subverse
       alone.  I assume subverses can never be part of an elision, so I don't
       worry about that here. */

    if (RefBase.C_DummyElement != elts[3]) // If asked for a subverse, return precisely that.
      return getVerseDescriptor(elts)!!.m_WordCount



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
      count += getVerseDescriptor(tempElts)!!.m_WordCount
    }

    return count
  }


  /****************************************************************************/
  protected open fun commonGetWordCountForCanonicalTitle (elts: IntArray): Int
  {
    return m_Text.m_CanonicalTitleDetails[Ref.rd(elts[0], elts[1], 0).toRefKey()]!!
  }

  /****************************************************************************/
  protected open fun commonHasCanonicalTitle (elts: IntArray) :Boolean
  {
    return Ref.rd(elts[0], elts[1], 0).toRefKey() in m_Text.m_CanonicalTitleDetails
  }


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
    return MiscellaneousUtils.checkInStrictlyAscendingOrder(getChapterDescriptor(elts)!!.m_Content.m_ContentMap.keys.toList())
  }


  /****************************************************************************/
  /**
  * Utility function for use by derived classes.  Takes either a single
  * reference or a reference range in non-standard format (I use USX as
  * standard here), and converts it to standard (USX) format.
  *
  * @param node Node containing the attribute to be handled.
  * @param attributeName Name of attribute containing reference details.
  * @return Reformatted value.
  */

  protected fun getStandardisedId (node: Node, attributeName: String): String
  {
    return m_RefRangeParser(node[attributeName]!!).toString()
  }


  /****************************************************************************/
  /**
  * Indicates whether a given node is non-canonical.  Need cater only for
  * node which can legitimately appear within a verse or a canonical title.
  *
  * @param node
  * @return True if node is non-canonical
  */

  protected abstract fun isNonCanonical (node: Node): Boolean


  /****************************************************************************/
  /**
  * Loads the data structures from a given document, accumulating a word count
  * where requested and possible.  This outline structure should almost
  * certainly be fine for any XML-based representation (you'd simply need to
  * supply suitable implementations of isNonCanonical and getRelevacneOfNode),
  * but you may want to override it for other representations.
  *
  * @param doc Document from which data is taken.  (This data is added to any
  *   data already stored -- it does not *replace* it.)
  *
  * @param wantWordCount What it say on the tin.
  */

  protected open fun load (doc: Document, wantWordCount: Boolean)
  {
    /**************************************************************************/
    var canonicalTitleWordCount = 0
    var inCanonicalTitle = false
    var inVerse = false
    var isElision = false
    var verseWordCount = 0



    /**************************************************************************/
    fun textIsOfInterest (textNode: Node): Boolean
    {
      var parent = textNode
      while (true)
     {
        parent = parent.parentNode ?: break
        if (isNonCanonical(parent))
          return false
      }

      return true
    }



    /**************************************************************************/
    /* Decides whether text needs to be added to the word count. */

    fun processText (node: Node)
    {
      if (inCanonicalTitle && textIsOfInterest(node))
        canonicalTitleWordCount += StepStringUtils.wordCount(node.textContent)

      else if (inVerse && !isElision && textIsOfInterest(node))
        verseWordCount += StepStringUtils.wordCount(node.textContent)
    }



    /**************************************************************************/
    val allNodes = Dom.collectNodesInTree(doc)
    allNodes.forEach {
      var processNode = true
      while (processNode)
      {
        val relevance = getRelevanceOfNode(it)
        when (relevance.nodeType)
        {
          NodeRelevanceType.VerseStart ->
          {
            handleVerseSid(relevance.idAsString)
            isElision = "-" in relevance.idAsString; verseWordCount = 0; inVerse = true
          }


          NodeRelevanceType.VerseEnd ->
          {
            handleVerseEid(relevance.idAsString, verseWordCount)
            isElision = false; inVerse = false
          }


          NodeRelevanceType.CanonicalTitleStart ->
          {
            canonicalTitleWordCount = 0
            inCanonicalTitle = true
          }


          NodeRelevanceType.CanonicalTitleEnd ->
          {
            m_Text.m_CanonicalTitleDetails[Ref.rdUsx(relevance.idAsString).toRefKey()] = canonicalTitleWordCount
            inCanonicalTitle = false
          }


          NodeRelevanceType.Text ->
          {
            processText(it)
          }


          NodeRelevanceType.Boring ->
          {
          }
        } // when

        processNode = relevance.andProcessThisNode
      } // while (processNode)
    } // forEach
  } // load


  /****************************************************************************/
  /**
  * Undoes the effect of preprocess, qv.
  *
  * @param doc Document being processed.
  */

  protected open fun postprocess (doc: Document)
  {
  }


  /****************************************************************************/
  /**
  * Performs any preprocessing which may simplify later processing.  If this
  * makes any destructive changes, you need a corresponding postprocess method
  * to restore things.
  *
  * @param doc Document being processed.
  */

  protected open fun preprocess (doc: Document)
  {
  }


  /****************************************************************************/
  /**
  * Assesses whether a node is of relevance to the processing here.
  * Relevance is determined mainly in terms of whether the node impacts the
  * job of gathering word counts and canonical title starts and ends.
  *
  * The return value
  */

  protected abstract fun getRelevanceOfNode (node: Node): NodeRelevance
  protected enum class NodeRelevanceType { VerseStart, VerseEnd, CanonicalTitleStart, CanonicalTitleEnd, Text, Boring }
  protected data class NodeRelevance (val nodeType: NodeRelevanceType, val idAsString: String, val andProcessThisNode: Boolean)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val C_Multiplier = RefBase.C_Multiplier.toInt()
  private val m_BookAbbreviationToFilePathMappings: MutableMap<String, String> = mutableMapOf()
  private var m_CollectingWordCounts = false
  protected lateinit var m_RefRangeParser: (String) -> RefRange  // A routine to parse individual references.
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
  private class TextDescriptor
  {
    val m_Content = ContentHolder<BookDescriptor>() // The text is made up of books.
    val m_CanonicalTitleDetails: MutableMap<RefKey, Int> = mutableMapOf()
    val m_DuplicateVerses: MutableList<RefKey> = mutableListOf() // Just a list of refkeys.
    val m_ElisionDetails = LinkedHashMap<RefKey, Pair<RefKey, RefKey>>() // Maps the refKey of any verse in an elision to the pair comprising the first and last refKey.
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
    var m_WordCount: Int = 0
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

  private fun handleVerseEid (id: String, wordCount: Int)
  {
    val refKeys = RefRange.rdUsx(id).getAllAsRefs()
    val isElision = refKeys.size > 1

    refKeys.forEach {
      val v = getVerseDescriptor(it.getCopyOfElements())!!
      v.m_WordCount = if (isElision) C_ElementInElision else wordCount
    }
  }


  /****************************************************************************/
  private fun handleVerseSid (id: String): VerseDescriptor
  {
    /**************************************************************************/
    val idAsRef = RefRange.rdUsx(id)
    val refKeys = idAsRef.getAllAsRefKeys()
    var res: VerseDescriptor? = null



    /**************************************************************************/
    /* If this is an elision, create an elision entry for each verse. */

    if (refKeys.size > 1)
    {
      val elisionThunk = Pair(idAsRef.getFirstAsRefKey(), idAsRef.getLastAsRefKey())
      refKeys.forEach { m_Text.m_ElisionDetails[it] = elisionThunk }
    }



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
  private fun makeElts (ref: Ref): IntArray { return ref.getCopyOfElements() }
  private fun makeElts (refKey: RefKey): IntArray { return Ref.rd(refKey).getCopyOfElements() }
  private fun makeElts (b:Int, c: Int = 0, v: Int = 0, s: Int = 0): IntArray { return listOf(b, c, v, s).toIntArray() }
  private fun makeElts (refAsString: String): IntArray { return Ref.rdUsx(refAsString).getCopyOfElements() }
  private fun makeRef_bcv  (elts: IntArray): RefKey { return Ref.rd(elts).toRefKey_bcv()  }
  private fun makeRef_vs   (elts: IntArray): RefKey { return Ref.rd(elts).toRefKey_vs() }
} // BibleStructure





/******************************************************************************/
/**
* OSIS-based text.
*/

class BibleStructureOsis: BibleStructure()
{
  /****************************************************************************/
  /* Does what it says on the tin. */

  override fun isNonCanonical (node: Node): Boolean
  {
    return ".${Dom.getNodeName(node)}." in m_NonCanonicalTags
  }


  /****************************************************************************/
  /* In OSIS, <div type='chapter'> and <chapter> are equivalent.  Processing is
     simplified if we standardise on <chapter>.  Because they are equivalent,
     there is no need to worry about undoing this change later.

     Unfortunately, though, there is an additional issue -- chapters do not
     contain an attribute giving their id according to the documentation, and
     I do need this in the case of Psalms at least, because I need to associate
     any canonical titles with their containing chapter, and I need the id fo
     the chapter.  And this attribute _will_ need to be removed after the
     processing here, because it is non-standard. */

  override fun preprocess (doc: Document)
  {
    /**************************************************************************/
    Dom.findNodesByAttributeValue(doc, "div", "type", "chapter").forEach {
      Dom.deleteAllAttributes(it)
      Dom.setNodeName(it,"chapter")
    }



    /**************************************************************************/
    var chapterNode: Node? = null
    Dom.collectNodesInTree(doc).forEach {
      when (Dom.getNodeName(it))
      {
        "chapter" -> chapterNode = it

        "verse" ->
        {
          if (null != chapterNode)
          {
            val sid = it["sID"]
            if (null != sid)
            {
              val x = Ref.rdOsis(sid.split('-')[0]).formatMeAsOsis("bc") // The split is there in case the verse represents an elision.
              chapterNode!!["sID"] = x
              chapterNode = null
            }
          } // if (null == chapterNode)
        } // verse
      } // when
    } // forEach
  } // preprocess


  /****************************************************************************/
  /* Undoes anything preprocess did which should not be retained. */

  override fun postprocess (doc: Document)
  {
    Dom.findNodesByName(doc, "chapter").forEach { Dom.deleteAllAttributes(it) }
  }


  /****************************************************************************/
  /* This determines the relevance of any given node to the 'load' function
     in BibleStructure.  That function is interested in verse starts and ends,
     and canonical title starts and ends.

     OSIS actually supports both a milestone version of verse markers and an
     enclosing form.  However, the OSIS reference manual states that 'in all
     but rare cases, the milestone form of the verse element should be used',
     and I shall therefore assume this to be the case here.

     Canonical titles are rather more complicated because this definitely _is_
     an enclosing node.  The processing here tells 'load' when we both enter
     and leave the title, and in order to achieve this, it has to retain the
     node itself so it can check when subsequent nodes cease to be contained
     within it. */

  override fun getRelevanceOfNode (node: Node): NodeRelevance
  {
    when (Dom.getNodeName(node))
    {
      "verse" ->
      {
        return if (Dom.hasAttribute(node, "sID"))
          NodeRelevance(NodeRelevanceType.VerseStart, getStandardisedId(node, "sID"), false)
        else
          NodeRelevance(NodeRelevanceType.VerseEnd,   getStandardisedId(node, "eID"), false)
      }


      "chapter" ->
      {
        m_CurrentChapterRefAsString = getStandardisedId(node, "sID")
      }


      "title" ->
      {
        val type = node["type"]
        if ("psalm" == type)
        {
          m_CurrentCanonicalTitleNode = node
          return NodeRelevance(NodeRelevanceType.CanonicalTitleStart, m_CurrentChapterRefAsString, false)
        }
      }


      else ->
      {
        if (null != m_CurrentCanonicalTitleNode)
        {
          m_CurrentCanonicalTitleNode = null
          return NodeRelevance(NodeRelevanceType.CanonicalTitleEnd, m_CurrentChapterRefAsString, false)
        }
      }

    } // when

    return NodeRelevance(NodeRelevanceType.Boring, "", false)
  }


  /****************************************************************************/
  private var m_CurrentCanonicalTitleNode: Node? = null
  private var m_CurrentChapterRefAsString: String = ""
  private var m_NonCanonicalTags = ""


  /*****************************************************************************/
  init {
    /***************************************************************************/
    m_RefRangeParser = { text -> RefRange.rdOsis(text, null, null) }




    /***************************************************************************/
    /* List of non-canonical things.  It's not entirely straightforward to come
       up with a list here.

       - There's really no need to include things which cannot be found inside
         a verse, because I look for canonical text only when I am within a
         verse (or actually, also within a canonical header).

       - The OSIS reference manual gives a list of the things which are
         permitted within an _enclosing_ verse, but I'm not 100% sure that
         carries through to things which you can legitimately have within a
         pair of _milestone_ verse markers.  In nay case, the XSD gives a more
         extensive list.

       - In the end, I've decided all I can really do is a best guess, based
         upon looking at the OSIS tags mentioned in Appendix F of the OSIS
         reference manual (the USFM-to-OSIS mappings supplied by SIL).  This
         won't be complete, but hopefully it will be good enough.

       - I make the further assumption that all canonical text will be inside
         a <chapter> node.  OSIS actually supports div:chapter as an alternative
         to this, but I have preprocessing which converts all div:chapter
         elements to <chapter>.  This is useful, because it means that I can
         then simply rule out as non-canonical all remaining div tags.  That
         saves a lot of processing, because div comes in a lot of flavours.

       Things below are ordered based on my guess as to which are most frequent.
    */

    m_NonCanonicalTags += ".note"
    m_NonCanonicalTags += ".reference"
    m_NonCanonicalTags += ".div" // All sorts of things.
    m_NonCanonicalTags += ".title"
    m_NonCanonicalTags += ".fig"
    m_NonCanonicalTags += ".map"
    m_NonCanonicalTags += ".ndx" // Subject index entry.
    m_NonCanonicalTags += ".v" // Verse number.
    m_NonCanonicalTags += ".va" // Alternate verse number.
    m_NonCanonicalTags += ".vp" // Publishing alternate verse number.
    m_NonCanonicalTags += "."
  }
}





/******************************************************************************/
/**
* USX-based text.  This is slightly complicated by the fact that I'd like this
* one class to handle both plain vanilla USX and also extended USX.
*
* I think USX supports only _milestone_ tags, and (comes as news to me), it
* does not inherently support sid / eid markup -- it relies on simple numbers.
* (I _do_ generate sid / eid, though).  And while I thought it now requires
* the ends of verses to be marked as well as the beginnings, I can't see any
* mention of that in the reference manual.
*
* Anyway ...
*
* - I add sid / eid here if not already present.
*
* - If you want word counts, you need to have both start and end milestones.
*   I add these when generating enhanced USX, so you should restrict any
*   request for word counts to enhanced text.
*/

open class BibleStructureUsx: BibleStructure()
{
  /****************************************************************************/
  /* This determines the relevance of any given node to the 'load' function
     in BibleStructure.  That function is interested in verse starts and ends,
     and canonical title starts and ends.

     OSIS actually supports both a milestone version of verse markers and an
     enclosing form.  However, the OSIS reference manual states that 'in all
     but rare cases, the milestone form of the verse element should be used',
     and I shall therefore assume this to be the case here.

     Canonical titles are rather more complicated because this definitely _is_
     an enclosing node.  The processing here tells 'load' when we both enter
     and leave the title, and in order to achieve this, it has to retain the
     node itself so it can check when subsequent nodes cease to be contained
     within it. */

  override fun getRelevanceOfNode (node: Node): NodeRelevance
  {
    when (Dom.getNodeName(node))
    {
      "verse" ->
      {
        val id: String
        var type: NodeRelevanceType

        if ("sid" in node)
        {
          type = NodeRelevanceType.VerseStart
          id = node["sid"]!!
        }
        else
        {
          type = NodeRelevanceType.VerseEnd
          id = node["eid"]!!
        }

        // When processing USX, I add dummy verses at the end of each chapter to simplify processing.  We don't want to include these.
        if (RefBase.C_BackstopVerseNumber == RefRange.rdUsx(id).getLowAsRef().getV()) // RefRange because we may need to cope with elisions.
          type = NodeRelevanceType.Boring

        return NodeRelevance(type, id, false)
      }


      "chapter" ->
      {
        if (Dom.hasAttribute(node, "sid"))
          m_CurrentChapterRefAsString = node["sid"]!!
      }


      "para" ->
      {
        if ("d" == node["style"])
        {
          m_CurrentCanonicalTitleNode = node
          return NodeRelevance(NodeRelevanceType.CanonicalTitleStart, m_CurrentChapterRefAsString, false)
        }
      }


      else ->
      {
        if (null != m_CurrentCanonicalTitleNode)
        {
          m_CurrentCanonicalTitleNode = null
          return NodeRelevance(NodeRelevanceType.CanonicalTitleEnd, m_CurrentChapterRefAsString, false)
        }
      }

    } // when

    return NodeRelevance(NodeRelevanceType.Boring, "", false)
  }


  /****************************************************************************/
  override fun isNonCanonical (node: Node): Boolean
  {
    when (Dom.getNodeName(node))
    {
      "note" -> return true

      "para" ->
      {
        val style = node["style"]!!
        return style.matches("s\\d+|sr".toRegex()) // s\\d+ is section heading.  sr is references for section heading.
      }

      else -> return false
    }
  }


  /****************************************************************************/
  /* Ensure we have sids / eids.  I don't think there's any particular need
  *  to remove them again. */

  override fun preprocess (doc: Document)
  {
    MiscellaneousUtils.sidify(doc)
  }


  /****************************************************************************/
  private var m_CurrentCanonicalTitleNode: Node? = null
  private var m_CurrentChapterRefAsString = ""


  /****************************************************************************/
 init {
    /**************************************************************************/
    m_RefRangeParser = { text -> RefRange.rdUsx(text, null, null) }
  }
}





/******************************************************************************/
/**
* osis2mod scheme.
*/

class BibleStructureOsis2ModScheme (scheme: String): BibleStructure()
{
  /****************************************************************************/
  private val m_Scheme: String


  /****************************************************************************/
  override fun addFromDom (doc: Document, wantWordCount: Boolean, filePath: String?, bookName: String?) { throw StepException("Can't populate osis2mod scheme from text.") }
  override fun commonGetWordCount(elts: IntArray): Int { throw StepException("Can't ask for word count on an osis2mod scheme, because the schemes are abstract and have no text.") }
  override fun commonGetWordCountForCanonicalTitle(elts: IntArray): Int { throw StepException("Can't ask for word count on an osis2mod scheme, because the schemes are abstract and have no text.") }
  override fun getRelevanceOfNode (node: Node): NodeRelevance { throw StepException("getRelevanceOfNode should not be being called on an osis2mod scheme.") }
  override fun isNonCanonical (node: Node): Boolean { throw StepException("isNonCanonical should not be being called on an osis2mod scheme.") }
  override fun load (doc: Document, wantWordCount: Boolean) { throw StepException("load should not be being called on an osis2mod scheme.") }


  /****************************************************************************/
  /* A line in the data looks like    Calvin/Rut/22, 23, 18, 22   */

  private fun parseData ()
  {
    /**************************************************************************/
    fun processLine (line: String)
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
    StandardFileLocations.getInputStream(StandardFileLocations.getOsis2modVersificationDetailsFilePath(), null)!!.bufferedReader().use { it.readText() } .lines()
      .map { it.trim() }
      .filter { it.startsWith(selector) } // Limit to the lines for this text.
      .forEach { processLine(it) }
  }


  /****************************************************************************/
  init {
    m_Scheme = BibleStructuresSupportedByOsis2mod.canonicaliseSchemeName(scheme)
    m_RefRangeParser = { _ -> throw StepException("Parsing method should not be being called.") }
    parseData()
  }
}
