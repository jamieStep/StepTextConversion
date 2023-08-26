package org.stepbible.textconverter.support.bibledetails

import java.util.ArrayList
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepException
import kotlin.math.abs


/******************************************************************************/
/**
 * Base class for details of chapter / verse structure of Bibles.
 *
 * Two types of Bible details derive from the present class.  There are
 * 'standard' ones, like NRSVA, for which we have somewhat limited information,
 * but this information is known a priori; and there is the vernacular text
 * currently being processed, for which more extensive information is available,
 * but the information can be determined only by examining the text.
 *
 * The standard details are all structured in a similar manner, and derive from
 * [BibleStructureSupportedByOsis2modIndividual].
 *
 * Note that it is vanishingly unlikely that you will want to make use of this
 * present base class in your code -- at various points in the processing you
 * may want to deal *either* with a standard Bible *or* with a vernacular Bible,
 * but the two things really serve very different purposes.
 *
 * Many of the methods below come in a number of flavours, thus making it
 * possible to pass a reference as Ref, a string representing a USX reference,
 * or a refKey.  In general, there is no problem in over-specifying arguments.
 * Thus, for instance, if you are checking for the existence of a chapter, you
 * can pass something which includes book, chapter, verse and subverse.
 * 
 * @author ARA "Jamie" Jamieson
*/

abstract class BibleStructure
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  /**
   * Basic constructor.
   *
   * @param scheme Versification scheme name.
   */

  constructor (scheme: String) { m_Scheme = scheme }


  /****************************************************************************/
  /**
  * Copy constructor.
  *
  * @param other Another BibleStructure
  */

  constructor (other: BibleStructure)
  {
    m_Scheme = other.m_Scheme
    m_HasDc = other.hasDc()
    m_HasNt = other.hasNt()
    m_HasOt = other.hasOt()
  }


  /****************************************************************************/
  /**
   * Obtains a full list of all verse references for a given book.
   *
   * I make the assumption here that there are no gaps in the chapter
   * numbering -- ie it runs from 1 to whatever.
   *
   * @param bookNo Book number.
   *
   * @return Full list of all references for given book.
   */

  open fun getAllRefKeysForBook (bookNo: Int): List<RefKey>
  {
    val res: MutableList<RefKey> = ArrayList()
    val n = getLastChapterNo(bookNo)
    for (chapterNo in 1 .. n) res.addAll(getAllRefKeysForChapter(bookNo, chapterNo))
    return res
  }

  fun getAllRefKeysForBook (ref: Ref):          List<RefKey> { return getAllRefKeysForBook(ref.toRefKey()) }
  fun getAllRefKeysForBook (refOrName: String): List<RefKey> { return getAllRefKeysForBook(BibleBookNamesUsx.nameToNumber(refOrName.split(" ")[0])) }
  fun getAllRefKeysForBook (refKey: RefKey):    List<RefKey> { return getAllRefKeysForBook(Ref.getB(refKey)) }


  /****************************************************************************/
  /**
   * Obtains a full list of all verse references for a given book.
   * <p>
   * IMPORTANT: This method is aimed mainly at KJV(A) and NRSV(A).  You
   * <i>can</i> use it with vernacular texts, but you do so at your own risk,
   * since it does not cater for the possibility that a vernacular text may lack
   * verses in some places.
   * 
   * @param bookNo Book number.
   * @param chapterNo Chapter number.
   * 
   * @return Full list of all references for given book of the NRSVA.
   */
  
  fun getAllRefKeysForChapter (bookNo: Int, chapterNo: Int): List<RefKey>
  {
    val res: MutableList<RefKey> = ArrayList()
    val n = getLastVerseNo(bookNo, chapterNo)
    val ref = Ref.rd(bookNo, chapterNo, 0)
    for (i in 1 .. n) { ref.setV(i); res.add(ref.toRefKey()) }
    return res
  }

  fun getAllRefKeysForChapter (ref: Ref):           List<RefKey> { return getAllRefKeysForChapter(ref.getB(), ref.getC()) }
  fun getAllRefKeysForChapter (refString: String):  List<RefKey> { val r = Ref.rdUsx(refString); return getAllRefKeysForChapter(r.getB(), r.getC()) }
  fun getAllRefKeysForChapter (refKey: RefKey):     List<RefKey> { return getAllRefKeysForChapter(Ref.getB(refKey), Ref.getC(refKey)) }
  
  
  /****************************************************************************/
  /**
   * Returns the last verse number for a given chapter.
   * 
   * @param b Book.
   * @param c Chapter
   * 
   * @return Number of last verse, or 0 if book / chapter does not exist.
   */

  fun getLastVerseNo (b: Int, c: Int):    Int { return getLastVerseNo(Ref.rd(b, c, 0, 0)) }
  fun getLastVerseNo (ref: Ref):          Int { return getLastVerseNo(ref.toRefKey()) }
  fun getLastVerseNo (refString: String): Int { return getLastVerseNo(Ref.rdUsx(refString).toRefKey()) }
  abstract fun getLastVerseNo (refKey: RefKey): Int

  
  
  /****************************************************************************/
  /**
   * Does what it says on the tin.
   * 
   * @param bookNo Book of interest.
   * @return Number of chapters.
   */
  
  abstract fun getLastChapterNo (bookNo: Int): Int
  fun getLastChapterNo (ref: Ref):          Int { return getLastChapterNo(ref.toRefKey()) }
  fun getLastChapterNo (refOrName: String): Int { return getLastChapterNo(BibleBookNamesUsx.nameToNumber(refOrName.split(" ")[0])) }
  fun getLastChapterNo (refKey: RefKey):    Int { return getLastChapterNo(Ref.getB(refKey)) }

  
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
  
  fun getRefKeysInRange (theRefKeyLow: RefKey, theRefKeyHigh: RefKey): List<RefKey>
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

  fun getSubVerseDifference (refKey1: RefKey, refKey2: RefKey): Int
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
  
  fun getVerseDifference (theRefKey1: RefKey, theRefKey2: RefKey): Int
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
    for (i in chapter1 + 1 until chapter2)
      n += getLastVerseNo(bookNo, i)
    n += Ref.getV(refKey2)
    
    return multiplier * n
  }


  /****************************************************************************/
  /**
   * Checks to see if we have details for the given element.  Normally we will,
   * but USX supports some books which NRSVA does not, and this enables the
   * processing to check before proceeding.
   */

  fun hasBook (ref: Ref):          Boolean { return hasBook(ref.toRefKey()) }
  fun hasBook (refOrName: String): Boolean { return hasBook(BibleBookNamesUsx.nameToNumber(refOrName.split(" ")[0])) }
  fun hasBook (refKey: RefKey):    Boolean { return hasBook(Ref.getB(refKey)) }
  abstract fun hasBook (bookNo: Int): Boolean

  fun hasChapter (ref: Ref):          Boolean { return hasChapter(ref.toRefKey()) }
  fun hasChapter (refString: String): Boolean { return hasChapter(Ref.rdUsx(refString).toRefKey()) }
  abstract fun hasChapter (refKey: RefKey): Boolean

  fun hasVerse (ref: Ref):          Boolean { return hasVerse(ref.toRefKey()) }
  fun hasVerse (refString: String): Boolean { return hasVerse(Ref.rdUsx(refString).toRefKey()) }
  fun hasVerse (refKey: RefKey):    Boolean { return hasVerseAsVerse(refKey) || hasVerseAsSubverses(refKey) }

  fun hasVerseAsVerse (ref: Ref):          Boolean { return hasVerseAsVerse(ref.toRefKey()) }
  fun hasVerseAsVerse (refString: String): Boolean { return hasVerseAsVerse(Ref.rdUsx(refString).toRefKey()) }
  abstract fun hasVerseAsVerse (refKey: RefKey): Boolean

  fun hasVerseAsSubverses (ref: Ref):          Boolean { return hasVerseAsSubverses(ref.toRefKey()) }
  fun hasVerseAsSubverses (refString: String): Boolean { return hasVerseAsSubverses(Ref.rdUsx(refString).toRefKey()) }
  abstract fun hasVerseAsSubverses (refKey: RefKey): Boolean

  fun hasVerseAsVerseOrSubverses (ref: Ref):          Boolean { return hasVerseAsVerseOrSubverses(ref.toRefKey()) }
  fun hasVerseAsVerseOrSubverses (refString: String): Boolean { return hasVerseAsVerseOrSubverses(Ref.rdUsx(refString).toRefKey()) }
  abstract fun hasVerseAsVerseOrSubverses (refKey: RefKey): Boolean

  fun hasSubverse (ref: Ref):          Boolean { return hasSubverse(ref.toRefKey()) }
  fun hasSubverse (refString: String): Boolean { return hasSubverse(Ref.rdUsx(refString).toRefKey()) }
  abstract fun hasSubverse (refKey: RefKey): Boolean

  fun hasElementAsRequested (ref: Ref):          Boolean { return hasElementAsRequested(ref.toRefKey()) }
  fun hasElementAsRequested (refString: String): Boolean { return hasElementAsRequested(Ref.rdUsx(refString).toRefKey()) }
  fun hasElementAsRequested (refKey: RefKey): Boolean
  {
    if (!Ref.hasC(refKey))
      return hasBook(refKey)
    else if (!Ref.hasV(refKey))
      return hasChapter(refKey)
    else if (!Ref.hasS(refKey))
      return hasVerse(refKey)
    else
      return hasSubverse(refKey)
  }


  /****************************************************************************/
  /**
   * Returns an indication of whether we have any books from the deuterocanon.
   * 
   * @return True if we have books from the deuterocanon.
   */
  
  fun hasDc (): Boolean
  {
    return m_HasDc
  }
  
  
  /****************************************************************************/
  /**
   * Returns an indication of whether we have any books from the New Testament.
   * 
   * @return True if we have books from the New Testament.
   */
  
  fun hasNt (): Boolean
  {
    return m_HasNt
  }
  
  
  /****************************************************************************/
  /**
   * Returns an indication of whether we have any books from the Old Testament.
   * 
   * @return True if we have books from the Old Testament.
   */
  
  fun hasOt (): Boolean
  {
    return m_HasOt
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Protected                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected fun setDc () { m_HasDc = true }
  protected fun setNt () { m_HasNt = true }
  protected fun setOt () { m_HasOt = true }


  /****************************************************************************/
  var m_HasDc: Boolean = false
  var m_HasNt: Boolean = false
  var m_HasOt: Boolean = false


  /****************************************************************************/
  var m_Scheme: String


  /****************************************************************************/
  init
  {
    //m_Scheme = scheme
  }
}
