package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

import kotlinx.coroutines.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData

/******************************************************************************/
/**
 * Parallelisation.
 *
 * Parallel running is perhaps a bit of a luxury: processing an individual text
 * *can* be slow if the text is very large (as would be the case, for instance,
 * if it contained huge numbers of Strong's references or footnotes -- except
 * that I've taken other steps to avoid issues with footnotes).  However, in
 * general it is not unacceptably slow; and parallel running does bring with it
 * considerations which I have not baked into the code during development.
 *
 * This really means that there are certain caveats to take into account before
 * deciding to try running portions of the code in parallel:
 *
 * - You need to make sure either that the individual portions work with
 *   different instances of data so that they cannot tread upon each other's
 *   toes, or, if you *have* to use shared data structures, that those
 *   structures are thread safe.
 *
 * - Do bear in mind that sequential code may guarantee the ordering of the
 *   content of data structures, but that parallel code may not do so.
 *
 * - You have to accept that things may not occur in the 'natural' order.  For
 *   example, we are used to processing books in order, and this is reflected
 *   in the progress reporting.  If running in parallel, things may *not*
 *   happen in the normal order.  If you are not progress-reporting (and if
 *   everything works successfully) this won't matter, because it will not be
 *   visible.  However, if you *are* reporting progress, the report may come
 *   out in the wrong order; and if there are errors it may possibly be more
 *   difficult to locate them (you may need to turn off parallel running until
 *   you have investigated).
 *
 * - What happens if you need to abort processing, I am not entirely clear.  At
 *   the very least, I recommend using parallel processing only where you are
 *   working with in-memory data, rather than files.
 *
 *
 *
 *
 * ## Use
 *
 * My aim has been to introduce facilities which can be introduced easily into
 * the code without obscuring the underlying functionality.  At the same time,
 * I wanted something which could be turned off extremely easily without
 * making complicated changes to the existing code.
 *
 * Suppose you wanted to apply parallel processing to the following block of
 * code:
 *
 *    ```
 *      dataCollection.getRootNodes().forEach { bookNode ->
 *        bookNode.findNodesByName(dataCollection.getFileProtocol().tagName_chapter()).forEach(::addDummyVerseToChapter)
 *    ```
 *
 * Here, the inner block (bookNode.find ...) is an obvious candidate for
 * parallel running, because each book can be handled independently of the
 * others.
 *
 * To run this in parallel, you recode as follows:
 *
 *    ```
 *      with(ParallelRunning(true)) {
 *        run {
 *          dataCollection.getRootNodes().forEach { bookNode ->
 *            asyncable { bookNode.findNodesByName(dataCollection.getFileProtocol().tagName_chapter()).forEach(::addDummyVerseToChapter) }
 *          }
 *        }
 *      }
 *    ```
 *
 *
 * Here, the original code is fairly readily visible: the only change to the
 * code itself is that I have enclosed the inner statement in asyncable {...},
 * reflecting the fact that multiple instance of this code could be run in
 * parallel.
 *
 * The whole block is enclosed in
 *
 *    ```
 *      with(ParallelRunning(true)) {
 *        run {
*            ...
 *        }
 *      }
 *    ```
 *
 * If the argument to ParallelRunning is true, the code will be run in parallel.
 * If it is false, it will be run sequentially in the normal manner.
 *
 * You can also prevent parallel running altogether by setting
 * stepPermitParallelRunning to "no".  (You can set it anywhere --
 * environment variable, config file or command line, where you set it as
 * permitParallelRunning.
 *
 * Note, incidentally, that the 'with' above has lexical scope -- you can use
 * 'run' and 'asyncable' only physically within the braces which follow
 * *with(ParallelRunning(...))*.  If you want to hive the code off to a
 * separate function, that function needs to take a parameter of type
 * ParallelRunning and then use it with run etc.  And when calling it from
 * the scope of the calling ParallelRunning block, you pass it as 'this':
 *
 *    ```
 *      fun x (p: ParallelRunning, yourName: String)
 *      {
 *        p.run { println("Hi $yourName.") }
 *      }
 *
 *
 *      with(ParallelRunning(true)) {
 *        x(this, "John"
 *      }
 *    ```
 *
 *
 * There is one further problem for which I have not found an entirely
 * acceptable solution.  If one of the threads throws an exception, I
 * cannot actually force all threads to terminate immediately: I can
 * *request* that they do so, but they complete in their own sweet time.
 * As a consequence it is perfectly possible that further error messages
 * may be output.  I *think* I manage to output information relevant to
 * the first, but I have no way of being sure.
 *
 * @author ARA "Jamie" Jamieson
 */

class ParallelRunning (private val runInParallel: Boolean)
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
  * fn should be a code block comprising a loop, with the elements of the loop
  * being suitable for parallel running.  Runs the loop with parallel running
  * if the class was instantiated with runInParallel = true, otherwise runs
  * sequentially in the normal manner.
  *
  * @param fn Code to run.
  * @return Any return value from fn.
  */

  fun<T> run (fn: () -> T): T
  {
    return if (runInParallel && m_PermitParallelRunning)
      runBlocking {
        m_CoroutineScope = this
        val res = fn()
        m_Waits.forEach { it.await() }
        res
      }

    else
      fn()
  }


  /****************************************************************************/
  /**
  * A block of code, run under control of the [run] function, which is suitable
  * for parallel running.  The fact that it is *suitable* does not necessarily
  * mean that it *will* be run in parallel.  Whether it is or not depends upon
  * whether the class was instantiated with runInParallel = true.
  *
  * @param fn The code to be run.
  */

  fun asyncable (fn: () -> Unit)
  {
    if (runInParallel && m_PermitParallelRunning)
    {
      m_Waits.add(m_CoroutineScope.async(Dispatchers.Default) {
        try {
          fn()
        }
        catch (e: Exception)
        {
          m_Waits.forEach { try { it.cancel() } catch (_: Exception) { } } // Try to kill off all processes.
          throw (e)
        }
      })
    }
    else
    {
      fn()
    }
  }





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
     * Returns an indication of whether parallel running is permitted.
     *
     * @return True if parallel running is permitted.
     */

    fun isPermitted () = m_PermitParallelRunning
    private val m_PermitParallelRunning = ConfigData.getAsBoolean("stepPermitParallelRunning", "yes")
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private lateinit var m_CoroutineScope: CoroutineScope
  private val m_Waits: MutableList<Deferred<Unit>> = mutableListOf() // Things we're waiting for.
}
