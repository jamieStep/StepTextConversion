package org.stepbible.textconverter.support.debug

//import org.stepbible.textconverter.TestController
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.exitProcess


/******************************************************************************/
/**
 * Logging.
 * 
 * @author ARA Jamieson
 */


/*******************************************************************************/
object Logger
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
  
  fun getNumberOfErrors (): Int
  {
    return m_Errors!!.size
  }
  
  
  /****************************************************************************/
  /**
   * Returns a count of the number of information messages.
   * 
   * @return Number of information messages.
   */
  
  fun getNumberOfInformations (): Int
  {
    return m_Info!!.size
  }
  
  
  /****************************************************************************/
  /**
   * Returns a count of the number of warnings.
   * 
   * @return Number of warnings.
   */
  
  fun getNumberOfWarnings () : Int
  {
    return m_Warnings!!.size
  }
  
  
  /****************************************************************************/
  /**
   * Sets the file path for logging.  If the parameter is null, output goes
   * to System.out.
   * 
   * @param logFilePath Full pathname for log file.
   */
  
  fun setLogFile (logFilePath: String)
  {
    m_LogFilePath = logFilePath
  }
  
  
  /****************************************************************************/
  /**
   * Announces any accumulated errors, warnings or info messages.
   * 
   * @param throwException If true, throws an exception if any errors have been recorded.
   * @throws StepException Typically reflects IO issues.
   */
  
  fun announceAll (throwException: Boolean)
  {
    val n = m_Errors!!.size
    announce(m_Errors!!,   "Error"      ); if (m_LogFilePath.isNotEmpty()) m_Errors!!.clear()
    announce(m_Warnings!!, "Warning"    ); if (m_LogFilePath.isNotEmpty()) m_Warnings!!.clear()
    announce(m_Info!!,     "Information"); if (m_LogFilePath.isNotEmpty()) m_Info!!.clear()
    sortLogFile()

    announceSpecial(); if (m_LogFilePath.isNotEmpty()) m_SpecialMessages.clear()

    if (throwException && 0 != n)
      throw StepException("Abandoning processing -- errors detected.  See converterLog.txt.", false)
  }
  
  
  /****************************************************************************/
  /**
   * Announces any accumulated errors, warnings or info messages and then
   * terminates the run.  I assume we want to do this only if we have had
   * some errors.
   */
  
  fun announceAllAndTerminateImmediatelyIfErrors ()
  {
    if (0 == m_ErrorCount) return
    System.err.println("Fatal errors.  Terminating immediately.  See converterLog.txt and previous program output.")
    announceAll(false)
    exitProcess(0)
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
//    if (TestController.instance().suppressErrors())
//    {
//      warning(refKey,"!!!!!!!!!!!!!! Error converted to warning while testing: $text")
//      return
//    }


    ++m_ErrorCount
    addMessage(m_Errors, refKey, text)
  }
  

  /****************************************************************************/
  /**
  * Returns the current prefix.
  *
  * @return Current prefix.
  */

  fun getPrefix () = m_Prefix



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
  fun nullReporter (text: String) { }
  fun nullReporter (refKey: Long, text: String) { }



  /****************************************************************************/
  /**
   * Enables you to set a string which will be prefixed to all messages,
   * perhaps to give additional context.  You may well want to make sure the
   * string ends, say, with ": ".  This actually builds up a stack, so you can
   * change the prefix within something which itself set a prefix.  Entries are
   * popped if you pass a null string.
   * 
   * @param prefix Prefix.
   */

  @Synchronized fun setPrefix (prefix: String?)
  {
    if (null == prefix)
      m_Prefix.pop()
    else
      m_Prefix.push(prefix)
  }
  
  
  /****************************************************************************/
  /**
  * Records details of a 'special' message.  Special messages come out at the
  * end of the log output.
  *
  * @param text
  */

  fun specialMessage (text: String)
  {
    m_SpecialMessages.add(text)
  }


  /****************************************************************************/
  /**
   * Summarises the present messages to the Dbg stream.
   */
  
  fun summariseResults ()
  {
    val logFilePath = if (m_LogFilePath.isEmpty()) "" else "  For details, see $m_LogFilePath."
    val s = when {
        m_ErrorCount   > 0 -> "Errors have been reported.$logFilePath"
        m_WarningCount > 0 -> "Warnings have been reported.$logFilePath"
        m_InfoCount    > 0 -> "Information messages have been reported.$logFilePath"
        else               -> "No processing messages have been issued."
    }
    
    Dbg.reportProgress(s)
  }
  
  
  /****************************************************************************/
  /**
  * Suppresses error messages within the code passed as argument.
  *
  * @param body Code to run.
  */

  fun suppressErrors (body: Any.() -> Unit)
  {
    val save = m_Errors; m_Errors = null
    try
    {
      body()
    }
    catch (e: Exception)
    {
      throw StepException(e)
    }
    finally
    {
      m_Errors = save
    }
  }


  /****************************************************************************/
  /**
  * Suppresses error messages within the code passed as argument.
  *
  * @param body Code to run.
  */

  fun suppressErrorsAndWarnings (body: Any.() -> Unit)
  {
    val saveWarnings = m_Warnings; m_Warnings = null
    val saveErrors = m_Errors; m_Errors = null
    try
    {
      body()
    }
    catch (e: Exception)
    {
      throw StepException(e)
    }
    finally
    {
      m_Errors = saveErrors
      m_Warnings = saveWarnings
    }
  }


  /****************************************************************************/
  /**
  * Suppresses warning messages within the code passed as argument.
  *
  * @param body Code to run.
  */

  fun suppressWarnings (body: Any.() -> Unit)
  {
    val save = m_Warnings; m_Warnings = null
    try
    {
      body()
    }
    catch (e: Exception)
    {
      throw StepException(e)
    }
    finally
    {
      m_Warnings = save
    }
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
  private fun addMessage (messageContainer: SortedMap<RefKey, MutableList<String>>?, refKey: Long, text: String)
  {
    if (null == messageContainer) return
    var me: MutableList<String>? = messageContainer[refKey]
    if (null == me) { me = mutableListOf(); messageContainer[refKey] = me }
    var s = if (m_Prefix.isEmpty()) "" else m_Prefix.peek() + ": "
    s += text
    me.add(s)
  }
  
  
  /****************************************************************************/
  private fun announce (messageContainer: Map<RefKey, List<String>>, msgType: String)
  {
    /**************************************************************************/
    val outputter = getOutputter()



    /**************************************************************************/
    val content: MutableList<String> = mutableListOf()
    fun convertToDisplayableForm (refKey: Long)
    {
      var refPart = ""; if (refKey > 0L) refPart = Ref.rd(refKey).toString() + ": "
      val prefix = "$msgType: $refPart"
      (messageContainer[refKey]?.distinct()?.map { prefix + it })?.forEach { content.add(it) }
    }
    

    
    /**************************************************************************/
    /* If the file doesn't already exist, create it with a header which lets us
       determine which root folder we're working in.  Then, whether we do this
       or not, append the various messages after removing duplicates. */

    val fileAlreadyExists = (File(m_LogFilePath)).exists()
    if (!fileAlreadyExists) File(m_LogFilePath).appendText(File(m_LogFilePath).parent + "\n\n")
    messageContainer.forEach { convertToDisplayableForm(it.key) }
    val filteredContent = content.filter { it !in m_MessageDeduplicator }
    if (filteredContent.isNotEmpty())
    {
      filteredContent.forEach { m_MessageDeduplicator.add(it); outputter("$it\n") }
      outputter("\n")
    }
  }
  
  
  /****************************************************************************/
  private fun announceSpecial ()
  {
    val outputter = getOutputter()
    m_SpecialMessages.forEach { outputter("$it\n") }
    outputter("\n")
  }


  /****************************************************************************/
  /* Determine where to send output. */

  private fun getOutputter (): (String) -> Unit
  {
    val outputterToConsole: (String) -> Unit = { print(it) }
    val outputterToFile   : (String) -> Unit = { File(m_LogFilePath).appendText(it) }
    return if (m_LogFilePath.isEmpty()) outputterToConsole else outputterToFile
  }


  /****************************************************************************/
  /** Sorts the log file so that errors come before warnings come before
   *  information messages.
   */
  
  private fun sortLogFile ()
  {
    /******************************************************************************************************************/
    if (m_LogFilePath.isEmpty() || !(File(m_LogFilePath)).exists())
      return


    /******************************************************************************************************************/
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

    File(m_LogFilePath).useLines{ sequence -> sequence.forEach{ partition(it) } }



    /******************************************************************************************************************/
    val output: MutableList<String> = ArrayList()
    output.addAll(others) // Make sure the file prefix is retained at the top of the file.  (I'm not actually expecting 'others' to contain more than just this one line.
    output.addAll(errors)
    output.addAll(warnings)
    output.addAll(informations)
    output.add("")
    File(m_LogFilePath).writeText(output.joinToString(separator = "\n"){ it.replace("<nl>", "\n") })
  }
  
  
  /********************************************************************************************************************/
  private var m_Errors  : SortedMap<RefKey, MutableList<String>>? = TreeMap()
  private var m_Info    : SortedMap<RefKey, MutableList<String>>? = TreeMap()
  private var m_Warnings: SortedMap<RefKey, MutableList<String>>? = TreeMap()
  private val m_SpecialMessages: MutableList<String> = mutableListOf()
  
  private var m_ErrorCount   = 0
  private var m_InfoCount    = 0
  private var m_WarningCount = 0

  private val m_MessageDeduplicator = TreeSet(String.CASE_INSENSITIVE_ORDER)

  private var m_LogFilePath: String = ""
  private val m_Prefix: Stack<String>  = Stack()
}