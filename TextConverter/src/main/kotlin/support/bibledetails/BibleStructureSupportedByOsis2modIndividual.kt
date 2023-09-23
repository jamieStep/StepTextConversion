/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.debug.Dbg
import java.util.ArrayList
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey


/******************************************************************************/
/**
 * Chapter and verse structure of the Bible implied by the class name.
 *
 * This class hold processing common to all standard versification schemes.
 * At the time of writing, I have details for both NRSVA and KJVA.  The latter
 * is not used anywhere, and I do not anticipate that in fact it will be used,
 * but I'm hanging on to it just in case.
 *
 * @author ARA "Jamie" Jamieson
*/

open class BibleStructureSupportedByOsis2modIndividual : BibleStructure
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

  constructor (scheme: String) : super(scheme)


  /****************************************************************************/
  /**
  * Copy constructor.
  *
  * @param other Another BibleStructure.
  */

  constructor (other: BibleStructureSupportedByOsis2modIndividual): super(other)


  /****************************************************************************/
  /**
   * Returns a set contain the refKeys for all verses (not subverses) in the
   * Bible according to the versification scheme supported by the concrete
   * class which inherits from this one.
   *
   * @return Set of refKeys.
   */

  fun getAllVerseRefKeys (): Set<RefKey>
  {
    val res: MutableSet<RefKey> = HashSet(50_000)
    for (b in m_Bible.indices)
      res.addAll(getAllVerseRefKeys(b))
    return res
  }


  /****************************************************************************/
  /**
   * Returns a set contain the refKeys for all verses (not subverses) in the
   * Bible according to the versification scheme supported by the concrete
   * class which inherits from this one.
   *
   * @return Set of refKeys.
   */

  fun getAllVerseRefKeys (bookAbbreviation: String): Set<RefKey>
  {
    return getAllVerseRefKeys(BibleBookNamesUsx.abbreviatedNameToNumber(bookAbbreviation))
  }


  /****************************************************************************/
  /**
   * Returns a set contain the refKeys for all verses (not subverses) in the
   * Bible according to the versification scheme supported by the concrete
   * class which inherits from this one.
   *
   * @return Set of refKeys.
   */

  fun getAllVerseRefKeys (bookNumber: Int): Set<RefKey>
  {
    val res: MutableSet<RefKey> = HashSet(1000)
    if (null != m_Bible[bookNumber])
      for (c in m_Bible[bookNumber]!!.m_ChapterDescriptors.indices)
        for (v in 1 .. m_Bible[bookNumber]!!.m_ChapterDescriptors[c]!!.m_nVerses)
          res.add(Ref.rd(bookNumber, c, v, 0).toRefKey())

    return res
  }


  /****************************************************************************/
  /**
   * Returns the last verse number for a given chapter.
   *
   * @param refKey
   *
   * @return Number of last verse, or 0 if book / chapter does not exist.
   */

  override fun getLastVerseNo (refKey: Long): Int
  {
    val chapterDetails = getChapterDetails(refKey)
    return if (null == chapterDetails) 0 else return chapterDetails.m_nVerses
  }



  /****************************************************************************/
  /**
   * Does what it says on the tin.
   *
   * @param bookNo Book of interest.
   * @return Number of chapters.
   */

  override fun getLastChapterNo (bookNo: Int): Int
  {
    val bookDetails = getBookDetails(bookNo)
    return (bookDetails?.m_ChapterDescriptors?.size ?: 1) - 1
  }


  /****************************************************************************/
  /**
   * Checks to see if we have details for the given element.  Normally we will,
   * but USX supports some books which NRSVA does not, and this enables the
   * processing to check before proceeding.
   */

  override fun hasBook (bookNo: Int): Boolean { return null != getBookDetails(bookNo) }
  override fun hasChapter (refKey: Long): Boolean { return null != getChapterDetails(refKey) }
  override fun hasVerseAsVerse (refKey: Long): Boolean { return Ref.getV(refKey) <= getLastVerseNo(refKey) }
  override fun hasVerseAsSubverses (refKey: Long): Boolean { return false }
  override fun hasVerseAsVerseOrSubverses (refKey: Long): Boolean { return false }
  override fun hasSubverse (refKey: Long): Boolean { return false }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Initialisation                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Adds information to the internal data structures.
   * 
   * @param versificationScheme
   * @param ubsBookNumber
   * @param numberOfVersesInChapter 
   */
  
  fun addChapterDetails ( @Suppress("UNUSED_PARAMETER") versificationScheme: String, ubsBookNumber: Int, numberOfVersesInChapter: Array<Int>)
  {
    val bd = B()
    addLeadingEntries(m_Bible, ubsBookNumber, B())
    m_Bible[ubsBookNumber] = bd
    
    if (BibleAnatomy.isOt(ubsBookNumber))
      setOt()
    else if (BibleAnatomy.isNt(ubsBookNumber))
      setNt()
    else
      setDc()
    
    for (i in 0 until numberOfVersesInChapter.size)
    {
      val cd = C()
      addLeadingEntries(bd.m_ChapterDescriptors, i + 1, C())
      bd.m_ChapterDescriptors[i + 1] = cd
      cd.m_nVerses = numberOfVersesInChapter[i]
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun addLeadingEntries (list: MutableList<B?>, newIx: Int, dummy: B)
  {
    while (list.size <= newIx) list.add(null) // <= because we want the n'th details to occupy the n'th position, not the (n-1)'th.
  }


  /****************************************************************************/
  private fun addLeadingEntries (list: MutableList<C?>, newIx: Int, dummy: C)
  {
    while (list.size <= newIx) list.add(null) // <= because we want the n'th details to occupy the n'th position, not the (n-1)'th.
  }


  /****************************************************************************/
  private fun getBookDetails (bookNo: Int): B?
  {
    return if (bookNo >= m_Bible.size) null else m_Bible[bookNo]
  }


  /****************************************************************************/
  private fun getChapterDetails (refKey: Long): C?
  {
    val bookDetails = getBookDetails(Ref.getB(refKey)) ?: return null
    val chapterNo = Ref.getC(refKey)
    return if (chapterNo >= bookDetails.m_ChapterDescriptors.size) null else bookDetails.m_ChapterDescriptors[chapterNo]
  }


  /****************************************************************************/
  class C
  {
    var m_nVerses: Int = 0
  }


  /****************************************************************************/
  class B
  {
    var m_ChapterDescriptors: MutableList<C?> = ArrayList()
  }
  
  
  /****************************************************************************/
  var m_Bible: MutableList<B?> = ArrayList()
}
