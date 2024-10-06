package org.stepbible.textconverter.nonapplicationspecificutils.debug

import org.stepbible.textconverter.applicationspecificutils.Utils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.util.*


/******************************************************************************/
/**
 * Controls progress reporting.
 *
 *
 *
 *
 * ## Main functionality
 *
 * The functions are as follows (all write to stdout):
 *
 * - [report]: Does no more than output a string, potentially with a prefix.
 *   The *level* parameter determines what that prefix is: some newlines at
 *   the top level (level 0) and some indentation in other cases.
 *
 * - [reportAsContinuation] Outputs a given piece of text as a continuation of
 *   the current output line.
 *
 * - [reportBookAsContinuation]: As reportAsContinuation, except that it
 *   specifically takes a book abbreviation (OSIS or USX) as input, and
 *   converts it to USX form before output.
 *
 * - [reportEol]: Probably mainly of interest for internal use here.  Outputs
 *   a number of newlines.
 *
 * - [reportWithContinuation]: Outputs the given string (with a prefix as
 *   described above for *report*), but does not output a newline at the end
 *   of the text.  It takes a function as its last parameter, and runs that
 *   code under its own control.  This makes it possible to retain information
 *   about the processing active at the time when not carrying out parallel
 *   processing.
 *
 *
 * I anticipate *report* being used mainly to flag the fact that we have reached
 * a certain point in the processing, but to have no dependent processing of its
 * own.
 *
 * I then imagine reportWithContinuation flagging the start of some piece of
 * processing, perhaps with progress being further reported by repeated calls
 * to reportAsContinuation or reportBookAsContinuation.
 *
 *
 *
 *
 *
 * ## Retention of active processing
 *
 * When *not* parallel processing, I retain details of all active prompts
 * passed to reportWithContinuationInternal, along with the most recent
 * text passed to reportAsContinuation or reportBookAsContinuation.  This
 * information is available for debugging purposes.
 *
 * This information is not available when using parallel processing.  This is
 * because the only obvious way of recording it is using a stack, and in a
 * parallel environment thread A may push something on to the stack, then
 * process B may do so, but A may terminate first and pop the stack, which
 * would remove B's information.
 *
 * @author ARA "Jamie" Jamieson
 */

object Rpt: ObjectInterface
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
  * Returns details of active processing when not running in parallel.
  *
  * @return Details of active processing.
  */

  fun getStack (): String
  {
    return if (m_ParallelRunning)
      ""
    else
      m_ReportStack.reversed().joinToString(" / ") + (if (null == m_ActiveContinuation) "" else " / $m_ActiveContinuation")
  }


  /****************************************************************************/
  fun report (level: Int, text: String) = reportInternal(level, text)
  fun reportAsContinuation (text: String) = reportAsContinuationInternal(text)
  fun reportBookAsContinuation (text: String) = reportBookAsContinuationInternal(text)
  fun reportEol (n: Int = 1) = reportEolInternal(n)
  fun reportWithContinuation (level: Int, text: String, fn: () -> Unit) = reportWithContinuationInternal(level, text, fn)

  @Synchronized fun error (text: String)   { System.err.print("\n*** ERROR: $text"           ) }
  @Synchronized fun info  (text: String)   { System.err.print("\n--- Information: +++ $text" ) }
  @Synchronized fun warning (text: String) { System.err.print("\n+++ Warning: +++ $text"     ) }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  @Synchronized private fun reportAsContinuationInternal (text: String)
  {
    if (!m_ParallelRunning) m_ActiveContinuation = text
    print(" $text")
  }


  /****************************************************************************/
  private fun reportBookAsContinuationInternal (bookName: String)
  {
    val revisedBookName = Utils.prettifyBookAbbreviation(bookName) ?: bookName
    reportAsContinuationInternal(revisedBookName)
  }


  /****************************************************************************/
  @Synchronized private fun reportEolInternal (n: Int) { print("\n\n\n\n\n\n\n\n\n".substring(0, n)) }


  /****************************************************************************/
  @Synchronized private fun reportInternal (level: Int, text: String)
  {
    Dbg.d(text.isBlank())
    val prefix = when (level)
      {
        -1    -> "\n"
        0    ->  "\n\n"
        else -> "\n" + "                    ".substring(0, 2 * (level - 1)) + "- "
      }

    print(prefix + text)
  }


  /****************************************************************************/
  private fun reportWithContinuationInternal (level: Int, text: String, fn: () -> Unit)
  {
    try
    {
      reportInternal(level, text)
      if (!m_ParallelRunning) { m_ActiveContinuation = null; m_ReportStack.push(text) }
      fn()
      //reportEol(1)
    }

    catch (e: Exception)
    {
      throw StepExceptionWithStackTraceAbandonRun(e)
    }

    finally
    {
      if (!m_ParallelRunning) { m_ActiveContinuation = null; m_ReportStack.pop() }
    }
  }


  /****************************************************************************/
  private var m_ActiveContinuation: String? = null
  private var m_ParallelRunning = ParallelRunning.isPermitted()
  private var m_ReportStack = Stack<String>()
}