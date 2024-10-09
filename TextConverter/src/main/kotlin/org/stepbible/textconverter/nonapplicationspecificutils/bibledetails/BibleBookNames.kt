/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.bibledetails

import java.util.TreeMap
import org.stepbible.textconverter.nonapplicationspecificutils.shared.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun


/******************************************************************************/
/**
 * Definitive list of book names and numbers, and the wherewithal to map
 * between them.
 * 
 * @author ARA "Jamie" Jamieson
*/

open class BibleBookNames
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Companion                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  companion object {
    /**************************************************************************/
    /**
    * Gets the UBS book number corresponding to a given name, by hook or by
    * crook.  Caters for the possibility that the name may be abbreviated,
    * short or long, and that it may be in USX form, OSIS form or vernacular
    * form.
    *
    * @param name
    * @return Book number, or zero.
    */

    operator fun get (name: String): Int
    {
      var res = BibleBookNamesUsx.nameToNumber(name)
      if (0 == res) res = BibleBookNamesOsis.nameToNumber(name)
      if (0 == res) res = BibleBookNamesTextAsSupplied.nameToNumber(name)
      return res
    }
  }






  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Takes a book abbreviation which is ok as per the USX (or OSIS) standard
   * and checks whether we can actually process that book.  (We cannot if there
   * is no provision for the book in the Crosswire header files.)  This will be
   * an issue only for DC books.
   *
   * @param abbreviatedName
   * @return True if book is supported.
   */

  fun bookIsSupported (abbreviatedName: String) = "\u0001" != m_BooksInOrder[m_AbbreviatedNameToIndex[abbreviatedName]!!].shortName


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
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected fun addBookDescriptor (ubsBookNo: Int, abbreviatedName: String, shortName: String = "", longName: String = "")
  {
    for (i in m_BooksInOrder.size .. ubsBookNo) m_BooksInOrder.add(i, BookDescriptor("", "", ""))
    m_BooksInOrder[ubsBookNo] = BookDescriptor(abbreviatedName, shortName, longName)
    m_AbbreviatedNameToIndex[abbreviatedName] = ubsBookNo
    m_ShortNameToIndex[shortName] = ubsBookNo
    m_LongNameToIndex[longName] = ubsBookNo
  }




  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Marker used to indicate books which, although valid in USX, are not
     supported by the Crosswire header files, and therefore cannot be handled
     here. */

  internal val C_NotSupported = "\u0001"


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
    throw StepExceptionWithStackTraceAbandonRun("Unknown $m_BookCollectionName name: $name")
  }
  
  
  /****************************************************************************/
  open var m_BookCollectionName = ""
  private val m_BooksInOrder: MutableList<BookDescriptor> = ArrayList()
  private val m_AbbreviatedNameToIndex: MutableMap<String, Int> = TreeMap(String.CASE_INSENSITIVE_ORDER)
  private val m_ShortNameToIndex:       MutableMap<String, Int> = TreeMap(String.CASE_INSENSITIVE_ORDER)
  private val m_LongNameToIndex:        MutableMap<String, Int> = TreeMap(String.CASE_INSENSITIVE_ORDER)
}

