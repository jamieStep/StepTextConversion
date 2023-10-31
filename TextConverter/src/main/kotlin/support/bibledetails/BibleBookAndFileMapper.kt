/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.w3c.dom.Document
import java.io.File


/******************************************************************************/
/**
 * Relates book names / numbers and files for the text as supplied.
 *
 * This is a singleton, populated from a given folder via the 'populate'
 * method.  ('populate' can be called more than once; on second and subsequent
 * calls it replaces the data which was already there.)
 *
 * It provides only very basic information about the text in the given folder,
 * reflecting the books which appear in that text, but not containing any
 * information at a lower level than this.  If you want information about
 * chapters and verses etc, you need to have recourse to the various
 * BibleStructure_* classes.
 *
 * (The reason for splitting this functionality is that it may be useful to
 * know which books are available, to be able to iterate over them in book
 * order etc; and it may be useful to do this ahead of doing anything else.)
 *
 * **IMPORTANT:** The class examines *all* of the files in the
 * specified folder when populating the internal data structures.
 *
 * @author ARA "Jamie" Jamieson
*/

open class BibleBookAndFileMapper
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  /**
   * Links the abbreviated name of a book to the file containing it.  Intended
   * mainly for reversification, where it may be necessary to create new DC
   * books on the fly.
   * 
   * @param filePath
   * @param ubsAbbreviation 
   */
  
  fun addBookDetails (filePath: String, ubsAbbreviation: String)
  {
    addBookDetails(filePath, BibleBookNamesUsx.abbreviatedNameToNumber(ubsAbbreviation))
  }
  
  
  /****************************************************************************/
  /**
   * Returns a list of all available book names ordered by UBS book number.
   * 
   * @return List of abbreviated names for available books.
   */
  
  fun getAbbreviatedBookNamesInOrder (): List<String>
  {
    return getBookNumbersInOrder().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }


  /****************************************************************************/
  /**
   * Returns the book number associated with a given file, or -1 if not
   * found.
   *
   * @param filePath
   * @return Book number.
   */

  fun getBookNumberFromFile (filePath: String): Int
  {
    return m_BookOrderingDetailsByFilePath[filePath] ?: -1
  }


  /****************************************************************************/
  /**
   * Returns a list of all available book numbers ordered by UBS book number.
   * 
   * @return List of available book numbers.
   */
  
  fun getBookNumbersInOrder (): List<Int>
  {
    return m_BookOrderingDetailsByBookNumber.keys.filter { null != m_BookOrderingDetailsByBookNumber[it] }
  }
  
  
  /****************************************************************************/
  /**
   * Returns an ordered list of book names in the portion of the Bible implied
   * by the name of the method.
   * 
   * @return List of UBS abbreviations for books.
   */
  
  fun getBooksDc (): List<String>
  {
    return getBookNumbersInOrder()
               .filter{ BibleAnatomy.isDc(it) }
               .map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }
  
  
  /****************************************************************************/
  /**
   * Returns an ordered list of book names in the portion of the Bible implied
   * by the name of the method.
   * 
   * @return List of UBS abbreviations for books.
   */
  
  fun getBooksNt (): List<String>
  {
    return getBookNumbersInOrder()
      .filter{ BibleAnatomy.isNt(it) }
      .map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }
  
  
  /****************************************************************************/
  /**
   * Returns an ordered list of book names in the portion of the Bible implied
   * by the name of the method.
   * 
   * @return List of UBS abbreviations for books.
   */
  
  fun getBooksOt (): List<String>
  {
    return getBookNumbersInOrder()
      .filter{ BibleAnatomy.isOt(it) }
      .map{ BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }
  
  
  /****************************************************************************/
  /**
   * Returns the path to the USX file for the book.
   * 
   * @param bookNumber Book number.
   * @return File path or null if not appropriate for this run.
   */
  
  fun getFilePathForBook (bookNumber: Int): String?
  {
    return getFilePathForBook(BibleBookNamesUsx.numberToAbbreviatedName(bookNumber))
  }
  
  
  /****************************************************************************/
  /**
   * Returns the path to the USX file for the book.
   *
   * @param bookName UBS abbreviation for book.
   * @return File path or null if not appropriate for this run.
   */

  fun getFilePathForBook (bookName: String): String?
  {
    return m_BookOrderingDetailsByBookNumber[BibleBookNamesUsx.nameToNumber(bookName)]?.filePath ?: return null
  }


  /****************************************************************************/
  fun hasOt (): Boolean { return m_BookOrderingDetailsByBookNumber.keys.filter { null != m_BookOrderingDetailsByBookNumber[it] } .any { BibleAnatomy.isOt(it) } }
  fun hasNt (): Boolean { return m_BookOrderingDetailsByBookNumber.keys.filter { null != m_BookOrderingDetailsByBookNumber[it] } .any { BibleAnatomy.isNt(it) } }
  fun hasDc (): Boolean { return m_BookOrderingDetailsByBookNumber.keys.filter { null != m_BookOrderingDetailsByBookNumber[it] } .any { BibleAnatomy.isDc(it) } }

  fun hasFullOt (): Boolean { return BibleAnatomy.getNumberOfBooksInOt() == m_BookOrderingDetailsByBookNumber.keys.filter { null != m_BookOrderingDetailsByBookNumber[it] } .filter { BibleAnatomy.isOt(it) } .size }
  fun hasFullNt (): Boolean { return BibleAnatomy.getNumberOfBooksInNt() == m_BookOrderingDetailsByBookNumber.keys.filter { null != m_BookOrderingDetailsByBookNumber[it] } .filter { BibleAnatomy.isNt(it) } .size }


  /****************************************************************************/
  /**
   * These various methods should be called only via a derived class (of which
   * there is one for raw files and one for enhanced files).  They iterate over
   * all files (or all selected files when doing a debug run), passing to the
   * supplied function either the path to the file, or the path plus a pre-read
   * DOM.  Calls to the supplied function are ordered by book order.
   * 
   * @param fn Function to apply to each file.
   */
  
  fun iterateOverAllFiles      (fn: (bookName: String, filePath: String)               -> Unit) { iterateOverFiles(fn, false) }
  fun iterateOverAllFiles      (fn: (bookName: String, filePath: String, dom:Document) -> Unit) { iterateOverFiles(fn, false) }
  fun iterateOverSelectedFiles (fn: (bookName: String, filePath: String)               -> Unit) { iterateOverFiles(fn, true ) }
  fun iterateOverSelectedFiles (fn: (bookName: String, filePath: String, dom:Document) -> Unit) { iterateOverFiles(fn, true ) }






  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Protected / Private                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  //protected fun populate () {}


  /****************************************************************************/
  /**
   * Empties out the existing structures and populates from the files in the
   * given folder.
   *
   * @param preferredFolderPath Path of folder containing files to be processed.
   * @param otherFolderPath Things are populated from preferredFolderPath
   *        first, but if any book is not found there, we look for it in
   *        otherFolderPath.
   * @throws Exception Any old exception.
   */

  protected fun populate (preferredFolderPath: String, otherFolderPath: String? = null)
  {
    /**************************************************************************/
    val done: MutableSet<Int> = HashSet()
    fun handleFile (filePath: String)
    {
      val bookNo = MiscellaneousUtils.getBookNumberFromFile(filePath)
      if (done.contains(bookNo)) return
      done.add(bookNo)
      addBookDetails(filePath, bookNo)
    }



    /**************************************************************************/
    reset()
    try
    {
      val filePattern = StandardFileLocations.getRawUsxFilePattern(null)
      if (File(preferredFolderPath).exists()) StepFileUtils.iterateOverFilesInFolder(preferredFolderPath, filePattern,::handleFile, null)
      if (null != otherFolderPath && File(otherFolderPath).exists()) StepFileUtils.iterateOverFilesInFolder(otherFolderPath, filePattern, ::handleFile, null)
    }
    catch (e: Exception)
    {
      throw StepException(e)
    }
  }


  /****************************************************************************/
  private fun addBookDetails (filePath: String, bookNo: Int)
  {
    m_BookOrderingDetailsByBookNumber[bookNo] = BookDetails(filePath, bookNo)
    m_BookOrderingDetailsByFilePath[filePath] = bookNo
  }
  
  
  /****************************************************************************/
  /* 24-Oct-23: Changes to return book name as well as file path. */

  private fun getFileList (limitToSelectedBooks: Boolean): List<Pair<String, String>>
  {
    val limitationFilter: (bookNumber: Int) -> Boolean = if (limitToSelectedBooks) { {  x -> Dbg.wantToProcessBook(x) } } else { { _ -> true } }
    return m_BookOrderingDetailsByBookNumber
      .keys
      .asSequence()
      .sorted()
      .filter { null != m_BookOrderingDetailsByBookNumber[it] }
      .filter { limitationFilter.invoke(m_BookOrderingDetailsByBookNumber[it]!!.bookNumber) }
      .filter { File(m_BookOrderingDetailsByBookNumber[it]!!.filePath).exists() } // Particularly with reversification, books which originally existed may have been emptied and deleted.
      .map { Pair(BibleBookNamesUsx.numberToAbbreviatedName(m_BookOrderingDetailsByBookNumber[it]!!.bookNumber), m_BookOrderingDetailsByBookNumber[it]!!.filePath) }
      .toList()
  }


  /****************************************************************************/
  private fun iterateOverFiles (fn: (bookName: String, filePath: String) -> Unit, limitToSelectedBooks: Boolean)
  {
    getFileList(limitToSelectedBooks).forEach { fn(it.first, it.second) }
  }


  /****************************************************************************/
  private fun iterateOverFiles (fn: (bookName: String, filePath: String, document: Document) -> Unit, limitToSelectedBooks: Boolean)
  {
    getFileList(limitToSelectedBooks).forEach { fn(it.first, it.second, Dom.getDocument(it.second)) }
  }
  
  
  /****************************************************************************/
  private fun reset ()
  {
    m_BookOrderingDetailsByBookNumber.clear()

    for (i in BibleAnatomy.getBookNumberForStartOfOt() .. BibleAnatomy.getBookNumberForEndOfOt())
    m_BookOrderingDetailsByBookNumber[i] = null

    for (i in BibleAnatomy.getBookNumberForStartOfNt() .. BibleAnatomy.getBookNumberForEndOfNt())
      m_BookOrderingDetailsByBookNumber[i] = null

    for (i in BibleAnatomy.getBookNumberForStartOfDc() .. BibleAnatomy.getBookNumberForEndOfDc())
      m_BookOrderingDetailsByBookNumber[i] = null
  }


  /****************************************************************************/
  private data class BookDetails (val filePath: String, val bookNumber: Int)
  private val m_BookOrderingDetailsByBookNumber: MutableMap<Int, BookDetails?> = LinkedHashMap()
  private val m_BookOrderingDetailsByFilePath  : MutableMap<String, Int > = HashMap()
}
