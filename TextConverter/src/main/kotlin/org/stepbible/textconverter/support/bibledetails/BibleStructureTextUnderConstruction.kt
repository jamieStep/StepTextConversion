/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
import org.stepbible.textconverter.support.usx.Usx
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.ref.RefRange
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Stores and makes available information about the book / chapter / verse
 * structure of the vernacular text.  Note that if you populate the structures
 * here from a folder, the class processes *all* files in that folder: it does
 * not limit itself to those files selected by any debug limitations.
 *
 * The class is a singleton to make it easier to find (ie you don't have to
 * pass instances around).  Note, though, that this doesn't preclude you from
 * using it early in proceedings to reflect the content of the raw USX files
 * and later to reflect the content of the enhanced ones -- each time you call
 * any of the populate\* functions, it clears out its internal data
 * structures.
 *
 * The class builds up details of which chapters and verses are present and
 * also the word counts for the various verses and any canonical titles (the
 * word counts only if you inform the populate\* method that you want them).
 *
 *
 * This class can cope with texts in which verse-starts contain either sids or
 * numbers (it does assume uniformity, though -- if any chapter or verse has a
 * sid, I assume all do, and if any has a number, I assume all do.  It does
 * not rely upon the existence of verse-end markers.
 *
 * @author ARA "Jamie" Jamieson
 */

object BibleStructureTextUnderConstruction: BibleStructure("underconstruction")
{
    /****************************************************************************/
    /* A note on the internal structure, which is in essence a tree.

       At the top, we have Bible, which has no data of its own: its only purpose
       is to hold a collection of books (class 'B').

       B's hold a collection of C's.  They also have an error flag which is used
       to indicate whether the collection of C's is incomplete or wrongly
       ordered.

       C's similarly hold a collection of V's, and also have an error flag to
       indicate whether the collection of V's is incomplete or wrongly ordered.
       In addition, each C has a member which can store the word count in any
       associated canonical header.

       V's hold a collection of S's.  They too have an error flag, used for
       similar purposes; and they have a flag to indicate whether they are
       part of an elision.

       And finally S's contain just a word count.  All V's have at least one
       S (subverse zero), which holds the word count for the owning verse as
       a whole.  If the verse is made up of a number of subverses, then the
       word count in subverse zero is the aggregate across all of the other
       subverses (and therefore still represents the word count for the
       verse as a whole).

       Callers which request the word count may also get one of two special
       values -- C_ElementUnavailable (if they ask for the word count for a
       verse which does not exist) or C_ElementInElision if they ask for the
       count for something which forms part of an elision (word counts are
       meaningless in an elision, because there is no way of determining how
       much text belongs to each of the elements of the elision.
    */
    /****************************************************************************/



    /****************************************************************************/
    /**                                                                        **/
    /**                               Public                                   **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

    /****************************************************************************/
    /** Returned when you compare the text under construction with another
     *  scheme.  Hopefully the purposes of the fields are obvious.  All are
     *  returned as sorted lists of RefKeys. */

    data class ComparisonWithOtherScheme (val inTextUnderConstructionButNotInOtherScheme: List<RefKey>,
                                          val inOtherSchemeButNotInTextUnderConstruction: List<RefKey>,
                                          val inBoth: List<RefKey>)


    /****************************************************************************/
    /** Values returned for counts when the counts may not make sense. */

    const val C_ElementUnavailable = -1
    const val C_ElementInElision   = -2


    /****************************************************************************/
    /**
    * Compares the text of a single book under construction with NRSVx.
    *
    * @param bookNumber What it says on the tin.
    * @pram otherScheme The scheme against which to compare.
    * @return ComparisonWithOtherText giving details of common verses etc.
    */

    fun compareWithGivenScheme (bookNumber: Int, otherScheme: BibleStructureSupportedByOsis2modIndividual): ComparisonWithOtherScheme
    {
      val inTextUnderConstruction = getAllRefKeysForBook(bookNumber).toSet()
      val inOtherScheme = otherScheme.getAllRefKeysForBook(bookNumber).toSet()
      val inTextUnderConstructionButNotInOtherScheme = inTextUnderConstruction subtract inOtherScheme
      val inOtherSchemeButNotInTextUnderConstruction = inOtherScheme subtract inTextUnderConstruction
      val inBoth = inTextUnderConstruction intersect inOtherScheme
      //if ("act".equals(BibleBookNamesUsx.numberToAbbreviatedName(bookNumber), ignoreCase = true)) Dbg.d(inTextUnderConstruction.joinToString(", "))
      return ComparisonWithOtherScheme(inTextUnderConstructionButNotInOtherScheme.sorted(), inOtherSchemeButNotInTextUnderConstruction.sorted(), inBoth.sorted())
    }


    /****************************************************************************/
    /**
     * Returns a list of all of UBS numbers for all of the Bible books available.
     *
     * @return List of UBS numbers.
     */

    fun getAllBookNumbers (): List<Int>
    {
      return Bible.m_Books.keys.sorted().toList()
    }


    /****************************************************************************/
    /**
     * Obtains a full list of all verse references for a given book.
     *
     * I make the assumption here that there are no gaps in the chapter
     * numbering -- ie it runs from 1 to whatever.
     *
     * @param bookNumber Book number.
     *
     * @return Full list of all references for given book.
     */

    override fun getAllRefKeysForBook (bookNumber: Int): List<RefKey>
    {
      val res: MutableList<RefKey> = mutableListOf()

      fun processChapter (bookNumber: Int, chapterNumber: Int, chapter: C)
      {
        val maxKey = chapter.m_Verses.keys.max()
        for (i in 1 .. maxKey)
          if (!chapter.m_Verses[i]!!.m_Missing)
            res.add(Ref.rd(bookNumber, chapterNumber, i, RefBase.C_DummyElement).toRefKey())
      }

      Bible.m_Books[bookNumber]!!.m_Chapters.forEach { processChapter(bookNumber, it.key, it.value) }

      return res
    }


    /****************************************************************************/
    /**
    * Returns a list of refKeys for missing verses.  Note that this
    * only detects holes within the list of verses -- I don't know a priori the
    * verse number where a chapter is supposed to end, so I can't tell if things
    * are missing at the end.
    *
    * @return List of missing verses.
    */

    fun getAllRefKeysForMissingVerses (): List<RefKey>
    {
       val res:MutableList<RefKey> = ArrayList()

       fun processChapter (bookNumber: Int, chapterNumber: Int, chapter: C)
       {
         val maxKey = chapter.m_Verses.keys.max()
         for (i in 1 .. maxKey)
           if (!chapter.m_Verses.contains(i))
             res.add(Ref.rd(bookNumber, chapterNumber, i, RefBase.C_DummyElement).toRefKey())
       }

       fun processBook (bookNumber: Int, book: B)
       {
         book.m_Chapters.forEach { processChapter(bookNumber, it.key, it.value) }
       }

       Bible.m_Books.forEach { processBook(it.key, it.value) }

       return res
    }


    /****************************************************************************/
    /**
    * Returns a list of refKeys for verses which have subverses.
    *
    * @return List of verses which have subverses.
    */

    fun getAllRefKeysForVersesHavingSubverses (): List<RefKey>
    {
      val res:MutableList<RefKey> = ArrayList()

      fun processVerse (bookNumber: Int, chapterNumber: Int, verseNumber: Int, verse: V)
      {
        if (verse.m_Subverses.size > 1)  res.add(Ref.rd(bookNumber, chapterNumber, verseNumber, RefBase.C_DummyElement).toRefKey())
      }

      fun processChapter (bookNumber: Int, chapterNumber: Int, chapter: C)
      {
        chapter.m_Verses.forEach { processVerse(bookNumber, chapterNumber, it.key, it.value) }
      }

      fun processBook (bookNumber: Int, book: B)
      {
        book.m_Chapters.forEach { processChapter(bookNumber, it.key, it.value)}
      }

      Bible.m_Books.forEach { processBook(it.key, it.value) }

      return res
    }


    /****************************************************************************/
    /**
     * Does what it says on the tin.  CAUTION: If you are dealing with Vulgate-
     * based texts, chapters A onwards are represented internally by numbers
     * around 500, so for books which are affected by this, the chapter
     * numbering is somewhat artificial.
     *
     * @param bookNo Book of interest.
     * @return Number of chapters.
     */

    override fun getLastChapterNo (bookNo: Int): Int
    {
        val bookDetails = getBookDetails(bookNo)
        return bookDetails?.m_Chapters?.size ?: C_ElementUnavailable
    }


    /****************************************************************************/
    /**
     * Returns the last verse number for a given chapter.
     *
     * @param refKey
     *
     * @return Number of last verse, or C_ElementUnavailable if the book / chapter does not exist.
     */

    override fun getLastVerseNo (refKey: RefKey): Int
    {
        val chapterDetails = getChapterDetails(refKey)
        return chapterDetails?.m_Verses?.size ?: C_ElementUnavailable
    }


    /****************************************************************************/
    /**
     * Returns the word count for the given element.
     *
     * @param ref Ref for the psalm of interest.  Only the book (psalms)
     *            and the chapter are needed.  You can supply other things if
     *            you wish, but they are ignored.
     *            
     * @return Word count, or error indicator.
     */

    fun getCanonicalTitleWordCount (ref: Ref):          Int { return getCanonicalTitleWordCount(ref.toRefKey()) }
    fun getCanonicalTitleWordCount (refString: String): Int { return getCanonicalTitleWordCount(Ref.rdUsx(refString).toRefKey()) }
    fun getCanonicalTitleWordCount (refKey: RefKey):    Int { return getChapterDetails(refKey)?.m_CanonicalTitleWordCount ?: C_ElementUnavailable }

    fun getWordCount (ref: Ref):          Int { return getWordCount(ref.toRefKey()) }
    fun getWordCount (refString: String): Int { return getWordCount(Ref.rdUsx(refString).toRefKey()) }
    fun getWordCount (refKey: RefKey):    Int { return getWordCountA(refKey) }


    /****************************************************************************/
    /**
     * Checks to see if we have details for the given element.
     */

    override fun hasBook (bookNo: Int): Boolean { return null != getBookDetails(bookNo) }
    override fun hasChapter (refKey: RefKey): Boolean  { return null != getChapterDetails(refKey) }
    override fun hasVerseAsVerse (refKey: RefKey): Boolean { val details = getVerseDetails(refKey); return null != details && 1 == details.m_Subverses.size }
    override fun hasVerseAsSubverses (refKey: RefKey): Boolean { val details = getVerseDetails(refKey); return null != details && details.m_Subverses.size > 1 }
    override fun hasVerseAsVerseOrSubverses (refKey: RefKey): Boolean { val details = getVerseDetails(refKey); return null != details }
    override fun hasSubverse (refKey: RefKey): Boolean { return null != getSubverseDetails(refKey) }


    /****************************************************************************/
    /**
     * Checks if the text contains a canonical title for a given psalm.
     *
     * @param ref Identifies the psalm of interest (need to supply at least
     *            the book and chapter; anything else you supply is ignored).
     *
     * @return True if canonical title is available.
     */

            fun hasCanonicalTitle (ref: Ref):          Boolean { return hasCanonicalTitle(ref.toRefKey()) }
    private fun hasCanonicalTitle (refKey: RefKey):    Boolean { return getCanonicalTitleWordCount(refKey) >= 0 }



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

    fun isAdjacent (lowBcvs: RefKey, highBcvs: RefKey): Boolean
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
    /**
     * Populates the instance from a given document.
     *
     * @param wantWordCount True if the caller requires a count of the words in each verse.
     * @param reportIssues True if we are to report missing verses etc.
     */

    fun populate (document: Document, wantWordCount: Boolean = true, reportIssues: Boolean = true)
    {
       Bible.reset()
       processFile("", document, wantWordCount, reportIssues)
       //Dbg.d(Bible.prettyPrint())
    }


    /****************************************************************************/
    /**
     * Populates the instance from the collection of files held in
     * [BibleBookAndFileMapperRawUsx].
     *
     * @param mapper Maps books to the files which contain their content.
     * @param wantWordCount True if the caller requires a count of the words in each verse.
     * @param reportIssues True if we are to report missing verses etc.
     */

    fun populate (mapper: BibleBookAndFileMapper, wantWordCount: Boolean = true, reportIssues: Boolean = true)
    {
       Dbg.reportProgress("  Determining Bible structure")
       fun process (filePath: String, document: Document) { processFile(filePath, document, wantWordCount, reportIssues) }
       Bible.reset()
       mapper.iterateOverSelectedFiles(::process)
       //Dbg.d(Bible.prettyPrint())
    }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                               Private                                  **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/
  
    /********************************************************************************/
    private fun buildDataStructureFromFile (@Suppress("UNUSED_PARAMETER") filePath: String, document: Document)
    {
        /****************************************************************************/
        var currentBookInstance = B()
        var currentChapterInstance = C()
        var bookName = ""
        var chapterNo = ""

        fun processNode (node: Node)
        {
            when (Dom.getNodeName(node))
            {
                /************************************************************/
                "book", "_X_book" ->
                {
                    bookName = Dom.getAttribute(node, "code")!!

                    val bookNo = BibleBookNamesUsx.nameToNumber(bookName)

                    currentBookInstance = Bible.add(bookNo)

                    if (BibleAnatomy.isOt(bookNo))
                        m_HasOt = true
                    else if (BibleAnatomy.isNt(bookNo))
                        m_HasNt = true
                    else
                        m_HasDc = true
                }



                /************************************************************/
                "chapter", "_X_chapter" ->
                {
                    if (Dom.hasAttribute(node, "eid"))
                      return

                    if (Dom.hasAttribute(node, "number"))
                    {
                      chapterNo = Dom.getAttribute(node, "number")!!
                      currentChapterInstance = currentBookInstance.add(Ref.rdUsx(bookName + " " + chapterNo).toRefKey())
                    }
                    else
                    {
                      val x = Dom.getAttribute(node, "sid")!!
                      chapterNo = Ref.rdUsx(x).getC().toString()
                      currentChapterInstance = currentBookInstance.add(Ref.rdUsx(x).toRefKey())
                    }
                }



                /************************************************************/
                /* Note A: Where we have subverses, we always also need to
                   have an entry for the base verse, devoid of any subverse
                   mark.  Some texts always do this, but in some texts you
                   may start straight in with subverse a), with nothing prior
                   to that.  The code at note A tracks this case by recording
                   the verse reference for the previous element.  If we are
                   dealing with a subverse, and if this subverse is for a
                   different verse from the previous one, we need to create
                   the empty verse. */

                "verse" ->
                {
                    if (Dom.hasAttribute(node, "eid"))
                      return

                    val refKeyRange = if (Dom.hasAttribute(node, "sid"))
                      makeRefKeyRange(node, Dom.getAttribute(node, "sid")!!)
                    else
                      makeRefKeyRange(node, bookName + " " + chapterNo + ":" + Dom.getAttribute(node, "number")!!)

                    if (Ref.getV(refKeyRange.first) == RefBase.C_BackstopVerseNumber) // If we happen to have the temporary backstop verses I use still hanging around, ignore them.
                      return

                    if (Ref.hasS(refKeyRange.first) && Ref.clearS(refKeyRange.first) != m_PreviousRefKey) // See note A above.
                    {
                      val verseRefKey = Ref.clearS(refKeyRange.first)
                      currentChapterInstance.add(verseRefKey, false)
                    }

                    m_PreviousRefKey = Ref.clearS(refKeyRange.second)

                    val elision = refKeyRange.first != refKeyRange.second
                    for (i in refKeyRange.first .. refKeyRange.second)
                        currentChapterInstance.add(i, elision)
                } // verse
            } // when
        } // fun processNode



        /****************************************************************************/
        val nodes = Dom.collectNodesInTree(document)
        nodes.forEach { processNode(it) }
    }

    private var m_PreviousRefKey: RefKey = 0


    /********************************************************************************/
    private fun aggregateWordCounts ()
    {
        /****************************************************************************/
        fun aggregateWordCounts (v: V)
        {
          /**************************************************************************/
          /* If any element has a special marker, ensure they all do. */

          var force: Int? = null
          run overSubverses@ {
            v.m_Subverses.forEach {
              if (C_ElementInElision == it.value.m_WordCount)
              {
                force = C_ElementInElision
                return@overSubverses
              }
              else if (C_ElementUnavailable == it.value.m_WordCount)
              {
                force = C_ElementUnavailable
                return@overSubverses
              }
            }
          }

          if (null != force)
          {
            v.m_Subverses.forEach { it.value.m_WordCount = force!! }
            return
          }



          /**************************************************************************/
          /* Aggregate. */

          var n = 0
          v.m_Subverses.forEach { if (0 != it.key) n += it.value.m_WordCount }
          if (n > 0) v.m_Subverses[0]!!.m_WordCount += n
        }



        /****************************************************************************/
        Bible.m_Books.values
            .forEach { b -> b.m_Chapters.values
                .forEach { c -> c.m_Verses.values
                    .forEach { aggregateWordCounts(it) }}}
    }


    /********************************************************************************/
    private fun checkForIssues ()
    {
        /****************************************************************************/
        var bookName = ""
        var msgs = ""



        /****************************************************************************/
        fun processChapter (bookDetails: B, chapterNo: Int)
        {
            val chapter = bookDetails.m_Chapters[chapterNo]!!
            if (chapter.m_Missing) msgs += "\n$bookName $chapterNo: Verses out of order or missing."
        }



        /****************************************************************************/
        fun processBook (bookNo: Int)
        {
            bookName = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
            val book = Bible.m_Books[bookNo]!!
            if (book.m_Missing) msgs += "\n$bookName: Chapters out of order or missing."
            book.m_Chapters.keys.sorted().forEach { processChapter(book, it) }
        }



        /****************************************************************************/
        Bible.m_Books.keys.sorted().forEach { processBook(it) }
        if (msgs.isNotEmpty()) Logger.warning(msgs)
    }


    /********************************************************************************/
    private fun collectWordCount (document: Document)
    {
        val nodes = Dom.collectNodesInTree(document)
        collectWordCountInCanonicalHeaders(nodes.filter { Dom.hasAttribute(it, "para", "d") })
        collectWordCount(nodes.filter { !Usx.isInherentlyNonCanonicalTag(it) || "verse" == Dom.getNodeName(it) })
        collectWordCountSpecialCases()
        aggregateWordCounts()
    }


    /********************************************************************************/
    private fun collectWordCountSpecialCases ()
    {
        Bible.m_Books.values
            .forEach { b -> b.m_Chapters.values
                .forEach { c -> c.m_Verses.values
                    .filter { v ->  v.m_Missing }
                        .forEach { v -> v.m_Subverses.values
                            .forEach { s -> s.m_WordCount = C_ElementUnavailable }}}}

        Bible.m_Books.values
            .forEach { b -> b.m_Chapters.values
                .forEach { c -> c.m_Verses.values
                    .filter { v ->  v.m_IsPartOfElision }
                        .forEach { v -> v.m_Subverses.values
                            .forEach { s -> s.m_WordCount = C_ElementInElision }}}}
    }


    /********************************************************************************/
    private fun collectWordCount (nodes: List<Node>)
    {
        /****************************************************************************/
        var counting = false
        var currentBookInstance = B()
        var currentBookName = ""
        var currentChapterInstance = C()
        var currentChapterNo = 0
        var currentVerseInstance: V
        var currentSubverseInstance = S()
        var inElision = false
        var wordCount = 0



        /****************************************************************************/
        fun finishCount ()
        {
            if (!counting) return
            currentSubverseInstance.m_WordCount = if (inElision) C_ElementInElision else wordCount
            wordCount = 0
            inElision = false
            counting = false
        }



        /****************************************************************************/
        fun processNode (node: Node)
        {
            when (Dom.getNodeName(node))
            {
                /************************************************************/
                "book", "_X_book" ->
                {
                    finishCount()
                    currentBookName = Dom.getAttribute(node, "code")!!
                    val currentBookNo = BibleBookNamesUsx.nameToNumber(currentBookName)
                    currentBookInstance = Bible.get(currentBookNo)!!
                }



                /************************************************************/
                "chapter", "_X_chapter" ->
                {
                    if (Dom.hasAttribute(node, "eid")) // We won't necessarily have eids, and we need to be able to cope without.
                      return

                    finishCount()
                    currentChapterNo = if (Dom.hasAttribute(node, "number")) Dom.getAttribute(node, "number")!!.toInt() else Ref.rdUsx(Dom.getAttribute(node, "sid")!!).getC()
                    currentChapterInstance = currentBookInstance.m_Chapters[currentChapterNo]!!
                }



                /************************************************************/
                "verse" ->
                {
                    finishCount()

                    if (Dom.hasAttribute(node, "eid")) // We won't necessarily have eids, and we need to be able to cope without.
                      return

                    val sid = Dom.getAttribute(node, "sid") ?:
                                (currentBookName + " " + currentChapterNo.toString() + ":" + Dom.getAttribute(node, "number")!!)
                    counting = true
                    inElision = sid.contains("-")
                    if (inElision)
                    {
                      RefRange.rdUsx(sid).getAllAsRefKeys().forEach {
                        currentVerseInstance = currentChapterInstance.m_Verses[Ref.getV(it)]!!
                        currentSubverseInstance = currentVerseInstance.m_Subverses[Ref.getS(it)]!!
                        currentSubverseInstance.m_WordCount = C_ElementInElision
                      }
                    }
                    else
                    {
                      val refKey = Ref.rdUsx(sid).toRefKey()
                      val currentVerseNo = Ref.getV(refKey)
                      currentVerseInstance = currentChapterInstance.m_Verses[currentVerseNo]!!
                      currentSubverseInstance = currentVerseInstance.m_Subverses[Ref.getS(refKey)]!!
                    }
                 } // verse



                /************************************************************/
                /* No need to check text is canonical -- I deleted all non-
                   canonical trees earlier. */

                else ->
                {
                    if (inElision)
                      wordCount = C_ElementInElision
                    else if (counting && Dom.isTextNode(node))
                      wordCount += determineWordCount(node)
                }
            } // when
        } //fun processNode



        /****************************************************************************/
        nodes.forEach { processNode(it) }
        finishCount()
    }


    /********************************************************************************/
    private fun collectWordCountInCanonicalHeaders (nodes: List<Node>)
    {
        fun doCount (node: Node)
        {
            var wordCount = 0
            Dom.collectNodesInTree(node)
                .filter { !Usx.isInherentlyNonCanonicalTag(it) }
                .filter { Dom.isTextNode(it) }
                .forEach { wordCount += determineWordCount(it)}
            val chapter = Dom.findAncestorByNodeName(node, "_X_chapter")!!
            val chapterRefKey = Ref.rdUsx(Dom.getAttribute(chapter, "sid")!!).toRefKey()
            val chapterDetails = getChapterDetails(chapterRefKey)!!
            chapterDetails.m_CanonicalTitleWordCount = wordCount
        }

        nodes.forEach { doCount(it) }
    }


    /****************************************************************************/
    /* Counts the number of canonical words in a verse. */

    private fun determineWordCount (text: Node): Int
    {
        var fullTextContent = text.textContent
        fullTextContent = fullTextContent.replace("\n", " ").trim { it <= ' ' }
        return if (fullTextContent.isBlank()) 0 else fullTextContent.split("\\p{Z}+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size
    }


    /********************************************************************************/
    private fun getBookDetails (bookNo: Int): B? { return Bible.get(bookNo) }
    private fun getChapterDetails (refKey: RefKey): C? { return getBookDetails(Ref.getB(refKey))?.get(refKey) }
    private fun getVerseDetails (refKey: RefKey): V? { return getChapterDetails(refKey)?.get(refKey) }
    private fun getSubverseDetails (refKey: RefKey): S? { return getVerseDetails(refKey)?.get(refKey) }


    /****************************************************************************/
    /* Gets the word count for a verse or subverse.  A verse will have subverse
       zero set, and a subverse will have something else.  If we have a genuine
       subverse, we'll be pointing at the subverse itself and can use its word
       count.  If we have subverse zero, we will indeed be pointing at a subverse
       for subverse zero, and previous processing will have stored in this the
       count for the entire verse across all subverses (if any). */

    private fun getWordCountA (refKey: RefKey): Int
    {
      //val b = getBookDetails(Ref.getB(refKey))
      //val c = getChapterDetails(refKey)
      //val v = getVerseDetails(refKey)
      //val s = getSubverseDetails(refKey)

      val details = getSubverseDetails(refKey)!!
      return details.m_WordCount
    }


    /****************************************************************************/
   private fun makeRefKeyRange (node: Node, currentRef: String): Pair<RefKey, RefKey>
   {
       /*************************************************************************/
       if (Dom.hasAttribute(node, "sid"))
       {
           val sid = Dom.getAttribute(node, "sid")!!
           val refRange = RefRange.rdUsx(sid)
           if (refRange.getLowAsRef().hasS()) // If we have a subverse, assume it can't be an elision, and we can just take this as-is.
               return Pair(refRange.getLowAsRefKey(), refRange.getLowAsRefKey())

           val verseHigh = refRange.getHighAsRef().getV()
           val lowRefKey = refRange.getLowAsRefKey()
           return Pair(lowRefKey, Ref.setV(lowRefKey, verseHigh))
       }



       /*************************************************************************/
       /* Not sid, so it must be a number. */

       val number = Dom.getAttribute(node, "number")!!
       if (number.matches(Regex(".*[a-z]+"))) // If we have a subverse, assume it can't be an elision, and we can just take this as-is.
       {
           val currentRefPart = currentRef.split(":")[0] + ":" + number
           val refKey = Ref.rdUsx(currentRefPart).toRefKey()
           return Pair(refKey, refKey)
       }

       val x = number.split("-")
       val verseLow = x[0].toInt()
       val verseHigh = if (1 == x.size) verseLow else x[1].toInt()
       val refLow = currentRef.split(":")[0] + ":" + verseLow
       val refKeyLow = Ref.rdUsx(refLow).toRefKey()
       val refHigh = currentRef.split(":")[0] + ":" + verseHigh
       val refKeyHigh = Ref.rdUsx(refHigh).toRefKey()
       return Pair(refKeyLow, refKeyHigh)
    }


    /****************************************************************************/
    private fun processFile (filePath: String, document: Document, wantWordCount: Boolean, reportIssues: Boolean)
    {
        buildDataStructureFromFile(filePath, document)
        if (reportIssues) checkForIssues()
        if (wantWordCount) collectWordCount(document)
    }


    /********************************************************************************/
    private open class ErrorFlagMixin
    {
        var m_Missing = false
    }


    /********************************************************************************/
    private class S: ErrorFlagMixin()
    {
        fun prettyPrint (bookName: String, chapterNo: Int, verseNo: Int, subverseNo: Int): String
        {
          return bookName + " " + chapterNo + ":" + verseNo + convertNumberToRepeatingString(subverseNo, 'a', 'z')
        }

        var m_WordCount = 0
    }


    /********************************************************************************/
    private class V: ErrorFlagMixin()
    {
        fun get (refKey: RefKey): S? { return m_Subverses[Ref.getS(refKey)] }
        fun add (refKey: RefKey): S { return add(m_Subverses, Ref.getS(refKey), ::maker_S, false) }

        fun prettyPrint (bookName: String, chapterNo: Int, verseNo: Int): String
        {
          val res = m_Subverses.keys.joinToString(" / ") { m_Subverses[it]!!.prettyPrint(bookName, chapterNo, verseNo, it) }
          return res.ifEmpty { "\u0001" }
        }

        val m_Subverses: MutableMap<Int, S> = HashMap()
        var m_IsPartOfElision = false
    }


    /********************************************************************************/
    private class C: ErrorFlagMixin()
    {
        fun get (refKey: RefKey): V?  { return m_Verses[Ref.getV(refKey)] }

        fun add (refKey: RefKey, isPartOfElision: Boolean): V
        {
            val res = add(m_Verses, Ref.getV(refKey), ::maker_V)
            res.add(refKey)
            res.m_IsPartOfElision = isPartOfElision
            return res
        }

        fun prettyPrint (bookName: String, chapterNo: Int): String
        {
          return m_Verses.keys.joinToString("\n") { m_Verses[it]!!.prettyPrint(bookName, chapterNo, it) }
        }

        val m_Verses: MutableMap<Int, V> = HashMap()
        var m_CanonicalTitleWordCount = C_ElementUnavailable
    }


    /********************************************************************************/
    private class B: ErrorFlagMixin()
    {
        fun add (refKey: RefKey): C { return add(m_Chapters, Ref.getC(refKey), ::maker_C) }
        fun get (refKey: RefKey): C?  { return m_Chapters[Ref.getC(refKey)] }
        val m_Chapters: MutableMap<Int, C> = HashMap()

        fun prettyPrint (bookNo: Int): String
        {
          val bookName = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
          return m_Chapters.keys.joinToString("\n\n==========\n") { m_Chapters[it]!!.prettyPrint(bookName, it) }
        }
    }


    /********************************************************************************/
    private object Bible
    {
        fun add (bookNo: Int): B { val res = B(); m_Books[bookNo] = res; return res }
        fun get (bookNo: Int): B? { return m_Books[bookNo] }
        fun reset() { m_Books = HashMap() }
        var m_Books: MutableMap<Int, B> = HashMap()

        fun prettyPrint (): String
        {
          val res = m_Books.keys.joinToString("\n\n\n====================\n") { m_Books[it]!!.prettyPrint(it) }
          return res.replace("\u0001\n", "")
        }
    }


    private var m_HaveVerseEndMarkers = false





    /********************************************************************************/
    /********************************************************************************/
    /********************************************************************************/
    /********************************************************************************/
    /********************************************************************************/

    /********************************************************************************/
    fun maker_C (): Any { return C() }
    fun maker_V (): Any { return V() }
    fun maker_S (): Any { return S() }


    /********************************************************************************/
    /* Adds an entry to one of the maps in B / C.
    *
    *  This has recently become more complicated because we have encountered texts
    *  in which the translators have deliberately placed verses in the wrong order.
    *  Previously it hadn't occurred to me that this would ever happen.
    *
    *  The loop below creates verses which appear, at a given point, to be missing.
    *  However, if we have out-of-order material, it is possible we may encounter
    *  one of the missing verses later, which is why I clear the 'missing' flag in
    *  the null != map[ix] clause near the top of the processing.
    **/

    private fun <CONTENT_TYPE> add (map: MutableMap<Int, CONTENT_TYPE>, ix: Int, maker: () -> Any, checkSequence: Boolean = true): CONTENT_TYPE
    {
      if (null != map[ix])
      {
        (map[ix] as ErrorFlagMixin).m_Missing = false
        return map[ix] as CONTENT_TYPE
      }

      val res = maker() as CONTENT_TYPE

      if (checkSequence && (res !is C || ix < RefBase.C_AlphaChapterOffset)) // Carry out checks only if we've been asked to do so, and if we're not dealing with a Vulgate alpha chapter designator.
        for (i in map.size + 1 until ix)
        {
          val entry = maker() as CONTENT_TYPE
          (entry as ErrorFlagMixin).m_Missing = true
          map[i] = entry
        }

      map[ix] = res

      return res
    }
}
