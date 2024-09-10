/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.debug

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNames
import org.stepbible.textconverter.applicationspecificutils.ReversificationDataRow
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceBase
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


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
 *
 *
 *
 *
 * ## Progress reporting
 *
 * At the time of writing I'm not entirely sure I've got this nailed.  I
 * intended to use indentation to indicate when one piece of processing was
 * dependent upon another, and to have the indentation worked out automatically
 * from the arguments to the various methods.  I'm not presently sure this
 * really works.
 *
 * Anyway ...
 *
 * - You can use [reportProgress] simply to output a progress message, and
 *   one form of this method lets you indicate an indentation level.
 *   reportProgress simply outputs a message but retains no record of the
 *   fact that it has done so.
 *
 * - [withReportProgressMain] outputs a top level message, and then runs
 *   processing for which that message is relevant.  This does retain
 *   a record of the message (on a stack) which is therefore available for
 *   error reporting if the processing under its control fails.
 *
 * - [reportProgressSub] outputs a next level message, and then runs
 *   processing for which that message is relevant.  This also retains
 *   a record of the message (on a stack) which is therefore available for
 *   error reporting if the processing under its control fails.
 *
 * - [withProcessingBooks] / [withProcessingBook] handles low-level
 *   reporting where you want the new details simply to be appended to the
 *   current line on the output.  This is useful, for instance, where you
 *   are applying a given piece of processing to each of a number of books,
 *   and rather than have an output line for each book (which might give
 *   rise to an awful lot of lines) would rather have the name of the
 *   book appended to the current line.  withProcessingBook can be used
 *   only within the context of withProcessingBooks, the latter being
 *   responsible for setting things up so that withProcessingBook does the
 *   right thing, and then clearing things down again.  Both of these are
 *   wrappers around contained processing, and so details of the book being
 *   processed are available for error reporting if required.  You must make
 *   sure that no other progress reporting occurs while these are active,
 *   because other output will get muddled with the line currently being
 *   output.
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
  /*                   Debug control -- limited debugging                     */
  /*                                                                          */
  /****************************************************************************/
  /****************************************************************************/

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
  /**                         Progress reporting                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Returns details of the latest message stored by [withReportProgressMain] or
  * [withReportProgressSub].  Intended mainly for error reporting.
  *
  * @return Latest message.
  */

  fun getActiveProcessingId () = if (m_ActiveProcessingIds.isEmpty()) "" else m_ActiveProcessingIds.peek()


  /****************************************************************************/
  /**
  * Pops the current message off the stack and returns it.
  *
  * @return What, until now, was the message at the top of the stack.
  */

  fun popActiveProcessingIds () = m_ActiveProcessingIds.pop()


  /****************************************************************************/
  /**
   *  Pushes a new message on to the stack.
   *
   *  @param text Message.
   */

  fun pushActiveProcessingIds (text: String) = m_ActiveProcessingIds.push(text)


  /****************************************************************************/
  /**
   * Outputs the top level of the active id stack.
   */

  @Synchronized fun reportProgress ()
  {
    println(getActiveProcessingId())
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
  * Sets a prefix to be used on all reportProgress outputs.
  */

  fun setReportProgressPrefix (prefix: String)
  {
    m_ReportProgressPrefix = prefix
  }


  /****************************************************************************/
  /**
  * Outputs a top-level progress message, and then runs code to which that
  * message is relevant.  The message continues to be available while the
  * code is running, and can therefore be used in error processing.
  *
  * @param text Text describing the processing which is being applied.
  * @param fn Functionality to which this message applies.
  */

  fun withReportProgressMain (text: String, fn: () -> Unit)
  {
    try
    {
      reportProgressMain(text)
      fn()
    }
    finally
    {
      unreportProgressMain()
    }
  }


  /****************************************************************************/
  /**
  * Outputs a next-level progress message, and then runs code to which that
  * message is relevant.  The message continues to be available while the
  * code is running, and can therefore be used in error processing.
  *
  * @param text Text describing the processing which is being applied.
  * @param fn Functionality to which this message applies.
  */

  fun withReportProgressSub (text: String, fn: () -> Unit)
  {
    try
    {
      reportProgressSub(text)
      fn()
    }
    catch (e: Exception)
    {
      throw StepExceptionWithStackTraceAbandonRun(e)
    }
    finally
    {
      unreportProgressSub()
    }
  }


  /****************************************************************************/
  /**
  * Announces that the given 'main' object is active.
  *
  * @param text Text to be announced.
  */

  private fun reportProgressMain (text: String)
  {
    pushActiveProcessingIds(text)
    if (text.isNotEmpty() && !text.startsWith("@")) reportProgress("\n*** $text")
  }



  /****************************************************************************/
  /**
  * Announces that the given subprocessing is active.
  *
  * @param text String to be added to string at top of stack and used as output.
  */

  private fun reportProgressSub (text: String)
  {
    val s = "- $text"
    pushActiveProcessingIds(s)
    if (text.isNotEmpty() && !text.startsWith("@")) reportProgress(s)
  }

  private fun unreportProgressMain () = popActiveProcessingIds()
  private fun unreportProgressSub  () = popActiveProcessingIds()




  /****************************************************************************/
  private val m_ActiveProcessingIds: Stack<String> = Stack()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**     Progress reporting of multiple low-level associated outputs        **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /*
     The functionality in this section is intended for use where we want to
     give very low-level progress reports -- typically when applying the same
     processing to all books in turn, when we want the individual books to be
     listed all on the same line.

     Note that this cannot be used if you anticipate having additional progress
     output within the processing for each book, because that will become
     muddled up with the single-line output being generated here.

     Strictly referring to this as processing for books is misleading, because
     it could be used for other things (and at present I am also using it to
     report processing of reversification data lines).  However, its main use
     is likely to be for books.
  */
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Arranges for a block of code to be run in a context where the processing of
  * individual books can be reported on a single line.
  *
  * <span class='important'>Don't use this if you anticipate the processing of
  * the individual book outputting additional progress information of its own --
  * the aim here is to list all of the books on a single line, and if additional
  * output is being generated, that output will become muddled up with the
  * output generated here.</span>
  *
  * @param text Text which appears at the start of the output line to indicate
  *   what processing is being applied.
  * @param fn Processing to be run under control of the present method.
  */

  fun withProcessingBooks (text: String, fn: () -> Unit)
  {
    withReportProgressSub("@$text") {
      withProcessingBooksStart("- $text")
      fn()
      withProcessingBooksEnd()
    }
  }


  /****************************************************************************/
  /**
  * Announces that a given book is being processed (or typically that a given
  * book is being processed -- there's nothing to stop you using for other
  * things too.  The supplied text is appended to the end of the output line
  * on the screen, suitably spaced.
  *
  * @text Text to be output.
  * @fn Processing to be run under control of the present method.
  */

  fun withProcessingBook (text: String, fn: () -> Unit)
  {
    try {
      m_BookBeingProcessedStack.push(text)
      print(m_WithProcessingBookPrefix)
      print(" $text")
      m_WithProcessingBookPrefix = ""
      fn()
    }
    catch (e: Exception)
    {
      println(); println(); println()
      System.err.println("Error occurred while processing ${m_BookBeingProcessedStack.peek()}.")
      throw(e)
    }
    finally {
      m_BookBeingProcessedStack.pop()
    }
  }


  /****************************************************************************/
  val m_BookBeingProcessedStack: Stack<String> = Stack()
  var m_WithProcessingBookPrefix: String = ""


  /****************************************************************************/
  /* At the end of the run of books or whatever, we need to force the output
     on to a new line.*/

  private fun withProcessingBooksEnd () = println()


  /****************************************************************************/
  /* At the start of processing we merely record details of the prefix which
     is to be written at the start of the input line to record what processing
     is being applied. */

  private fun withProcessingBooksStart (prefix: String)
  {
    m_WithProcessingBookPrefix = prefix
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
    //File(Paths.get(FileLocations.getTemporaryInvestigationsFolderPath(), fileName).toString()).bufferedWriter().write(text)
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
  private var m_DebugRunningOnPartialCollectionOfBooksOnly = false
  private val m_FileOutput:   MutableList<Pair<String, String>> = mutableListOf() // Deferred output.
  private var m_ReportProgressPrefix = ""
  private val m_ScreenOutput: MutableList<Pair<String, String>> = mutableListOf() // Deferred output.
}
