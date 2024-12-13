/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.bibledetails

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.shared.BookNameLength
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.util.*
import kotlin.collections.ArrayList


/******************************************************************************/
/**
 * This class handles initialisation of each of the different standard book
 * collections (Crosswire header files, USX and OSIS), and also makes provision
 * for the handling of the vernacular details.
 *
 * <span class='important'>The [init] method of the companion object needs to
 * be called very early, because it provides information needed by the Dbg
 * class to determine whether a particular run is being limited to a subset of
 * books only.
 *
 * Book numbering is based upon the UBS book numbering scheme.  The actual books
 * supported are limited by what the Crosswire header files support.  More
 * information appears in the file bookNames.tsv in the resources section of
 * this JAR file.
 *
 * The companion object handles initialisation.  The remaining functionality
 * in the base class is concerned with accessing the above data.  There is then
 * a separate derived class corresponding to each collection (USX, OSIS, etc).
 *
 *
 * @author ARA "Jamie" Jamieson
 */

open class BibleBookNames
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Companion                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  companion object
  {
    /**************************************************************************/
    /**
    * Debug support -- returns the preferred abbreviated names of all books
    * supported.
    *
    * @return List of abbreviated names of books.
    */

    fun dbgGetBookAbbreviations () = DbgWantToProcessBook.indices.map { BibleBookNamesUsx.numberToAbbreviatedName(it) }


    /**************************************************************************/
    /**
    * Debug support.  Lets you indicate whether a particular book is to be
    * processed or not.
    *
    * @param ix Index into book structure.
    * @param setting True or false, according as the book is or is not to be
    *   processed.
    */

    fun dbgSetProcessBook (ix: Int, setting: Boolean) { DbgWantToProcessBook[ix] = setting }


    /**************************************************************************/
    /**
    * Returns an indication of whether a particular book is to be processed
    * or not.
    *
    * @param bookNo
    * @return True or false
    */

    fun dbgWantToProcessBook (bookNo: Int) = DbgWantToProcessBook[bookNo]


    /**************************************************************************/
    /**
    * Returns an indication of whether a particular book is to be processed
    * or not.
    *
    * @param abbreviatedName
    * @return True or false
    */

    fun dbgWantToProcessBook (abbreviatedName: String) = DbgWantToProcessBook[BibleBookNamesUsx.abbreviatedNameToNumber(abbreviatedName)]


    /**************************************************************************/
    /**
    * Returns the total number of books supported.  Or more accurately, the
    * number of slots in the arrays used to hold book details -- some of these
    * may be empty.
    *
    * @return Number of books supported.
    */

    fun getNumberOfBooksSupported () = DbgWantToProcessBook.size


    /**************************************************************************/
    /**
    * Initialises the various data structures.
    */

    fun init ()
    {
      StepFileUtils.readDelimitedTextStream(FileLocations.getInputStream(FileLocations.getBookNamesFilePath())!!).forEach {
        addDetails(it)
      }

      for (i in BibleBookNamesOsis.getBookDescriptors().indices)
        DbgWantToProcessBook.add(true) // Until we know otherwise, say we want to process all books.
    }


    /**************************************************************************/
    private fun addDetails (fields: List<String>)
    {
      val elts = fields.toMutableList()

      val ubsBookNo = elts[0].toInt()

      if ("~" == elts[3]) // ~ as USX abbreviation means we just copy the OSIS abbreviation.
        elts[3] = elts[1]

      if (elts[3].startsWith("~")) // USX abbreviation starting with ~: this was to mark a potential issue.  Pro tem, I simply drop the ~.
        elts[3] = elts[3].substring(1)

      if ("~" == elts[4]) // ~ as USX short name means we just copy the OSIS abbreviation.
        elts[4] = elts[2]

      var ix = -1
      for (collection in listOf(BibleBookNamesOsis, BibleBookNamesUsx, BibleBookNamesImp, BibleBookNamesOsis2modJsonFile))
      {
        ix += 2  // The first set of associated information is entries 1 & 2; then 3 & 4 etc.
        collection.addBookDescriptor(ubsBookNo, elts[ix], elts[ix + 1], elts[ix + 1])
      }
    }


    val DbgWantToProcessBook = mutableListOf<Boolean>()
  } // companion





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Adds a descriptor to the list of books about which we know.
   *
   * For standard schemes -- those defined by USX, OSIS and the Crosswire
   * header files -- we can expect to call this method for each acceptable
   * book, in order.
   *
   * For the vernacular scheme, this is not necessarily the case, so I make
   * provision to receive stuff out of order.
   *
   * @param ubsBookNo What it says on the tin.
   * @param abbreviatedName
   * @param shortName
   * @param longName
   *
   */

  fun addBookDescriptor (ubsBookNo: Int, abbreviatedName: String, shortName: String = "", longName: String = "")
  {
    for (i in m_BooksInOrder.size .. ubsBookNo) m_BooksInOrder.add(i, BookDescriptor("", "", ""))
    m_BooksInOrder[ubsBookNo] = BookDescriptor(abbreviatedName, shortName, longName)
    m_AbbreviatedNameToIndex[abbreviatedName] = ubsBookNo
    m_ShortNameToIndex[shortName] = ubsBookNo
    m_LongNameToIndex[longName] = ubsBookNo
  }


  /****************************************************************************/
  /**
   * Takes a name of the length implied by the name of the method, and returns
   * the corresponding UBS book number.  Throws a StepException if not found.
   *
   * @param name Book name.
   * @return UBS book number.
   */

  fun abbreviatedNameToNumber (name: String): Int { return getNumber(m_AbbreviatedNameToIndex, name) }
  fun shortNameToNumber       (name: String): Int { return getNumber(m_ShortNameToIndex,       name) }
  fun longNameToNumber        (name: String): Int { return getNumber(m_LongNameToIndex,        name) }


  /****************************************************************************/
  fun abbreviatedNameExists (abbreviatedName: String) = m_AbbreviatedNameToIndex.containsKey(abbreviatedName)


  /****************************************************************************/
  fun hasBook                (bookNo: Int)  = m_BooksInOrder[bookNo].abbreviatedName.isNotEmpty()
  fun hasBookAbbreviatedName (name: String) = name in m_AbbreviatedNameToIndex
  fun hasBookShortName       (name: String) = name in m_ShortNameToIndex
  fun hasBookLongName        (name: String) = name in m_LongNameToIndex



  /****************************************************************************/
  /**
   * Takes a name any length, and returns the corresponding UBS book number.
   * Throws a StepException if not found.  Relies upon names not being
   * ambiguous across the various indexes.
   *
   * @param name Book name.
   * @return UBS book number.
   */

  fun nameToNumber (name: String): Int
  {
    if (m_AbbreviatedNameToIndex.containsKey(name)) return getNumber(m_AbbreviatedNameToIndex, name)
    if (m_ShortNameToIndex.containsKey(name))       return getNumber(m_ShortNameToIndex,       name)
    if (m_LongNameToIndex.containsKey(name))        return getNumber(m_LongNameToIndex,        name)
    return 0
  }


  /****************************************************************************/
  /**
   * Returns an ordered list of book names of the length implied by the
   * method name.
   *
   * @return List of names.
   */

  fun getAbbreviatedNameList (): List<String> { return m_BooksInOrder.map { it.abbreviatedName } .filter { it.isNotEmpty() } }
  fun getShortNameList       (): List<String> { return m_BooksInOrder.map { it.shortName       } .filter { it.isNotEmpty() } }
  fun getLongNameList        (): List<String> { return m_BooksInOrder.map { it.longName        } .filter { it.isNotEmpty() } }
  fun getCombinedNameList    (): List<String> { return (getAbbreviatedNameList() + getShortNameList() + getLongNameList()).distinct() }


  /****************************************************************************/
  /**
   * Returns an ordered list of book names of the length implied by the
   * method name.
   *
   * @param length Required length of name.
   * @return List of names.
   */

  fun getNameList (length: BookNameLength): List<String>
  {
    return when (length)
    {
      BookNameLength.Abbreviated -> getAbbreviatedNameList()
      BookNameLength.Short       -> getShortNameList()
      else                       -> getLongNameList()
    }
  }


  /****************************************************************************/
  /**
   * Converts a UBS book number to the name of the length implied by the name of
   * the method.
   *
   * @param n Book number.
   * @return Name.
   */

  fun numberToAbbreviatedName (n: Int): String { return m_BooksInOrder[n].abbreviatedName }
  fun numberToShortName       (n: Int): String { return m_BooksInOrder[n].shortName }
  fun numberToLongName        (n: Int): String { return m_BooksInOrder[n].longName }


  /****************************************************************************/
  /**
   * Converts a UBS book number to the name of the length implied by the name of
   * the method.
   *
   * @param n Book number.
   * @param bookNameLength What it says on the tin.
   * @return Name.
   */

  fun numberToName (n: Int, bookNameLength: BookNameLength): String
  {
    /**************************************************************************/
    try
    {
      return when (bookNameLength)
      {
        BookNameLength.Abbreviated -> numberToAbbreviatedName(n)
        BookNameLength.Short       -> numberToShortName      (n)
        else                       -> numberToLongName       (n)
      }
    }



    /**************************************************************************/
    /* We don't always have vernacular book names, particularly for the
       apocrypha.  I assume here that a failure probably indicates that we
       _were_ dealing with the vernacular, and that therefore a reasonable
       fallback is to use USX instead (the alternative is to give up, and that
       seems undesirable).  Of course there's a problem if the original lookup
       was for USX, because we'll get stack overflow ... */

    catch (_: Exception)
    {
      return BibleBookNamesUsx.numberToName(n, bookNameLength)
    }
  }


  /****************************************************************************/
  /**
   * Returns a list of book descriptors.  Intended mainly (solely?) for use
   * where a particular vernacular actually follows the USX scheme, which means
   * this should probably be called only from vernacular processing, and should
   * be called on the USX derivative of the present class only.
   *
   * @return List of book descriptors.
   */

  fun getBookDescriptors (): List<ConfigData.VernacularBookDescriptor>
  {
    return m_BooksInOrder.map { ConfigData.VernacularBookDescriptor(it.abbreviatedName, it.abbreviatedName, it.shortName, it.longName) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private data class BookDescriptor (val abbreviatedName: String, val shortName: String, val longName: String)


  /****************************************************************************/
  /* Gets the index number for a given book.  I force this to be non-null,
     accepting that in fact the return value may actually be null because
     in theory we may be given a book name which is not in the lists. */

  private fun getNumber (index: MutableMap<String, Int> , name: String): Int
  {
    if (index.containsKey(name))
      return index[name]!!
    else
      throw StepExceptionWithStackTraceAbandonRun("Unknown $m_BookCollectionName name: $name")
  }


  /****************************************************************************/
  open var m_BookCollectionName = ""
  private val m_BooksInOrder: MutableList<BookDescriptor> = ArrayList()
  private val m_AbbreviatedNameToIndex: MutableMap<String, Int> = TreeMap(String.CASE_INSENSITIVE_ORDER)
  private val m_ShortNameToIndex:       MutableMap<String, Int> = TreeMap(String.CASE_INSENSITIVE_ORDER)
  private val m_LongNameToIndex:        MutableMap<String, Int> = TreeMap(String.CASE_INSENSITIVE_ORDER)
}


/******************************************************************************/
object BibleBookNamesImp: BibleBookNames()               // Used when taking IMP files as input.
object BibleBookNamesOsis: BibleBookNames()              // Used when taking OSIS files as input, and also in general processing.
object BibleBookNamesUsx: BibleBookNames()               // Used when taking USX files as input, and also because USX is widely used internally as the standard reference system.
object BibleBookNamesTextAsSupplied: BibleBookNames()    // Reflects the text actually being worked upon.
object BibleBookNamesOsis2modJsonFile: BibleBookNames()  // Used when creating the JSON file used by the STEP version of ossi2mod to give structure and mapping details.