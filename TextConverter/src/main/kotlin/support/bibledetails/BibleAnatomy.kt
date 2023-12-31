/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.shared.BiblePart
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey


/******************************************************************************/
/**
 * Basic information about the anatomy of the Bible in general, based upon
 * UBS book numbers.
 * <p>
 *
 * @author ARA "Jamie" Jamieson
*/

object BibleAnatomy
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  val C_PsalmsWithCanonicalHeaders = setOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 97, 98, 99, 100, 101, 102, 103, 107, 108, 109, 110, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 137, 138, 139, 140, 141, 142, 143, 144, 145)
  val C_AbbreviatedNamesOfBooksWhichMayHaveCanonicalHeaders = setOf("Psa", "Hab")

  
  /****************************************************************************/
  /** @return UBS book number. */ fun getBookNumberForStartOfOt (): Int { return m_OtStart }
  /** @return UBS book number. */ fun getBookNumberForEndOfOt   (): Int { return m_OtEnd }
  /** @return UBS book number. */ fun getBookNumberForStartOfNt (): Int { return m_NtStart }
  /** @return UBS book number. */ fun getBookNumberForEndOfNt   (): Int { return m_NtEnd }
  /** @return UBS book number. */ fun getBookNumberForStartOfDc (): Int { return m_DcStart }
  /** @return UBS book number. */ fun getBookNumberForEndOfDc   (): Int { return m_DcEnd }
  
  /** @return UBS book number. */ fun getBookNumberForStartOfGospels (): Int { return m_GospelsStart }
  /** @return UBS book number. */ fun getBookNumberForEndOfGospels   (): Int { return m_GospelsEnd }
  
  /** @return UBS book number. */ fun getBookNumberForStartOfEpistles (): Int { return m_EpistlesStart }
  /** @return UBS book number. */ fun getBookNumberForEndOfEpistles   (): Int  { return m_EpistlesEnd }
  
  /** @return Number of books. */ fun getNumberOfBooksInNt (): Int { return getBookNumberForEndOfNt() - getBookNumberForStartOfNt() + 1 }
  /** @return Number of books. */ fun getNumberOfBooksInOt (): Int { return getBookNumberForEndOfOt() - getBookNumberForStartOfOt() + 1 }
  
  /** @param bookNumber UBS book number
   * @return True if condition satisfied. */
  fun isDc (bookNumber: Int): Boolean { return !isNt(bookNumber) && !isOt(bookNumber) }

  /** @param bookNumber UBS book number
   * @return True if condition satisfied. */
  fun isNt (bookNumber: Int): Boolean { return getBookNumberForStartOfNt() <= bookNumber && bookNumber <= getBookNumberForEndOfNt() }
  
  /** @param bookNumber UBS book number
   * @return True if condition satisfied. */
 fun isOt (bookNumber: Int): Boolean { return getBookNumberForStartOfOt() <= bookNumber && bookNumber <= getBookNumberForEndOfOt() }



  /****************************************************************************/
  /**
   * Returns an indication of the portion of the Bible within which a given
   * book sits.
   * 
   * @param bookNumber UBS book number.
   * @return OT / NT / DC or null if not known.
   */
  
  fun getBookCollection (bookNumber: Int): BiblePart
  {
    if (isOt(bookNumber)) return BiblePart.OT
    if (isNt(bookNumber)) return BiblePart.NT
    return BiblePart.DC
  }


  /****************************************************************************/
  /**
  * Returns a list of all verses which are missing in many texts, and therefore
  * perhaps not worth reporting.
  *
  * @return Commonly missing verses.
  */

  fun getCommonlyMissingVerses (): List<RefKey>
  {
    return m_CommonlyMissingVerses
  }


  /****************************************************************************/
  /**
   * Checks to see if a given verse is in the set of those which are commonly
   * missing.
   *
   * @param refAsUsxString What it says on the tin.
   *
   * @return True if verse is in list.
   */

  fun isCommonlyMissingVerse (refAsUsxString: String): Boolean
  {
      return isCommonlyMissingVerse(Ref.rdUsx(refAsUsxString).toRefKey_bcv())
  }


  /****************************************************************************/
  /**
   * Checks to see if a given verse is in the set of those which are commonly
   * missing.
   *
   * @param refKey RefKey for verse.
   *
   * @return True if verse is in list.
   */

  fun isCommonlyMissingVerse (refKey: Long): Boolean
  {
        return m_CommonlyMissingVerses.contains(refKey)
  }


  /****************************************************************************/
  /**
   * Returns an indication of whether a given USX reference contains a single
   * chapter book.
   * 
   * @param bookNumber
   * @return 
   */
  
  fun isSingleChapterBook (bookNumber: Int): Boolean
  {
    return isSingleChapterBook(BibleBookNamesUsx.numberToAbbreviatedName(bookNumber))
  }


  /****************************************************************************/
  /**
   * Returns an indication of whether a given USX reference contains a single
   * chapter book.
   *
   * @param usxReference
   * @return
   */

  fun isSingleChapterBook (usxReference: String): Boolean
  {
    return ".oba.phm.2jn.3jn.jud.s3y.sus.bel.man.blt.".contains(usxReference.split(" ")[0].lowercase())
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val m_CommonlyMissingVerses =
      "MAT 17:21¬MAT 18:11¬MAT 23:14¬MRK 7:16¬MRK 9:44¬MRK 9:46¬MRK 11:26¬MRK 15:28¬LUK 17:36¬JHN 5:4¬ACT 8:37¬ACT 15:34¬ACT 24:7¬ACT 28:29¬ROM 16:24".split("¬")
          .map { Ref.rdUsx(it).toRefKey() }

  private var m_OtStart = 0
  private var m_OtEnd = 0
  private var m_NtStart = 0
  private var m_NtEnd = 0
  private var m_GospelsStart = 0
  private var m_GospelsEnd = 0
  private var m_EpistlesStart = 0
  private var m_EpistlesEnd = 0
  private var m_DcStart = 0
  private var m_DcEnd = 0
    
    
  /****************************************************************************/
  init
  {
    m_OtStart        = BibleBookNamesUsx.abbreviatedNameToNumber("Gen")
    m_OtEnd          = BibleBookNamesUsx.abbreviatedNameToNumber("Mal")
    m_NtStart        = BibleBookNamesUsx.abbreviatedNameToNumber("Mat")
    m_NtEnd          = BibleBookNamesUsx.abbreviatedNameToNumber("Rev")
    m_GospelsStart   = BibleBookNamesUsx.abbreviatedNameToNumber("Mat")
    m_GospelsEnd     = BibleBookNamesUsx.abbreviatedNameToNumber("Jhn")
    m_EpistlesStart  = BibleBookNamesUsx.abbreviatedNameToNumber("Rom")
    m_EpistlesEnd    = BibleBookNamesUsx.abbreviatedNameToNumber("Jud")
    m_DcStart = BibleBookNamesUsx.abbreviatedNameToNumber("Tob")
    m_DcEnd   = BibleBookNamesUsx.abbreviatedNameToNumber("6Ez") // Or should it be Dag?  Dag exists in UBS, but not in things like NRSVA.
  }
}
