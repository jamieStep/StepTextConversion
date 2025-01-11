/******************************************************************************/
package org.stepbible.tyndaleStudyNotesEtc

import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.nio.file.Paths
import kotlin.system.measureTimeMillis


/******************************************************************************/
/**
 * Controls debug and reporting.
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
  /**                                                                        **/
  /**                             Miscellaneous                              **/
  /**                                                                        **/
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
    Dom.outputDomAsXml(doc, Paths.get("C:/Users/Jamie/Desktop", fileName).toString(),null)
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
    File(Paths.get("C:/Users/Jamie/Desktop", fileName).toString()).bufferedWriter().use { it.write(text) }
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
  private val m_FileOutput:   MutableList<Pair<String, String>> = mutableListOf() // Deferred output.
  private val m_ScreenOutput: MutableList<Pair<String, String>> = mutableListOf() // Deferred output.
}
