/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.w3c.dom.Document
import java.io.File


/******************************************************************************/
/**
 * This class does two vaguely related things (or one of the two it does itself,
 * and the other it delegates).
 *
 * What it does itself it to manage mappings between book names and the USX
 * files which contain them.  What it delegates (to [BibleStructure]) is the
 * job of maintaining details of the chapter / verse structure.  If you use
 * the present class, you always get the former functionality.  You get the
 * detailed functionality only if you ask for it, using
 * [populateVerseStructure].
 *
 * Note that populateVerseStructure is fairly memory-intensive.  If you're
 * paranoid about such matters, you can call [clearVerseStructure] to clear
 * it out.  This doesn't clear the book / file mappings, though.  If you're
 * *really* paranoid, calling [reset] clears everything -- detailed information
 * and name / file mappings.
 *
 * You can't instantiate this directly -- only via derived classes.  I supply
 * several of these, and you need to take care to use the right one for the
 * right purpose:
 *
 * - [TextStructureBooksOnlyForPreprocessingRawInput]: Looks only at InputUsx.  Can
 *   be used only if that folder is indeed in use; can be initialised as soon
 *   as that fact has been established.
 *
 * - [TextStructureUsxForUseWhenAnalysingInput]: Looks at InputUsx if available.
 *   Otherwise, if InputVl exists, it looks at UsxA (the folder in which
 *   generated USX is placed).  Can be used only if one or other of InputUsx and
 *   InputVl exists.  Can be initialised once you have checked that InputUsx
 *   exists, or as soon as you have created USX from the VL.
 *
 * - [TextStructureUsxForUseWhenConvertingToEnhancedUsx]: Looks at UsxA if
 *   processing started from VL, or if processing started from USX and
 *   pre-processing has been applied.  Can be used only if one of those two
 *   folders exists.  Can be initialised once VL has been
 *   converted to USX, or once any pre-processing is complete.
 *
 *
 * @author ARA "Jamie" Jamieson
*/

open class TextStructure
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
  * Populates the chapter / verse structure for the files represented by this
  * class.  Note that you cannot call this until after you have called
  * populateBookAndFileMappings.
  *
  * @param prompt Output to screen as part of progress indicator.
  */

  fun populateVerseStructure (prompt: String)
  {
    m_BibleStructure.populateFromBookAndFileMapper(prompt = prompt, this)
  }


  /****************************************************************************/
  /**
  * Clears out the verse structure data so as to free up memory.
  */

  fun clearVerseStructure ()
  {
    m_BibleStructure = BibleStructureUsx()
  }


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
  * Returns the underlying Bible structure.
  */

  fun getBibleStructure () : BibleStructureUsx
  {
    if (!m_BibleStructure.isPopulated())
      throw StepException("Accessed Bible structure information without first populating it.")
    return m_BibleStructure
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
   * Returns a list of all file paths, in Bible-book order.
   *
   * @param limitToSelectedBooks If true, output is limited to the list
   *   selected by Dbg.
   * @return List of Pairs (bookName, path)
   */

  fun getFileList (limitToSelectedBooks: Boolean): List<Pair<String, String>>
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
  /* Forcibly empties and repopulates the structure. */

  open fun repopulate () { throw StepException("Base class implementation of repopulate exposed.")}




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Protected / Private                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  protected constructor () // Don't instantiate other then via derived classes.



  /****************************************************************************/
  /**
   * Empties out the existing structures and populates from the files in the
   * given folder.
   *
   * @param folderPath Path of folder containing files to be processed.
   * @throws Exception Any old exception.
   */

  protected fun populateBookAndFileMappings (folderPath: String)
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
      val filePattern = "(?i).*\\.${FileLocations.getFileExtensionForUsx()}".toRegex()
      if (File(folderPath).exists()) StepFileUtils.iterateOverFilesInFolder(folderPath, filePattern, ::handleFile, null)
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
  /**
  * Empties the data structures.  Useful mainly to ensure things can be
  * repopulated reliably as necessary.
  */

  protected fun reset ()
  {
    m_BookOrderingDetailsByBookNumber.clear()

    for (i in BibleAnatomy.getBookNumberForStartOfOt() .. BibleAnatomy.getBookNumberForEndOfOt())
    m_BookOrderingDetailsByBookNumber[i] = null

    for (i in BibleAnatomy.getBookNumberForStartOfNt() .. BibleAnatomy.getBookNumberForEndOfNt())
      m_BookOrderingDetailsByBookNumber[i] = null

    for (i in BibleAnatomy.getBookNumberForStartOfDc() .. BibleAnatomy.getBookNumberForEndOfDc())
      m_BookOrderingDetailsByBookNumber[i] = null

    m_BibleStructure = BibleStructureUsx()
  }


  /****************************************************************************/
  private data class BookDetails (val filePath: String, val bookNumber: Int)
  private val m_BookOrderingDetailsByBookNumber: MutableMap<Int, BookDetails?> = LinkedHashMap()
  private val m_BookOrderingDetailsByFilePath  : MutableMap<String, Int > = HashMap()
  private var m_BibleStructure = BibleStructureUsx()
}


/******************************************************************************/
/**
 * A verse of BibleBookAndFileMapper which handles files stored in the UsxB
 * folder.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextStructureEnhancedUsx: TextStructure()
{
  /****************************************************************************/
  /* Forcibly empties and repopulates the structure. */

  override fun repopulate ()
  {
    reset()
    val inFolder = FileLocations.getInternalUsxBFolderPath()
    populateBookAndFileMappings(inFolder)
    populateVerseStructure("Enhanced USX")
  }


  /****************************************************************************/
  init
  {
    repopulate()
  }
}




/******************************************************************************/
/**
 * A version of BibleBookAndFileMapper which looks at the earliest USX
 * available.
 *
 * You shouldn't make use of this unless you know you have InputUsx.  If you
 * do know that, then you can initialise this as early as you like.
 *
 * @author ARA "Jamie" Jamieson
*/

object TextStructureBooksOnlyForPreprocessingRawInput: TextStructure()
{
  /****************************************************************************/
  /* Forcibly empties and repopulates the structure. */

  override fun repopulate ()
  {
    reset()
    val inFolder = FileLocations.getInputUsxFolderPath()
    populateBookAndFileMappings(inFolder)
  }


  /****************************************************************************/
  init
  {
    repopulate()
  }
}




/******************************************************************************/
/**
 * A version of BibleBookAndFileMapper which looks at the earliest USX
 * available.
 *
 * If we have InputUsx, that it what will be used.  If we have InputVl, it looks
 * at UsxA, which will have been populated from InputVl.
 *
 * Do not initialise this until any VL processing is complete.  Having
 * initialised it at that point, you shouldn't need to repopulate it.
 *
 * @author ARA "Jamie" Jamieson
*/

object TextStructureUsxForUseWhenAnalysingInput: TextStructure()
{
  /****************************************************************************/
  /* Forcibly empties and repopulates the structure. */

  override fun repopulate ()
  {
    reset()
    val inFolder =
      if (StepFileUtils.fileOrFolderExists(FileLocations.getInputUsxFolderPath()))
        FileLocations.getInputUsxFolderPath()
      else if (StepFileUtils.fileOrFolderExists(FileLocations.getInputVlFolderPath()))
        FileLocations.getInternalUsxAFolderPath()
      else
        throw StepException("BibleBookAndFileMapperInputUsx -- no input folder")

    if (StepFileUtils.folderIsEmpty(inFolder))
      throw StepException("BibleBookAndFileMapperInputUsx -- input folder is empty")

    populateBookAndFileMappings(inFolder)
    populateVerseStructure("Analysing input USX")
  }


  /****************************************************************************/
  init
  {
    repopulate()
  }
}




/******************************************************************************/
/**
 * Supplies details of the files which are to be converted to enhanced USX.
 *
 * These will come from the InputUsx folder if it is available and is not
 * subject to pre-processing.  Otherwise (if the data *is* subject to
 * pre-processing or if we are starting from VL), they come from UsxA.
 *
 * Important: Do not initialise this class until after any pre-processing or
 * conversion from VL.  After that, you should not need to reinitialise it.
 *
 * @author ARA "Jamie" Jamieson
*/

object TextStructureUsxForUseWhenConvertingToEnhancedUsx: TextStructure()
{
  /****************************************************************************/
  /* Forcibly empties and repopulates the structure. */

  override fun repopulate ()
  {
    reset()
    val inputUsxFileDate = StepFileUtils.getLatestFileDate(FileLocations.getInputUsxFolderPath(), "usx")
    val internalUsxAFileDate = StepFileUtils.getLatestFileDate(FileLocations.getInternalUsxAFolderPath(), "usx")
    val inFolder = if (internalUsxAFileDate > inputUsxFileDate) FileLocations.getInternalUsxAFolderPath() else FileLocations.getInputUsxFolderPath()
    populateBookAndFileMappings(inFolder)
  }


  /****************************************************************************/
  init
  {
    repopulate()
  }
}