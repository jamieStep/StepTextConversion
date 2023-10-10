/******************************************************************************/
package org.stepbible.textconverter.support.debug

import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperRawUsx
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.w3c.dom.Document
import org.w3c.dom.Node
import kotlin.system.exitProcess


/******************************************************************************/
/**
 * Controls debug.
 *
 * @author ARA "Jamie" Jamieson
*/

object Dbg
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                Debug control -- select books to process                  */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Returns a list of the books selected for processing (empty if all books
  * are to be processed).
  *
  * @return List of books to be processed.
  */

  fun getBooksToBeProcessed (): List<String>
  {
     return m_BooksToBeProcessed.filter { it.m_Process} .map { it.m_Abbrev}
  }


  /****************************************************************************/
  /**
   * Returns an indication of whether we are running on only a subset of the
   * available books.  Useful eg with cross-reference checking, because we
   * probably don't want to check that all cross-reference targets exist if we
   * are processing only a partial collection.
   * 
   * @return True if running on only a partial collection of books.
   */
  
  fun runningOnPartialCollectionOfBooksOnly (): Boolean
  {
    return m_DebugRunningOnPartialCollectionOfBooksOnly
  }
  
  
  /****************************************************************************/
  /**
   * Sets the list of books to be processed.  Give either an array of UBS
   * abbreviations, or null if all books are to be processed.
   * 
   * @param abbrevs Comma-separated list of abbreviations.
   * @param test One of <, <=, =, !=, >=, >.  If = or !=, abbrevs can contain a
   *             list; otherwise it should be only a single book and the test is
   *             based on a comparison with that book.
   */
  
  fun setBooksToBeProcessed (abbrevs: String?, test: String = "=")
  {
    /**************************************************************************/
    if (abbrevs.isNullOrEmpty())
      return
    
    
    
    /**************************************************************************/
    val booksRequested = abbrevs.uppercase().split("\\W+".toRegex()).toSet()
    m_DebugRunningOnPartialCollectionOfBooksOnly = true

    if ("!=" == test)
      m_BooksToBeProcessed.forEach{ it.m_Process = true }
    else
      m_BooksToBeProcessed.forEach{ it.m_Process = false }



    /**************************************************************************/
    when (test)
    {
      "<" -> {
        val bookNo = getBookNo(abbrevs)
        for (i in 0..< bookNo) m_BooksToBeProcessed[i].m_Process = true
      }


      "<=" -> {
        val bookNo = getBookNo(abbrevs)
        for (i in 0.. bookNo) m_BooksToBeProcessed[i].m_Process = true
      }

      
      "=" ->
      {
        m_BooksToBeProcessed.filter{ booksRequested.contains(it.m_Abbrev)}.forEach{ it.m_Process = true }
      }

      
      "!=" ->
      {
        m_BooksToBeProcessed.filter{ booksRequested.contains(it.m_Abbrev)}.forEach{ it.m_Process = false }
      }


      ">" ->
      {
        val bookNo = getBookNo(abbrevs)
        for (i in bookNo + 1..< m_BooksToBeProcessed.size) m_BooksToBeProcessed[i].m_Process = true
      }
      
      
      ">=" ->
      {
        val bookNo = getBookNo(abbrevs)
        for (i in bookNo..< m_BooksToBeProcessed.size) m_BooksToBeProcessed[i].m_Process = true
      }
    }
  }


  /****************************************************************************/
  /**
   * Used to limit the books (files) which are processed by those activities
   * which loop over a collection of books.
   * 
   * @param filePath Name of file being considered for processing.
   * @return True if file should be processed.
   */
  
  fun wantToProcessBook (filePath: String): Boolean
  {
    val bookNo = BibleBookAndFileMapperRawUsx.getBookNumberFromFile(filePath)
    return if (-1 == bookNo) false else wantToProcessBook(bookNo)
  }

  
  /****************************************************************************/
  /**
   * Used to limit the books (files) which are processed by those activities
   * which loop over a collection of books.
   * 
   * @param bookNo Number of book being considered for processing.
   * @return True if book should be processed.
   */

  fun wantToProcessBook (bookNo: Int): Boolean
  {
    return m_BooksToBeProcessed[bookNo].m_Process
  }
  
  
  /****************************************************************************/
  /**
   * Used to limit the books (files) which are processed by those activities
   * which loop over a collection of books.
   * 
   * @param bookName USX abbreviation for book being considered for processing.
   * @return True if book should be processed.
   */

  fun wantToProcessBookByAbbreviatedName (bookName: String): Boolean
  {
    val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(bookName)
    return m_BooksToBeProcessed[bookNo].m_Process
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                               Set flags                                  */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  const val C_DebugFlag_none                 = 0L
  const val C_DebugFlag_basic                = 0x0000_0000_0000_0001L
  const val C_DebugFlag_addAttributesToNodes = 0x1000_0000_0000_0000L


  /****************************************************************************/
  /**
   * Returns the debug level.
   *
   * @return  The debug level.
   */

  fun getDebugFlag (): Long { return m_DebugLevel }


  /****************************************************************************/
  /**
   * Sets the debug level, taking the input as a string representing a number.
   *
   * @param s Debug level as a number.
   * @param onOff Indicates whether the bit should be set or not.
   */

  fun setDebugFlag (s :String, onOff: Boolean = true)
  {
    setDebugFlag(s.toLong(), onOff)
  }


  /****************************************************************************/
  /**
   * Sets or clears a debug flag setting.
   *
   * @param flag Single bit.
   * @param onOff Indicates whether the bit should be set or not.
   */

  fun setDebugFlag (flag: Long, onOff: Boolean = true)
  {
    m_DebugLevel =
      if (onOff)
        m_DebugLevel or flag
      else
        m_DebugLevel and flag.inv()
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                            Dom modification                              */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Adds a debug attribute to a node if the appropriate debug flag is set.
   *
   * @param node Node to receive attribute.
   * @param name Name of attribute ('_DBG_' is prepended to this).
   * @param value Value to be assigned to attribute.
   */

  fun recordDbgAttribute(node: Node, name: String, value: String): Node
  {
    if (0L != (m_DebugLevel and C_DebugFlag_addAttributesToNodes))
      Dom.setAttribute(node, "_DBG_$name", value)
    return node
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                   Debug control -- limited debugging                     */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* There are occasions when it is useful to flag in one location that debug
     information should be output in some other location.  The facilities here
     let you associate a named value with either true or false, and then pick
     that value up elsewhere. */
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Returns an indication of whether debug information is required.
   * 
   * @param name Name of flag.
   * @return True if debugging is required.
   */
  
  fun wantDebugInfo (name: String): Boolean
  {
    return G_Debug.containsKey(name) && G_Debug[name] == true
  }
  
  
  /****************************************************************************/
  /**
   * Set a flag from one part of the processing which can be used to control
   * debugging elsewhere.
   * 
   * @param name Name of flag.
   * @param value True if debugging is required.
   */
  
  fun setWantDebugInfo (name: String, value: Boolean)
  {
    G_Debug[name] = value
  }
  
  
  /****************************************************************************/
  private fun getBookNo (containingBookAbbrev: String): Int
  {
    val uc = containingBookAbbrev.uppercase()
    m_BooksToBeProcessed.forEachIndexed { ix, bookDetails -> if (uc.contains(bookDetails.m_Abbrev)) return ix }
    return -1
  }
  
  
  /****************************************************************************/
  private val G_Debug: MutableMap<String, Boolean> = hashMapOf()
  
  private class BookSelector (abbrev: String)
  {
    var m_Abbrev: String
    var m_Process = true
    init { m_Abbrev = abbrev }
  }
  
  /* Index into array corresponds to UBS book number. */
  
  private val m_BooksToBeProcessed = arrayOf(
    BookSelector("XXX"),
    BookSelector("GEN"),
    BookSelector("EXO"),
    BookSelector("LEV"),
    BookSelector("NUM"),
    BookSelector("DEU"),
    BookSelector("JOS"),
    BookSelector("JDG"),
    BookSelector("RUT"),
    BookSelector("1SA"),
    BookSelector("2SA"),
    BookSelector("1KI"),
    BookSelector("2KI"),
    BookSelector("1CH"),
    BookSelector("2CH"),
    BookSelector("EZR"),
    BookSelector("NEH"),
    BookSelector("EST"),
    BookSelector("JOB"),
    BookSelector("PSA"),
    BookSelector("PRO"),
    BookSelector("ECC"),
    BookSelector("SNG"),
    BookSelector("ISA"),
    BookSelector("JER"),
    BookSelector("LAM"),
    BookSelector("EZK"),
    BookSelector("DAN"),
    BookSelector("HOS"),
    BookSelector("JOL"),
    BookSelector("AMO"),
    BookSelector("OBA"),
    BookSelector("JON"),
    BookSelector("MIC"),
    BookSelector("NAM"),
    BookSelector("HAB"),
    BookSelector("ZEP"),
    BookSelector("HAG"),
    BookSelector("ZEC"),
    BookSelector("MAL"),
    BookSelector("XXX"),
    BookSelector("MAT"),
    BookSelector("MRK"),
    BookSelector("LUK"),
    BookSelector("JHN"),
    BookSelector("ACT"),
    BookSelector("ROM"),
    BookSelector("1CO"),
    BookSelector("2CO"),
    BookSelector("GAL"),
    BookSelector("EPH"),
    BookSelector("PHP"),
    BookSelector("COL"),
    BookSelector("1TH"),
    BookSelector("2TH"),
    BookSelector("1TI"),
    BookSelector("2TI"),
    BookSelector("TIT"),
    BookSelector("PHM"),
    BookSelector("HEB"),
    BookSelector("JAS"),
    BookSelector("1PE"),
    BookSelector("2PE"),
    BookSelector("1JN"),
    BookSelector("2JN"),
    BookSelector("3JN"),
    BookSelector("JUD"),
    BookSelector("REV"),
    BookSelector("TOB"),
    BookSelector("JDT"),
    BookSelector("ESG"),
    BookSelector("WIS"),
    BookSelector("SIR"),
    BookSelector("BAR"),
    BookSelector("LJE"),
    BookSelector("S3Y"),
    BookSelector("SUS"),
    BookSelector("BEL"),
    BookSelector("1MA"),
    BookSelector("2MA"),
    BookSelector("3MA"),
    BookSelector("4MA"),
    BookSelector("1ES"),
    BookSelector("2ES"),
    BookSelector("MAN"),
    BookSelector("PS2"),
    BookSelector("ODA"),
    BookSelector("PSS"),
    BookSelector("JSA"),
    BookSelector("JDB"),
    BookSelector("TBS"),
    BookSelector("SST"),
    BookSelector("DNT"),
    BookSelector("BLT"),
    BookSelector("LAO"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("XXX"),
    BookSelector("EZA"),
    BookSelector("5EZ"),
    BookSelector("6EZ")
  )

  
  
  
  
  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                            Conditional debug                             */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* The functions in this group can be used to pause processing based upon an
     examination of their outputs -- you simply need to apply a breakpoint to
     the println statement.  All of them return true if they hit the println
     statement. */

  /****************************************************************************/
  fun d (b: Boolean): Boolean
  {
    if (b)
      System.err.println("DEBUG")
    return b
  }

  
  /****************************************************************************/
  /* String equality, case-insensitive. */

  fun d (s1: String, s2: String): Boolean
  {
    val b = s1.equals(s2, ignoreCase = true)

    if (b)
      System.err.println("DEBUG")

    return b
  }

  
  /****************************************************************************/
  /* Parent string contains child string, case-insensitive. */

  fun dCont (parent:String, child: String): Boolean
  {
    val b = parent.lowercase().contains(child.lowercase())
    if (b)
      System.err.println("DEBUG")

    return b
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                        Unconditional debug output                        */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* The functions in this section all send output to System.err, and return
     the input value passed to them.  They are useful wherever you want to
     output debugging information on an ad hoc basis.*/
  /****************************************************************************/

  /****************************************************************************/
  @Synchronized fun <T> d (x: T): T
  {
    if (x is Iterable<*>)
      x.forEachIndexed { ix, value -> d(String.format("%3d", ix) + ": " + value.toString()) }
    else
      System.err.println("$x")

    return x
  }


  /****************************************************************************/
  @Synchronized fun d (n: Int): Int
  {
    System.err.println(n)
    return n
  }


  /****************************************************************************/
  @Synchronized fun d (n: Long): Long
  {
    System.err.println(n)
    return n
  }


  /****************************************************************************/
  @Synchronized fun d (s: String?): String?
  {
    System.err.println(s ?: "NULL")
    return s
  }


  /****************************************************************************/
  @Synchronized fun <T> d (things : Iterable<T>): Iterable<T>
  {
    things.forEachIndexed { ix, value -> d(String.format("%3d", ix) + ": " + value.toString()) }
    return things
  }

  
  /****************************************************************************/
  @Synchronized fun d (document : Document, fileName: String = "a"): Document
  {
    outputDom(document, fileName)
    return document
  }


  /****************************************************************************/
  @Synchronized fun d (n : Node): Node
  {
    System.err.println(Dom.toString(n))
    return n
  }


  /****************************************************************************/
  @Synchronized fun d (prefix: String, n: Node): Node
  {
    System.err.println(prefix + ": " + Dom.toString(n))
    return n
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                   Debug output based upon debug level                    */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Functions in this section output data to System.err based upon the debug
     level setting -- either outputting data if the level is non-zero, or
     doing so if the level ANDed with a bitmap passed to the method gives a
     non-zero value.  All of them return their argument. */

  /****************************************************************************/
  /**
   * Outputs information if the debug level is non-zero.
   *
   * @param s The string to be printed.
   * @return Copy of input.
   */
  
  fun println (s: String): String
  {
    if (m_DebugLevel > 0) System.err.println(s)
    return s
  }
  
  
  /****************************************************************************/
  /**
   * Outputs information based upon bit settings in the debug level.
   *
   * @param s The string to be printed.
   * @param flag A set of debug flags which are ANDed with the debug level to
   *   determine whether to generate output or not.
   * @return Copy of input.
   */
  
  fun println (s: String, flag: Long): String
  {
    if (0L != (m_DebugLevel and flag))
      System.err.println(s)
    return s
  }
  
  
  /****************************************************************************/
  /**
   * Outputs information no matter what.
   *
   * @param s The string to be printed.
   * @return Copy of input
   */
  
  fun printlnForce (s: String): String
  {
    System.err.println(s)
    return s
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Miscellaneous                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Outputs a string to System.err and exits immediately.
   *
   * @param s String to be written out.
   */

  @Synchronized fun panic (s: String)
  {
    System.err.println(s)
    exitProcess(99)
  }


  /****************************************************************************/
  /**
   * Outputs a string to stdout.
   *
   * @param s String to be written out.
   * @param level Enables you to indent stuff consistently.  Each increment in
   *   level gives a little bit more indentation.
   */

  @Synchronized fun reportProgress (s: String, level: Int = 0)
  {
    print("                    ".substring(0, 2 * level))
    kotlin.io.println(s)
  }


  /****************************************************************************/
  /**
   * Outputs contents of DOM.  Note that this uses a hard-coded output
   * location, and will therefore need changing for use on other systems.
   * 
   * @param doc Document.
   * @param fileName Just the file name (not extension) for output.  Data is
   *                 written to the desktop.
   */

  fun outputDom (doc: Document, fileName: String = "a")
  {
    Dom.outputDomAsXml(doc, "C:/Users/Jamie/Desktop/$fileName.usx", null)
  }
  
  
  
  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private var m_DebugLevel = 0L
  private var m_DebugRunningOnPartialCollectionOfBooksOnly = false
}
