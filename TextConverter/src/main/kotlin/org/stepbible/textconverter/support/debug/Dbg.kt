/******************************************************************************/
package org.stepbible.textconverter.support.debug

import org.stepbible.textconverter.support.bibledetails.BibleBookNames
import org.stepbible.textconverter.utils.ReversificationDataRow
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
     return m_BooksToBeProcessed.filter { "XXX" != it.m_Abbrev && it.m_Process } .map { it.m_Abbrev }
  }


  /****************************************************************************/
  /**
  * Used to make all books as being processed.  This is the default anyway, but
  * on a run where, for instance, you are just evaluating schemes, you need to
  * process all books, but may inadvertently have some config or whatever
  * hanging around which is limiting the books, and this will clear it.
  */

  fun resetBooksToBeProcessed ()
  {
    m_BooksToBeProcessed.forEach { it.m_Process = true}
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
   * @param usxAbbrevs Comma-separated list of abbreviations.
   * @param test One of <, <=, =, !=, >=, >.  If = or !=, abbrevs can contain a
   *             list; otherwise it should be only a single book and the test is
   *             based on a comparison with that book.
   */
  
  fun setBooksToBeProcessed (usxAbbrevs: String?, test: String = "=")
  {
    /**************************************************************************/
    if (usxAbbrevs.isNullOrEmpty())
      return
    
    
    
    /**************************************************************************/
    val booksRequested = usxAbbrevs.uppercase().split("\\W+".toRegex()).toSet()
    m_DebugRunningOnPartialCollectionOfBooksOnly = true

    if ("!=" == test)
      m_BooksToBeProcessed.forEach{ it.m_Process = true }
    else
      m_BooksToBeProcessed.forEach{ it.m_Process = false }



    /**************************************************************************/
    when (test)
    {
      "<" -> {
        val bookNo = getBookNo(usxAbbrevs)
        for (i in 0..< bookNo) m_BooksToBeProcessed[i].m_Process = true
      }


      "<=" -> {
        val bookNo = getBookNo(usxAbbrevs)
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
        val bookNo = getBookNo(usxAbbrevs)
        for (i in bookNo + 1..< m_BooksToBeProcessed.size) m_BooksToBeProcessed[i].m_Process = true
      }
      
      
      ">=" ->
      {
        val bookNo = getBookNo(usxAbbrevs)
        for (i in bookNo..< m_BooksToBeProcessed.size) m_BooksToBeProcessed[i].m_Process = true
      }
    }
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
    val bookNo = BibleBookNames[bookName]
    return m_BooksToBeProcessed[bookNo].m_Process
  }









  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                                Public                                    */
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

  fun addDebugAttributeToNode (node: Node, name: String, value: String): Node
  {
    if (m_AddDebugAttributesToNodes)
      Dom.setAttribute(node, "_DBG_$name", value)
    return node
  }


  /****************************************************************************/
  /**
  * What it says on the tin -- outputs details of reversification rows (which
  * presumably the caller will have limited to those actually selected).
  *
  * Does this conditionally, though, dependent upon the config settings.
  *
  * @param data Rows to be output.
  */

  fun displayReversificationRows (data: List<ReversificationDataRow>)
  {
    if (m_DbgDisplayReversificationRowsOutputter.isNotEmpty())
      data.forEach { runOutputters("rev", m_DbgDisplayReversificationRowsOutputter, it.toString()) }
  }


  /****************************************************************************/
  /**
  * Outputs any debug data accumulated during the run for which output was
  * deferred until the end of the run.
  */

  fun endOfRun ()
  {
    if (m_ScreenOutput.isNotEmpty())
    {
      d("\n\n\nDeferred debug data:\n\n")
      m_ScreenOutput.groupBy { group -> group.first } .forEach { elt -> elt.value.forEach { d(it.first + ": " + it.second) }; d(""); d("") }
    }

    if (m_FileOutput.isEmpty()) return

    File(FileLocations.getDebugOutputFilePath()).bufferedWriter().use { out ->
      out.write(ConfigData["stepModuleName"]!! + ": " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yy HH:mm")))
      out.newLine(); out.newLine()
      m_FileOutput.groupBy { group -> group.first } .forEach { elt -> elt.value.forEach { out.write(it.first + ": " + it.second); out.newLine() }; out.newLine(); out.newLine() }
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                                Private                                   */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun runOutputters (cat: String, outputters: List<(String, String) -> Unit>, s: String) { outputters.forEach { it.invoke(cat, s) } }
  private fun fileOutputter (cat: String, s: String)            { m_FileOutput.add(Pair(cat, s)) }
  private fun screenOutputterDeferred (cat: String, s: String)  { m_ScreenOutput.add(Pair(cat, s)) }
  private fun screenOutputterImmediate (cat: String, s: String) { d("$cat: $s") }


  /****************************************************************************/
  /**
  * Given a config parameter which can contain Screen, File and Deferred -- or
  * anything other than these -- works out whether we want to send output to
  * screen, file or both, and in the case of screen, whether we want the output
  * immediately at the time it is generated, or gathered up and output at the
  * end of the run, and then returns a suitably list of outputters. */

  private fun getOutputter (selector: String): List<(String, String) -> Unit>
  {
    val res: MutableList<(String, String) -> Unit> = mutableListOf()
    val sel = selector.lowercase().trim()

    if ("file" in sel)
      res.add(::fileOutputter)

    if ("screen" in sel)
      res.add(if ("deferred" in sel) ::screenOutputterDeferred else ::screenOutputterImmediate)

    return res
  }


  /****************************************************************************/
  private var m_AddDebugAttributesToNodes              = ConfigData.getAsBoolean("stepDbgAddDebugAttributesToNodes", "No")
  private var m_DbgDisplayReversificationRowsOutputter = getOutputter(ConfigData.get("stepDbgDisplayReversificationRows", "None"))
  private val m_FileOutput:   MutableList<Pair<String, String>> = mutableListOf() // Deferred output.
  private val m_ScreenOutput: MutableList<Pair<String, String>> = mutableListOf() // Deferred output.





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
    var m_Abbrev = abbrev
    var m_Process = true
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
    BookSelector("6EZ"),
    BookSelector("DAG")
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
  /**
  * Writes text to a file.
  *
  * @param text
  * @param filename Just the name portion.  The data automatically goes to the
  *   desktop.
  */

  fun dToFile (text: String, fileName: String = "a.xml")
  {
    File("$fileName").bufferedWriter().use { it.write(text) }
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
      doPrint("$x")

    return x
  }


  /****************************************************************************/
  @Synchronized fun d (n: Int): Int
  {
    doPrint(n.toString())
    return n
  }


  /****************************************************************************/
  @Synchronized fun d (n: Long): Long
  {
    doPrint(n.toString())
    return n
  }


  /****************************************************************************/
  @Synchronized fun d (s: String?): String?
  {
    doPrint(s ?: "NULL")
    return s
  }


  /****************************************************************************/
  @Synchronized fun <T> d (things : Iterable<T>): Iterable<T>
  {
    things.forEachIndexed { ix, value -> d(String.format("%4d", ix) + ": " + value.toString()) }
    return things
  }

  
  /****************************************************************************/
  @Synchronized fun d (document : Document, fileName: String = "a.xml"): Document
  {
    outputDom(document, fileName)
    return document
  }


  /****************************************************************************/
  @Synchronized fun d (n : Node): Node
  {
    doPrint(Dom.toString(n))
    return n
  }


  /****************************************************************************/
  @Synchronized fun d (prefix: String, n: Node): Node
  {
    doPrint(prefix + ": " + Dom.toString(n))
    return n
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
    print(if (m_ReportProgressPrefix.isEmpty() || s.isEmpty()) "" else "$m_ReportProgressPrefix: ")
    println(s)
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

  fun outputDom (doc: Document, fileName: String = "a.xml")
  {
    Dom.outputDomAsXml(doc, Paths.get(FileLocations.getTemporaryInvestigationsFolderPath(), fileName).toString(),null)
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

  fun outputText (text: String, fileName: String = "a.txt")
  {
    File(Paths.get(FileLocations.getTemporaryInvestigationsFolderPath(), fileName).toString()).bufferedWriter().write(text)
  }


  /****************************************************************************/
  /**
  * Sets a prefix to be used on all reportProgress outputs.
  */

  fun setReportProgressPrefix (prefix: String)
  {
    m_ReportProgressPrefix = prefix
  }
  
  
  
  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doPrint (s: String)
  {
    System.err.println(s)
  }


  /****************************************************************************/
  private var m_DebugRunningOnPartialCollectionOfBooksOnly = false
  private var m_ReportProgressPrefix = ""

}
