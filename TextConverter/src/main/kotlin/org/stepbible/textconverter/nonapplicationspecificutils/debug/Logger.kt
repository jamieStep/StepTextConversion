package org.stepbible.textconverter.nonapplicationspecificutils.debug

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionSilentAbandonRunBecauseErrorsRecordedInLog
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceBase
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.io.path.Path


/******************************************************************************/
/**
 * Logging.
 * 
 * @author ARA Jamieson
 */


/*******************************************************************************/
object Logger: ObjectInterface
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
   * Returns a count of the number of errors.
   * 
   * @return Number of errors.
   */
  
  fun getNumberOfErrors () = m_Errors!!.size
  
  
  /****************************************************************************/
  /**
   * Returns a count of the number of information messages.
   * 
   * @return Number of information messages.
   */
  
  fun getNumberOfInformations () = m_Info!!.size
  
  
  /****************************************************************************/
  /**
   * Returns a count of the number of warnings.
   * 
   * @return Number of warnings.
   */
  
  fun getNumberOfWarnings () = m_Warnings!!.size
  
  
  /****************************************************************************/
  /**
   * Sets the file path for logging.  If the parameter is null, output goes
   * to System.out.
   * 
   * @param logFilePath Full pathname for log file.
   */
  
  @Synchronized fun setLogFile (logFilePath: String)
  {
    m_Outputter = FileLogger
    FileLogger.setLogFilePath(logFilePath)
  }
  
  
  /****************************************************************************/
  /**
   * Announces any accumulated errors, warnings or info messages.
   * 
   * @param throwException If true, throws an exception if any errors have been recorded.
   */
  
  @Synchronized fun announceAll (throwException: Boolean)
  {
    val n = m_Errors!!.size
    announce(m_Errors!!,   "Error"      ); if (FileLogger === m_Outputter) m_Errors!!.clear() // Don't want to report things more than once.
    announce(m_Warnings!!, "Warning"    ); if (FileLogger === m_Outputter) m_Warnings!!.clear()
    announce(m_Info!!,     "Information"); if (FileLogger === m_Outputter) m_Info!!.clear()

    announceSpecial(); if (FileLogger === m_Outputter) m_SpecialMessages.clear()

    if (throwException && 0 != n)
      throw StepExceptionWithoutStackTraceBase("Abandoning processing -- errors detected.  See converterLog.txt.")
  }
  
  
  /****************************************************************************/
  /**
   * Announces any accumulated errors, warnings or info messages and then
   * terminates the run.  I assume we want to do this only if we have had
   * some errors.
   */
  
  @Synchronized fun announceAllAndTerminateImmediatelyIfErrors ()
  {
    if (0 == m_ErrorCount) return
    System.err.println("\nFatal errors.  Terminating immediately.  See converterLog.txt and previous program output.")
    announceAll(false)
    throw StepExceptionSilentAbandonRunBecauseErrorsRecordedInLog()
  }

  
  /****************************************************************************/
  /**
   * Writes a log file containing details of any errors etc, but leaves the
   * internal data structures intact.  The idea is that this enables us to
   * create a log file which can be included in the repository package, even
   * though it may be slightly incomplete because further issues may be logged
   * during or after the creation of the package.  Not complete, but better than
   * nothing.
   */

  @Synchronized fun announceAllForRepositoryPackage ()
  {
    StringLogger.close()
  }


  /****************************************************************************/
  /**
   * Records an error.
   * 
   * @param text Text to report.
   */

  @Synchronized fun error (text: String)
  {
    error(-1, text)
  }
  

  /****************************************************************************/
  /**
   * Records an error.
   * 
   * @param refKey RefKey of verse against which message is being reported, or
   *               0 for none.
   * @param text Text to report.
   */

  @Synchronized fun error (refKey: Long, text: String)
  {
    ++m_ErrorCount
    addMessage(m_Errors, refKey, text)
  }
  

  /****************************************************************************/
  /**
   * Records an information message.
   * 
   * @param text  Text to report.
   */

  @Synchronized fun info (text: String)
  {
    info(0, text)
  }
  

  /****************************************************************************/
  /**
   * Records an information message.
   * 
   * @param refKey RefKey of verse against which message is being reported, or
   *               0 for none.
   * @param text Text to report.
   */

  @Synchronized fun info (refKey: Long, text: String)
  {
    if (!text.contains("SUCCESS")) ++m_InfoCount
    addMessage(m_Info, refKey, text)
  }
  

  /****************************************************************************/
  /**
  * Records details of a 'special' message.  Special messages come out at the
  * end of the log output.
  *
  * @param text
  */

  @Synchronized fun specialMessage (text: String)
  {
    m_SpecialMessages.add(text.replace("\n\n", "\n"))
  }


  /****************************************************************************/
  /**
   * Summarises the present messages to the Dbg stream.
   */
  
  @Synchronized fun summariseResults ()
  {
    val logFilePath = if (ScreenLogger === m_Outputter) "" else "  For details, see ${FileLogger.getLogFilePath()}."
    val s = when {
        m_ErrorCount   > 0 -> "Errors have been reported.$logFilePath"
        m_WarningCount > 0 -> "Warnings have been reported.$logFilePath"
        m_InfoCount    > 0 -> "Information messages have been reported.$logFilePath"
        else               -> "No processing messages have been issued."
    }
    
    Rpt.report(level = 1, s)
  }
  
  
  /****************************************************************************/
  /**
   * Records a warning.
   * 
   * @param text Text of message.
   */

  @Synchronized fun warning (text: String)
  {
    warning(0, text)
  }

  
  /****************************************************************************/
  /**
   * Records a warning.
   * 
   * @param refKey RefKey of verse against which message is being reported, or
   *               0 for none.
   * @param text Text to report.
   */

  @Synchronized fun warning (refKey: Long, text: String)
  {
    //Dbg.dCont(text, "Verse mismatch")
    ++m_WarningCount
    addMessage(m_Warnings, refKey, text)
  }
  

  

  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun addMessage (messageContainer: MutableMap<RefKey, MutableList<String>>?, refKey: Long, text: String)
  {
    if (null == messageContainer) return
    var me: MutableList<String>? = messageContainer[refKey]
    if (null == me) { me = mutableListOf(); messageContainer[refKey] = me }
    me.add(text)
  }
  
  
  /****************************************************************************/
  private fun announce (messageContainer: Map<RefKey, MutableList<String>>, msgType: String)
  {
    /**************************************************************************/
    fun generateOutput (refKey: RefKey, texts: List<String>)
    {
      var refPart = ""; if (refKey > 0L) refPart = Ref.rd(refKey).toString() + ": "
      val prefix = "$msgType: $refPart"
      texts.forEach {
        output("$prefix$it")
      }
    }


    /**************************************************************************/
    /* Remove duplicates for each given refKey and generate sorted list. */

    val sortedMessages: SortedMap<RefKey, List<String>> = TreeMap()
    messageContainer.keys.forEach { sortedMessages[it] = messageContainer[it]!!.distinct() }
    sortedMessages.keys.forEach { generateOutput(it, sortedMessages[it]!!)}
  }
  
  
  /****************************************************************************/
  private fun announceSpecial ()
  {
    m_SpecialMessages.forEach { output(it) }
    output("\n")
  }


  /****************************************************************************/
  /* Sends output to the current outputter. */

  private fun output (text: String)
  {
    m_Outputter.output(text)
  }



  /********************************************************************************************************************/
  private var m_Errors  : MutableMap<RefKey, MutableList<String>>? = mutableMapOf()
  private var m_Info    : MutableMap<RefKey, MutableList<String>>? = mutableMapOf()
  private var m_Warnings: MutableMap<RefKey, MutableList<String>>? = mutableMapOf()
  private val m_SpecialMessages: MutableList<String> = mutableListOf()
  
  private var m_ErrorCount   = 0
  private var m_InfoCount    = 0
  private var m_WarningCount = 0

  private var m_Outputter: LoggerBase = ScreenLogger
}





/*******************************************************************************/
abstract class LoggerBase
{
  open fun close () {}
  abstract fun output (text: String)
  open fun setLogFilePath (logFilePath: String) {}
}





/*******************************************************************************/
/**
* This class has been forced upon me by the fact that File.appendText (which
* would have been far simpler) seems to add extra newlines in a way which I just
* couldn't fathom at all.
*/

object FileLogger: LoggerBase(), ObjectInterface
{
  /****************************************************************************/
  /**
  * Sorts the logged data and writes it out.
  */

  @Synchronized override fun close ()
  {
    sortLines()
    StepFileUtils.createFolderStructure(Path(m_LogFilePath!!).parent.toString())
    File(m_LogFilePath!!).writeText(m_Lines.joinToString(separator = "\n"){ it.replace("<nl>", "\n") })
  }


  /****************************************************************************/
  /**
   * Returns the file path.
   */

  fun getLogFilePath () = m_LogFilePath


  /****************************************************************************/
  /**
  * Outputs a given piece of text.
  *
  * @param text
  */

  @Synchronized override fun output (text: String)
  {
    m_Lines.add(text)
  }



  /****************************************************************************/
  /**
  * Sets the path for the log file.
  *
  * @param logFilePath
  */

  @Synchronized override fun setLogFilePath (logFilePath: String) { m_LogFilePath = logFilePath }


  /****************************************************************************/
  private fun sortLines ()
  {
    val errors: MutableList<String> = ArrayList()
    val informations: MutableList<String> = ArrayList()
    val warnings: MutableList<String> = ArrayList()
    val others: MutableList<String> = ArrayList()

    fun partition (line: String) {
      if (line.startsWith("Info"))
        informations.add(line)
      else if (line.startsWith("Warn"))
        warnings.add(line)
      else if (line.startsWith("Error"))
        errors.add(line)
      else if (line.trim().isNotEmpty())
        others.add("$line<nl>")
    }

    m_Lines.forEach(::partition)



    /**************************************************************************/
    m_Lines.clear()
    m_Lines.addAll(others) // Make sure the file prefix is retained at the top of the file.  (I'm not actually expecting 'others' to contain more than just this one line.
    m_Lines.addAll(errors)
    m_Lines.addAll(warnings)
    m_Lines.addAll(informations)
    m_Lines.add("")
  }


  /****************************************************************************/
  private var m_LogFilePath: String? = null
  var m_Lines = mutableListOf<String>() // Public so that StringLogger can get at it.
}




/*******************************************************************************/
/**
* Sends output to the screen.
*/

object ScreenLogger: LoggerBase(), ObjectInterface
{
  @Synchronized override fun output (text: String) { println(text) }
}





/*******************************************************************************/
/**
* Sends output to a string.
*/

object StringLogger: LoggerBase(), ObjectInterface
{
  @Synchronized override fun output (text: String) { }

  override fun close ()
  {
    val lines = FileLogger.m_Lines.toMutableList()
    val logFilePath = FileLocations.getConverterLogFilePath()
    StepFileUtils.createFolderStructure(Path(logFilePath).parent.toString())
    sortLines(lines)
    File(logFilePath).writeText(lines.joinToString(separator = "\n"){ it.replace("<nl>", "\n") })
  }

  /****************************************************************************/
  private fun sortLines (lines: MutableList<String>)
  {
    val errors: MutableList<String> = ArrayList()
    val informations: MutableList<String> = ArrayList()
    val warnings: MutableList<String> = ArrayList()
    val others: MutableList<String> = ArrayList()

    fun partition (line: String) {
      if (line.startsWith("Info"))
        informations.add(line)
      else if (line.startsWith("Warn"))
        warnings.add(line)
      else if (line.startsWith("Error"))
        errors.add(line)
      else if (line.trim().isNotEmpty())
        others.add("$line<nl>")
    }

    lines.forEach(::partition)



    /**************************************************************************/
    lines.clear()
    lines.addAll(others) // Make sure the file prefix is retained at the top of the file.  (I'm not actually expecting 'others' to contain more than just this one line.
    lines.addAll(errors)
    lines.addAll(warnings)
    lines.addAll(informations)
    lines.add("")
  }
}
