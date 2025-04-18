/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.debug

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNames
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.protocolagnosticutils.reversification.ReversificationDataRow
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis


/******************************************************************************/
/**
 * Controls debug and reporting.
 *
 *
 *
 *
 * ## Limiting the books processed
 *
 * If you know there are bugs in the processing for specific books, it may be
 * convenient to limit a run to processing only those books in order to save
 * time.  [setBooksToBeProcessed] can be used to specify the limits.  It is
 * fairly flexible, allowing you to set individual books, ranges, lists of
 * books etc.
 *
 *
 *
 *
 * # Conditional breakpointing
 *
 * There are various methods (mostly with the name 'd', short for Debug) which
 * test particular conditions.  They contain enough code within them that you
 * have something to which you can apply a breakpoint, so that effectively all
 * of these can function as conditional breakpoints.
 *
 * Possibly of interest for the more awkward situations are [setWantDebugInfo]
 * and [wantDebugInfo].  The former lets you associate a true / false value
 * with a name of your choice.  The latter lets you look these flags up by name
 * and obtain their value.
 *
 *
 *
 *
 *
 * ## Unconditional output
 *
 * Various methods (all just with the name 'd', short for Debug) are available
 * to output debug information.
 *
 * You can also use [outputDom] (again alternatively known as 'd') or
 * [outputText] to output the content of a DOM, or of a text string
 * respectively.
 *
 * @author ARA "Jamie" Jamieson
*/

object Dbg: ObjectInterface
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
  * Reports the amount of time in ms taken to execute a given piece of code.
  *
  * @param label Label to explain what the figure refers to.
  * @param fn Code being timed.
  */

  inline fun measureTime (label: String, fn: () -> Unit)
  {
    val time = measureTimeMillis(fn)
    d("\n+++ $label: $time\n")
  }




  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                   Debug control -- limited debugging                     */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun getBookNo (containingBookAbbrev: String): Int
  {
    val uc = containingBookAbbrev.uppercase()
    val bookNames = BibleBookNames.dbgGetBookAbbreviations().map { it.uppercase() }
    for (i in bookNames.indices)
      if (uc == bookNames[i])
      return i

    return -1
  }


  /****************************************************************************/
  private val G_Debug: MutableMap<String, Boolean> = hashMapOf()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Conditional debug                            **/
  /**                                                                        **/
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
  /**                                                                        **/
  /**                       Unconditional debug output                       **/
  /**                                                                        **/
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
  @Synchronized fun d (dataCollection: X_DataCollection, fileName: String = "a.xml"): X_DataCollection
  {
    outputDom(dataCollection.convertToDoc(), fileName)
    return dataCollection
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
  @Synchronized fun dDomTree (n: Node, fileName: String = "a.xml"): Node
  {
    // d(n); Dom.getChildren(n).forEach { d(it) }
    val doc = Dom.createDocument()
    val nn = doc.importNode(n, true)
    doc.appendChild(nn)
    outputDom(doc, fileName)
    return n
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                Debug control -- select books to process                  */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Used to make all books as being processed.  This is the default anyway, but
  * on a run where, for instance, you are just evaluating schemes, you need to
  * process all books, but may inadvertently have some config or whatever
  * hanging around which is limiting the books, and this will clear it.
  */

  fun resetBooksToBeProcessed ()
  {
    BibleBookNames.DbgWantToProcessBook.indices.forEach { BibleBookNames.DbgWantToProcessBook[it] = true }
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
    val setting = "!=" == test
    BibleBookNames.DbgWantToProcessBook.indices.forEach { BibleBookNames.dbgSetProcessBook(it, setting) }



    /**************************************************************************/
    when (test)
    {
      "<" -> {
        val bookNo = getBookNo(usxAbbrevs)
        for (i in 0..< bookNo) BibleBookNames.dbgSetProcessBook(i, true)
      }


      "<=" -> {
        val bookNo = getBookNo(usxAbbrevs)
        for (i in 0.. bookNo) BibleBookNames.dbgSetProcessBook(i, true)
      }

      
      "=" ->
      {
        val abbreviatedNames = BibleBookNames.dbgGetBookAbbreviations().map { it.uppercase() }
        abbreviatedNames.indices.filter { booksRequested.contains(abbreviatedNames[it]) }.forEach { BibleBookNames.dbgSetProcessBook(it, true) }
      }

      
      "!=" ->
      {
        val abbreviatedNames = BibleBookNames.dbgGetBookAbbreviations().map { it.uppercase() }
        abbreviatedNames.indices.filter { booksRequested.contains(abbreviatedNames[it]) }.forEach { BibleBookNames.dbgSetProcessBook(it, false) }
      }


      ">" ->
      {
        val bookNo = getBookNo(usxAbbrevs)
        for (i in bookNo + 1 ..< BibleBookNames.getNumberOfBooksSupported()) BibleBookNames.dbgSetProcessBook(i, true)
      }
      
      
      ">=" ->
      {
        val bookNo = getBookNo(usxAbbrevs)
        for (i in bookNo ..< BibleBookNames.getNumberOfBooksSupported()) BibleBookNames.dbgSetProcessBook(i, true)
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

  fun wantToProcessBook (bookNo: Int) = BibleBookNames.dbgWantToProcessBook(bookNo)

  
  /****************************************************************************/
  /**
   * Used to limit the books (files) which are processed by those activities
   * which loop over a collection of books.
   * 
   * @param abbreviatedName USX abbreviation for book being considered for
   *   processing.
   * @return True if book should be processed.
   */

  fun wantToProcessBook (abbreviatedName: String): Boolean = BibleBookNames.dbgWantToProcessBook(abbreviatedName)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Miscellaneous                              **/
  /**                                                                        **/
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
  /* There are occasions when it is useful to flag in one location that debug
     information should be output in some other location.  The facilities here
     let you associate a named value with either true or false, and then pick
     that value up elsewhere. */
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Returns an indication of whether debug information is required, based upon
   * flag settings set by [wantDebugInfo].
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
   * @param text Text to be written out.
   * @param fileName Just the file name (not extension) for output.  Data is
   *                 written to the desktop.
   */

  fun outputText (text: String, fileName: String = "a.txt")
  {
    File(Paths.get(FileLocations.getTemporaryInvestigationsFolderPath(), fileName).toString()).bufferedWriter().use { it.write(text) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /*                                                                          */
  /*                                Private                                   */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doPrint (s: String)
  {
    System.err.println(s)
  }


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
  * end of the run, and then returns a suitable list of outputters. */

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
  private var m_DebugRunningOnPartialCollectionOfBooksOnly = false
  private val m_FileOutput:   MutableList<Pair<String, String>> = mutableListOf() // Deferred output.
  private var m_ReportProgressPrefix = ""
  private val m_ScreenOutput: MutableList<Pair<String, String>> = mutableListOf() // Deferred output.
}
